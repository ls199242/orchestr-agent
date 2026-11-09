package com.shane.orchestragent.biz.recorder.impl;

import com.shane.orchestragent.biz.agent.Agent;
import com.shane.orchestragent.biz.eval.context.EvalCaseContext;
import com.shane.orchestragent.biz.eval.model.ExecutionTraceDTO;
import com.shane.orchestragent.biz.eval.trace.TraceRecorder;
import com.shane.orchestragent.biz.flow.StrategyFlow;
import com.shane.orchestragent.biz.flow.store.FlowStore;
import com.shane.orchestragent.biz.model.agent.AgentResult;
import com.shane.orchestragent.biz.model.flow.FlowProcessText;
import com.shane.orchestragent.biz.recorder.FlowExecutionRecorder;
import com.shane.orchestragent.biz.service.FlowService;
import com.shane.orchestragent.common.enums.FlowStateEnum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
    private TraceRecorder traceRecorder;

    public FlowExecutionRecorderImpl(FlowService flowService) {
        this.flowService = flowService;
    }

    @Autowired(required = false)
    public void setTraceRecorder(TraceRecorder traceRecorder) {
        this.traceRecorder = traceRecorder;
    }

    @Override
    public void onStateChange(String flowId, FlowStateEnum oldState, FlowStateEnum newState) {
        log.info("[FlowRecorder] 流程状态流转: flowId={}, {} -> {}", flowId, oldState, newState);
        if (flowService != null) {
            flowService.markState(flowId, newState);
        }
        if (traceRecorder != null && (newState == FlowStateEnum.FINISHED || newState == FlowStateEnum.STOPPED || newState == FlowStateEnum.ERROR)) {
            triggerTraceIngestIfApplicable(flowId, newState);
        }
    }

    /**
     * 流程终态时尝试触发评测 Trace 异步上报至外部 eval 系统
     */
    private void triggerTraceIngestIfApplicable(String flowId, FlowStateEnum state) {
        try {
            StrategyFlow flow = FlowStore.get(flowId);
            if (flow != null && flow.getContext() != null) {
                EvalCaseContext evalContext = flow.getContext().getEvalCaseContext();
                if (evalContext != null && evalContext.getTraceCollector() != null && evalContext.markReported()) {
                    evalContext.getTraceCollector().setFinalStatus(state == FlowStateEnum.FINISHED ? "completed" : state.name().toLowerCase());
                    ExecutionTraceDTO trace = evalContext.getTraceCollector().buildTrace();
                    log.info("[FlowRecorder] 流程达到终态 [{}]，异步触发 Trace 上报: flowId={}, traceId={}", flowId, trace.getTraceId());
                    traceRecorder.recordTraceAsync(trace);
                }
            }
        } catch (Exception e) {
            log.warn("[FlowRecorder] 终态 Trace 上报触发异常: flowId={}, error={}", flowId, e.getMessage());
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
