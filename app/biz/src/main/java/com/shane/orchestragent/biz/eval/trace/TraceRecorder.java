package com.shane.orchestragent.biz.eval.trace;

import com.shane.orchestragent.biz.eval.model.ExecutionTraceDTO;
import com.shane.orchestragent.common.exception.BizException;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 链路 Trace 记录与上报器接口
 * <p>
 * 负责在收集完链路 Trace 数据后，触发调用外部 eval 系统的 Trace 存储接口（POST /api/eval/traces），
 * 支持异步非阻塞上报及同步可靠上报模式。
 *
 * @author Shane
 */
public interface TraceRecorder {

    /**
     * 异步触发外部 eval 系统接口调用插入 Trace 记录
     * <p>
     * 使用轻量虚拟线程在后台异步执行，不阻塞工作流主执行链路，并包含异常容错与审计日志。
     *
     * @param trace 收集完成的完整链路 Trace 数据载荷
     * @return 包含持久化结果确认字典的 CompletableFuture
     */
    CompletableFuture<Map<String, Object>> recordTraceAsync(ExecutionTraceDTO trace);

    /**
     * 同步触发外部 eval 系统接口调用插入 Trace 记录
     *
     * @param trace 收集完成的完整链路 Trace 数据载荷
     * @return 评估服务端存储确认结果字典
     * @throws BizException 若网络或服务端通信异常
     */
    Map<String, Object> recordTrace(ExecutionTraceDTO trace) throws BizException;
}
