package com.shane.orchestragent.biz.listener;

import com.shane.orchestragent.biz.agent.Agent;
import com.shane.orchestragent.biz.flow.StrategyFlow;
import com.shane.orchestragent.biz.model.agent.AgentResult;

/**
 * 策略流程智能体节点执行监听器
 *
 * @author Shane
 */
public interface FlowAgentEventListener {

    /**
     * 智能体运行前触发
     *
     * @param flow 流程实例
     * @param agent 目标智能体
     */
    default void onBefore(StrategyFlow flow, Agent agent) {
    }

    /**
     * 智能体运行成功后触发
     *
     * @param flow 流程实例
     * @param agent 目标智能体
     * @param agentResult 执行产出结果
     */
    default void onAfter(StrategyFlow flow, Agent agent, AgentResult agentResult) {
    }

    /**
     * 智能体运行发生异常时触发
     *
     * @param flow 流程实例
     * @param agent 目标智能体
     * @param e 抛出的异常
     */
    default void onError(StrategyFlow flow, Agent agent, Exception e) {
    }
}
