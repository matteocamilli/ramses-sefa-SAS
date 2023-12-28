package it.polimi.ramses.analyse.domain;

import it.polimi.ramses.knowledge.domain.adaptation.specifications.Availability;
import it.polimi.ramses.knowledge.domain.adaptation.specifications.AverageResponseTime;
import it.polimi.ramses.knowledge.domain.adaptation.specifications.Vulnerability;
import it.polimi.ramses.knowledge.domain.architecture.Instance;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;


@Getter
@Setter
@Slf4j
public class InstanceStats {
    private Instance instance;
    private double averageResponseTime;
    private double availability;
    private double vulnerabilityScore;
    private boolean fromNewData;

    public InstanceStats(Instance instance, double averageResponseTime, double availability) {
        this.instance = instance;
        this.averageResponseTime = averageResponseTime;
        this.availability = availability;
        this.vulnerabilityScore = instance.getVulnerabilityScore();
        fromNewData = true;
    }

    public InstanceStats(Instance instance) {
        this.instance = instance;
        availability = instance.getLatestValueForQoS(Availability.class).getDoubleValue();
        averageResponseTime = instance.getLatestValueForQoS(AverageResponseTime.class).getDoubleValue();
        vulnerabilityScore = instance.getVulnerabilityScore();
        this.fromNewData = false;
    }

}
