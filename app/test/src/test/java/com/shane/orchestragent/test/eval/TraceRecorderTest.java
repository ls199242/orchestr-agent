package com.shane.orchestragent.test.eval;

import com.shane.orchestragent.biz.eval.client.EvalClient;
import com.shane.orchestragent.biz.eval.model.ExecutionTraceDTO;
import com.shane.orchestragent.biz.eval.trace.TraceRecorder;
import com.shane.orchestragent.biz.eval.trace.impl.TraceRecorderImpl;
import com.shane.orchestragent.common.exception.BizException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * TraceRecorder 单元测试
 * 验证向外部 eval 系统异步触发上报与同步上报逻辑
 *
 * @author Shane
 */
public class TraceRecorderTest {

    @Test
    @DisplayName("测试 Trace 异步上报成功流程")
    public void testRecordTraceAsyncSuccess() throws Exception {
        EvalClient mockClient = Mockito.mock(EvalClient.class);
        Map<String, Object> mockResp = Map.of("traceId", "t_001", "saved", true, "nodesCount", 2);
        Mockito.when(mockClient.recordTrace(Mockito.any(ExecutionTraceDTO.class))).thenReturn(mockResp);

        TraceRecorder recorder = new TraceRecorderImpl(mockClient);

        ExecutionTraceDTO trace = ExecutionTraceDTO.builder()
                .traceId("t_001")
                .caseId("case_flight_01")
                .runId("run_100")
                .mode("FREE")
                .finalStatus("completed")
                .env("test")
                .source("EVAL")
                .build();

        CompletableFuture<Map<String, Object>> future = recorder.recordTraceAsync(trace);
        Map<String, Object> result = future.get(3, TimeUnit.SECONDS);

        Assertions.assertNotNull(result);
        Assertions.assertEquals("t_001", result.get("traceId"));
        Assertions.assertEquals(true, result.get("saved"));
        Mockito.verify(mockClient, Mockito.times(1)).recordTrace(Mockito.argThat(t -> "t_001".equals(t.getTraceId())));
    }

    @Test
    @DisplayName("测试 Trace 异步上报发生服务端异常时容错降级，不中断业务主流程")
    public void testRecordTraceAsyncErrorHandling() throws Exception {
        EvalClient mockClient = Mockito.mock(EvalClient.class);
        Mockito.when(mockClient.recordTrace(Mockito.any())).thenThrow(new BizException("EVAL_ERROR", "Eval 服务超时"));

        TraceRecorder recorder = new TraceRecorderImpl(mockClient);

        ExecutionTraceDTO trace = ExecutionTraceDTO.builder()
                .traceId("t_err_002")
                .caseId("case_002")
                .build();

        CompletableFuture<Map<String, Object>> future = recorder.recordTraceAsync(trace);
        Map<String, Object> result = future.get(3, TimeUnit.SECONDS);

        // 异步异常应当被吞吐并打印日志，返回空 Map 降级
        Assertions.assertNotNull(result);
        Assertions.assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("测试空 Trace 或空 traceId 保护")
    public void testRecordTraceEmptyHandling() throws Exception {
        EvalClient mockClient = Mockito.mock(EvalClient.class);
        TraceRecorder recorder = new TraceRecorderImpl(mockClient);

        CompletableFuture<Map<String, Object>> f1 = recorder.recordTraceAsync(null);
        Assertions.assertTrue(f1.get().isEmpty());

        CompletableFuture<Map<String, Object>> f2 = recorder.recordTraceAsync(ExecutionTraceDTO.builder().build());
        Assertions.assertTrue(f2.get().isEmpty());

        Mockito.verifyNoInteractions(mockClient);
    }

    @Test
    @DisplayName("测试同步 recordTrace 模式")
    public void testRecordTraceSync() throws BizException {
        EvalClient mockClient = Mockito.mock(EvalClient.class);
        Mockito.when(mockClient.recordTrace(Mockito.any())).thenReturn(Map.of("saved", true));

        TraceRecorder recorder = new TraceRecorderImpl(mockClient);
        ExecutionTraceDTO trace = ExecutionTraceDTO.builder().traceId("t_sync_001").build();

        Map<String, Object> res = recorder.recordTrace(trace);
        Assertions.assertEquals(true, res.get("saved"));

        // 空 traceId 抛出异常
        Assertions.assertThrows(BizException.class, () -> recorder.recordTrace(new ExecutionTraceDTO()));
    }
}
