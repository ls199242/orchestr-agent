package com.shane.orchestragent.biz.model.enums;

import com.shane.orchestragent.common.enums.AgentTypeEnum;
import lombok.Getter;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 策略编排拓扑类型枚举
 *
 * @author Shane
 */
@Getter
public enum FlowTopologyTypeEnum {

    /**
     * ReAct 协同调度流 (自愈闭环，支持结果缓存)
     */
    REACT(List.of(AgentTypeEnum.PLANNER, AgentTypeEnum.CONDUCTOR, AgentTypeEnum.EVALUATOR, AgentTypeEnum.REPORTER), true),

    /**
     * 多轮对话意图分流 (实时交互，不使用结果缓存)
     */
    MULTIPLE_CHAT(List.of(AgentTypeEnum.ROUTER, AgentTypeEnum.PLANNER, AgentTypeEnum.CONDUCTOR, AgentTypeEnum.EVALUATOR, AgentTypeEnum.REPORTER), false);

    private final List<AgentTypeEnum> systemAgents;
    private final boolean useResultCache;

    private static final Map<String, FlowTopologyTypeEnum> CACHE = Arrays.stream(values())
            .collect(Collectors.toMap(Enum::name, e -> e));

    FlowTopologyTypeEnum(List<AgentTypeEnum> systemAgents, boolean useResultCache) {
        this.systemAgents = systemAgents;
        this.useResultCache = useResultCache;
    }

    public static FlowTopologyTypeEnum of(String flowType) {
        if (flowType == null) {
            return REACT;
        }
        FlowTopologyTypeEnum type = CACHE.get(flowType.toUpperCase());
        return type != null ? type : REACT;
    }
}
