package com.shane.orchestragent.biz.model.flow;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 规划器产出结果
 *
 * @author Shane
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlanResult implements Serializable {

    private List<FlowStep> steps;

    public boolean hasSteps() {
        return steps != null && !steps.isEmpty();
    }
}
