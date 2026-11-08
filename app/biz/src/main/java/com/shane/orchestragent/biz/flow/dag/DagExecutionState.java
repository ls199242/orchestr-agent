package com.shane.orchestragent.biz.flow.dag;

import com.shane.orchestragent.biz.model.flow.DagStepStatus;
import com.shane.orchestragent.biz.model.flow.FlowDagStep;
import com.shane.orchestragent.biz.model.flow.PlanResult;
import lombok.Getter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * DAG 调度执行状态 (黑板)
 *
 * @author Shane
 */
@Getter
public class DagExecutionState {

    /** 各步骤状态 */
    private final Map<String, DagStepStatus> status = new ConcurrentHashMap<>();

    /** 各步骤结构化输出 (Map 或 String) */
    private final Map<String, Object> outputs = new ConcurrentHashMap<>();

    /** 各步骤原始文本输出 */
    private final Map<String, String> rawOutputs = new ConcurrentHashMap<>();

    /** 各步骤异常错误信息 */
    private final Map<String, String> errors = new ConcurrentHashMap<>();

    /** 各步骤执行尝试次数 */
    private final Map<String, Integer> attempts = new ConcurrentHashMap<>();

    public DagExecutionState(PlanResult plan) {
        if (plan != null && plan.getSteps() != null) {
            for (FlowDagStep s : plan.getSteps()) {
                status.put(s.getId(), DagStepStatus.PENDING);
            }
        }
    }

    public boolean isAllSucceeded() {
        return status.values().stream()
                .allMatch(s -> s == DagStepStatus.SUCCEEDED || s == DagStepStatus.SKIPPED);
    }
}
