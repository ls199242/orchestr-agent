package com.shane.orchestragent.biz.recorder.impl;

import com.shane.orchestragent.biz.agent.Agent;
import com.shane.orchestragent.biz.flow.StrategyFlow;
import com.shane.orchestragent.biz.model.agent.AgentResult;
import com.shane.orchestragent.biz.model.flow.FlowProcessText;
import com.shane.orchestragent.biz.recorder.FlowExecutionRecorder;
import com.shane.orchestragent.biz.service.FlowService;
import com.shane.orchestragent.common.enums.FlowStateEnum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 流程执行记录器实现类 (记录状态变更并持久化执行文案流水)
 *
 * @author Shane
 */
@Component
public class FlowExecutionRecorderImpl implements FlowExecutionRecorder {

    private static final Logger log = LoggerFactory.getLogger(FlowExecutionRecorderImpl.class);

    private final FlowService flowService;

    public FlowExecutionRecorderImpl(FlowService flowService) {
        this.flowService = flowService;
    }

    @Override
    public void onStateChange(String flowId, FlowStateEnum oldState, FlowStateEnum newState) {
        log.info("[FlowRecorder] 流程状态流转: flowId={}, {} -> {}", flowId, oldState, newState);
        if (flowService != null) {
            flowService.markState(flowId, newState);
        }
    }

    @Override
    public void onBefore(StrategyFlow flow, Agent agent) {
        log.info("[FlowRecorder] 节点准备执行: flowId={}, agent={}", flow.getFlowId(), agent.getName());
    }

    @Override
    public void onAfter(StrategyFlow flow, Agent agent, AgentResult agentResult) {
        log.info("[FlowRecorder] 节点执行完成: flowId={}, agent={}", flow.getFlowId(), agent.getName());
        if (flowService != null && agentResult != null) {
            FlowProcessText text = FlowProcessText.builder()
                    .title(agent.getName())
                    .description(agentResult.getOutput())
                    .timestamp(System.currentTimeMillis())
                    .build();
            flowService.saveProcessText(flow.getFlowId(), text);
        }
    }

    @Override
    public void onError(StrategyFlow flow, Agent agent, Exception e) {
        log.error("[FlowRecorder] 节点执行失败: flowId={}, agent={}", flow.getFlowId(), agent.getName(), e);
    }
}
