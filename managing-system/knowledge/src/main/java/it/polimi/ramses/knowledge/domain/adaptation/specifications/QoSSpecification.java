package it.polimi.ramses.knowledge.domain.adaptation.specifications;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
@JsonSubTypes({
        @JsonSubTypes.Type(value = Availability.class),
        @JsonSubTypes.Type(value = AverageResponseTime.class),
        @JsonSubTypes.Type(value = Vulnerability.class)
        //@JsonSubTypes.Type(value = TotalCost.class)
})
@NoArgsConstructor
public abstract class QoSSpecification {

    private Double weight;

    public abstract boolean isSatisfied(double value);

    // True if in the list of values the ones that satisfy the constraint are above a certain percentage
    public boolean isSatisfied(List<Double> values, double percentage) {
        if (values == null || values.size() == 0)
            return false;
        double count = 0;
        for (Double d : values) {
            if (isSatisfied(d))
                count += 1;
        }
        return (count / values.size()) >= percentage;
    }

    public abstract String getConstraintDescription();

    abstract void fromJson(String json);

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + "(Weight: " + weight + ", Constraint: " + getConstraintDescription() + ")"; //+ ", priority: " + priority;
    }

}
