package it.polimi.ramses.knowledge;

import it.polimi.ramses.knowledge.domain.architecture.Instance;
import it.polimi.ramses.knowledge.domain.architecture.InstanceStatus;
import it.polimi.ramses.knowledge.domain.architecture.ServiceConfiguration;
import it.polimi.ramses.knowledge.domain.persistence.ConfigurationRepository;
import it.polimi.ramses.knowledge.domain.persistence.Vulnerability;
import it.polimi.ramses.knowledge.domain.persistence.VulnerabilityRepository;
import it.polimi.ramses.knowledge.externalinterfaces.ProbeClient;
import it.polimi.ramses.knowledge.externalinterfaces.ServiceInfo;
import it.polimi.ramses.knowledge.parser.QoSParser;
import it.polimi.ramses.knowledge.parser.SystemArchitectureParser;
import it.polimi.ramses.knowledge.parser.SystemBenchmarkParser;
import it.polimi.ramses.knowledge.domain.KnowledgeService;
import it.polimi.ramses.knowledge.domain.adaptation.specifications.QoSSpecification;
import it.polimi.ramses.knowledge.domain.architecture.Service;
import it.polimi.ramses.knowledge.parser.VulnerabilityParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.ResourceUtils;
import java.io.FileReader;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class KnowledgeInit implements InitializingBean {
    @Autowired
    private KnowledgeService knowledgeService;
    @Autowired
    private ConfigurationRepository configurationRepository;
    @Autowired
    private ProbeClient probeClient;
    @Autowired
    private Environment environment;
    @Autowired
    private VulnerabilityRepository vulnerabilityRepository;


    @Override
    public void afterPropertiesSet() throws Exception {
        String configDirPath = environment.getProperty("CONFIGURATION_PATH");
        if (configDirPath == null) {
            configDirPath = Paths.get("").toAbsolutePath().toString();
            log.warn("No configuration path specified. Using current working directory: {}", configDirPath);
        }
        FileReader architectureReader = new FileReader(ResourceUtils.getFile(configDirPath + "/system_architecture.json"));
        List<Service> serviceList = SystemArchitectureParser.parse(architectureReader);
        FileReader qoSReader = new FileReader(ResourceUtils.getFile(configDirPath + "/qos_specification.json"));
        Map<String, List<QoSSpecification>> servicesQoS = QoSParser.parse(qoSReader);
        FileReader benchmarkReader = new FileReader(ResourceUtils.getFile(configDirPath + "/system_benchmarks.json"));
        Map<String, List<SystemBenchmarkParser.ServiceImplementationBenchmarks>> servicesBenchmarks = SystemBenchmarkParser.parse(benchmarkReader);
        FileReader vulnerabilitiesReader = new FileReader(ResourceUtils.getFile(configDirPath + "/vulnerabilities.json"));
        Map<String, List<Vulnerability>> servicesVulnerabilities = VulnerabilityParser.parse(vulnerabilitiesReader, serviceList);

        // Compute the vulnerability score for each service implementation
        serviceList.forEach(service ->
                service.getPossibleImplementations().forEach((serviceImplementationId, serviceImplementation) -> {
                    List<Vulnerability> vulnerabilities = servicesVulnerabilities.get(serviceImplementationId);
                    // TODO compute the score not as the sum of the scores
                    double score = vulnerabilities.stream().mapToDouble(Vulnerability::getScore).sum();
                    log.info("Service {} implementation {} vulnerability score: {}", service.getServiceId(), serviceImplementationId, score);
                    serviceImplementation.setVulnerabilityScore(score);
                }));

        // Save vulnerabilities in the database
        vulnerabilityRepository.deleteAll();
        servicesVulnerabilities.forEach((serviceId, vulnerabilities) -> vulnerabilityRepository.saveAll(vulnerabilities));

        Map<String, ServiceInfo> probeSystemRuntimeArchitecture = probeClient.getSystemArchitecture();

        serviceList.forEach(service -> {
            ServiceInfo serviceInfo = probeSystemRuntimeArchitecture.get(service.getServiceId());
            if (serviceInfo == null)
                throw new RuntimeException("Service " + service.getServiceId() + " not found in the system  runtime architecture");
            List<String> instances = serviceInfo.getInstances();
            if (instances == null || instances.isEmpty()) {
                throw new RuntimeException("No instances found for service " + service.getServiceId());
            }
            service.setCurrentImplementationId(serviceInfo.getCurrentImplementationId());
            service.setAllQoS(servicesQoS.get(service.getServiceId()));
            servicesBenchmarks.get(service.getServiceId()).forEach(serviceImplementationBenchmarks -> {
                serviceImplementationBenchmarks.getQoSBenchmarks().forEach((adaptationClass, value) ->
                        service.getPossibleImplementations()
                                .get(serviceImplementationBenchmarks.getServiceImplementationId())
                                .setBenchmark(adaptationClass, value));
            });
            instances.forEach(instanceId -> {
                if (!instanceId.split("@")[0].equals(service.getCurrentImplementationId()))
                    throw new RuntimeException("Service " + service.getServiceId() + " has more than one running implementation");
                service.createInstance(instanceId.split("@")[1]).setCurrentStatus(InstanceStatus.ACTIVE);
            });
            service.setConfiguration(probeClient.getServiceConfiguration(service.getServiceId(), service.getCurrentImplementationId()));
            configurationRepository.save(service.getConfiguration());
            knowledgeService.addService(service);
        });

        for (Service service : serviceList) {
            ServiceConfiguration configuration = service.getConfiguration();
            if (configuration.getLoadBalancerType() != null && configuration.getLoadBalancerType().equals(ServiceConfiguration.LoadBalancerType.WEIGHTED_RANDOM)) {
                if (configuration.getLoadBalancerWeights() == null) {
                    for (Instance instance : service.getInstances())
                        configuration.addLoadBalancerWeight(instance.getInstanceId(), 1.0 / service.getInstances().size());
                } else if (!configuration.getLoadBalancerWeights().keySet().equals(service.getCurrentImplementation().getInstances().keySet())) {
                    throw new RuntimeException("Service " + service.getServiceId() + " has a load balancer weights map with different keys than the current implementation instances");
                }
            }
        }

        for (Service service : serviceList) {
            log.debug(service.toString());
        }
        log.info("Knowledge initialized");
    }
}
