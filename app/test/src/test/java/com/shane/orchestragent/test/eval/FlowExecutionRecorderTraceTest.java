package com.shane.orchestragent.test.eval;

import com.shane.orchestragent.biz.context.impl.DefaultStrategyContext;
import com.shane.orchestragent.biz.eval.context.EvalCaseContext;
import com.shane.orchestragent.biz.eval.trace.TraceRecorder;
import com.shane.orchestragent.biz.flow.BaseStrategyFlow;
import com.shane.orchestragent.biz.flow.store.FlowStore;
import com.shane.orchestragent.biz.recorder.impl.FlowExecutionRecorderImpl;
import com.shane.orchestragent.biz.service.FlowService;
import com.shane.orchestragent.common.enums.FlowStateEnum;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * FlowExecutionRecorderImpl 与 TraceRecorder 联动测试
 * 验证流程终态（FINISHED / STOPPED / ERROR）时自动触发评测 Trace 异步上报
 *
 * @author Shane
 */
public class FlowExecutionRecorderTraceTest {

    @Test
    @DisplayName("测试工作流流转至 FINISHED 终态时，FlowExecutionRecorder 自动触发 TraceRecorder 异步上报")
    public void testOnStateChangeFinishedTriggersTraceIngest() {
        FlowService mockFlowService = Mockito.mock(FlowService.class);
        TraceRecorder mockTraceRecorder = Mockito.mock(TraceRecorder.class);
        Mockito.when(mockTraceRecorder.recordTraceAsync(Mockito.any()))
                .thenReturn(CompletableFuture.completedFuture(Map.of("saved", true)));

        FlowExecutionRecorderImpl recorder = new FlowExecutionRecorderImpl(mockFlowService);
        recorder.setTraceRecorder(mockTraceRecorder);

        String flowId = "flow_trace_test_" + System.currentTimeMillis();
        DefaultStrategyContext context = new DefaultStrategyContext();
        EvalCaseContext evalContext = new EvalCaseContext("case_flight_01", "run_1", 1, "FREE", null);
        context.setEvalCaseContext(evalContext);

        BaseStrategyFlow mockFlow = Mockito.mock(BaseStrategyFlow.class);
        Mockito.when(mockFlow.getFlowId()).thenReturn(flowId);
        Mockito.when(mockFlow.getContext()).thenReturn(context);

        FlowStore.add(flowId, mockFlow);
        try {
            // 触发状态流转: RUNNING -> FINISHED
            recorder.onStateChange(flowId, FlowStateEnum.RUNNING, FlowStateEnum.FINISHED);

            // 验证触发了异步上报
            Mockito.verify(mockTraceRecorder, Mockito.times(1)).recordTraceAsync(Mockito.argThat(t ->
                    "case_flight_01".equals(t.getCaseId()) && "completed".equals(t.getFinalStatus())
            ));
            Assertions.assertTrue(evalContext.isReported());

            // 再次流转不重复触发
            recorder.onStateChange(flowId, FlowStateEnum.FINISHED, FlowStateEnum.FINISHED);
            Mockito.verify(mockTraceRecorder, Mockito.times(1)).recordTraceAsync(Mockito.any());
        } finally {
            FlowStore.remove(flowId);
        }
    }
}
