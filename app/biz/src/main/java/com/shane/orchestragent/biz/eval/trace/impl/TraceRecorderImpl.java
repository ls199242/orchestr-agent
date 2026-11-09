package com.shane.orchestragent.biz.eval.trace.impl;

import com.shane.orchestragent.biz.eval.client.EvalClient;
import com.shane.orchestragent.biz.eval.model.ExecutionTraceDTO;
import com.shane.orchestragent.biz.eval.trace.TraceRecorder;
import com.shane.orchestragent.common.exception.BizException;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 链路 Trace 记录与上报器实现类
 * <p>
 * 收集完成后，异步非阻塞触发外部 eval 系统的 Trace 存储接口，持久化执行明细流水与指标。
 *
 * @author Shane
 */
@Component
public class TraceRecorderImpl implements TraceRecorder {

    private static final Logger log = LoggerFactory.getLogger(TraceRecorderImpl.class);

    private final EvalClient evalClient;
    private final ExecutorService asyncExecutor;

    public TraceRecorderImpl(EvalClient evalClient) {
        this.evalClient = evalClient;
        // 采用轻量虚拟线程池作为异步上报执行器，杜绝阻塞业务线程池
        this.asyncExecutor = Executors.newVirtualThreadPerTaskExecutor();
    }

    /**
     * 允许外部或测试注入自定义 Executor
     */
    public TraceRecorderImpl(EvalClient evalClient, ExecutorService asyncExecutor) {
        this.evalClient = evalClient;
        this.asyncExecutor = asyncExecutor != null ? asyncExecutor : Executors.newVirtualThreadPerTaskExecutor();
    }

    @Override
    public CompletableFuture<Map<String, Object>> recordTraceAsync(ExecutionTraceDTO trace) {
        if (trace == null || StringUtils.isBlank(trace.getTraceId())) {
            log.warn("[TraceRecorder] 待异步上报的 Trace 为空或缺少 traceId，忽略本次上报");
            return CompletableFuture.completedFuture(Collections.emptyMap());
        }

        String traceId = trace.getTraceId();
        log.info("[TraceRecorder] 异步触发向 eval 系统上报 Trace 记录: traceId={}, caseId={}, runId={}",
                traceId, trace.getCaseId(), trace.getRunId());

        return CompletableFuture.supplyAsync(() -> {
            try {
                Map<String, Object> result = evalClient.recordTrace(trace);
                log.info("[TraceRecorder] Trace 异步上报成功: traceId={}, result={}", traceId, result);
                return result;
            } catch (Exception e) {
                log.error("[TraceRecorder] Trace 异步上报失败: traceId={}, error={}", traceId, e.getMessage(), e);
                return Collections.emptyMap();
            }
        }, asyncExecutor);
    }

    @Override
    public Map<String, Object> recordTrace(ExecutionTraceDTO trace) throws BizException {
        if (trace == null || StringUtils.isBlank(trace.getTraceId())) {
            throw new BizException("TRACE_INVALID", "待存储的 Trace 载荷或 traceId 不能为空");
        }
        return evalClient.recordTrace(trace);
    }
}
