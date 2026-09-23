package com.shane.orchestragent.biz.service.impl;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.shane.orchestragent.biz.flow.StrategyFlow;
import com.shane.orchestragent.biz.service.SseService;
import com.shane.orchestragent.biz.sse.SseEmitterUTF8;
import com.shane.orchestragent.common.enums.FlowStateEnum;
import com.shane.orchestragent.common.exception.BizException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * SSE 服务实现
 *
 * @author Shane
 */
@Service
public class SseServiceImpl implements SseService {

    private static final Logger log = LoggerFactory.getLogger(SseServiceImpl.class);

    private final Cache<String, SseEmitterUTF8> clientCache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(30))
            .maximumSize(5000)
            .build();

    @Override
    public SseEmitterUTF8 createSseClient(String clientId) {
        SseEmitterUTF8 emitter = new SseEmitterUTF8(60000L);
        emitter.onCompletion(() -> clientCache.invalidate(clientId));
        emitter.onTimeout(() -> clientCache.invalidate(clientId));
        emitter.onError(e -> clientCache.invalidate(clientId));
        clientCache.put(clientId, emitter);
        return emitter;
    }

    @Override
    public void send(String clientId, String eventName, Object data) {
        SseEmitterUTF8 emitter = clientCache.getIfPresent(clientId);
        if (emitter != null) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(data));
            } catch (Exception e) {
                log.error("[SseService] 推送消息失败: clientId={}", clientId, e);
                clientCache.invalidate(clientId);
            }
        }
    }

    @Override
    public void close(String clientId) {
        SseEmitterUTF8 emitter = clientCache.getIfPresent(clientId);
        if (emitter != null) {
            emitter.complete();
            clientCache.invalidate(clientId);
        }
    }

    @Override
    public SseEmitterUTF8 attachFlow(StrategyFlow strategyFlow) {
        if (strategyFlow == null) {
            throw new IllegalArgumentException("strategyFlow cannot be null");
        }
        String flowId = strategyFlow.getFlowId();
        SseEmitterUTF8 emitter = createSseClient(flowId);

        emitter.onTimeout(() -> {
            log.warn("[SseService] 客户端连接超时，终止流程: flowId={}", flowId);
            clientCache.invalidate(flowId);
            strategyFlow.stop();
        });
        emitter.onError(e -> {
            log.warn("[SseService] 客户端连接异常，终止流程: flowId={}", flowId, e);
            clientCache.invalidate(flowId);
            strategyFlow.stop();
        });

        strategyFlow.addStreamListener((agent, chunk) -> {
            try {
                if (chunk != null && chunk.getFirstMessage() != null) {
                    emitter.send(SseEmitter.event()
                            .name("agent_chunk")
                            .data("[" + agent.getName() + "]: " + chunk.getFirstMessage().getContent()));
                }
            } catch (Exception e) {
                log.warn("[SseService] 推送 agent_chunk 失败: flowId={}", flowId, e);
                emitter.completeWithError(e);
            }
        });

        AtomicBoolean completed = new AtomicBoolean(false);
        strategyFlow.addStateChangeListener((fId, oldState, newState) -> {
            if (newState == FlowStateEnum.FINISHED || newState == FlowStateEnum.STOPPED || newState == FlowStateEnum.ERROR) {
                if (!completed.compareAndSet(false, true)) {
                    return;
                }
                try {
                    if (newState == FlowStateEnum.FINISHED) {
                        String reply = strategyFlow.getContext() != null ? strategyFlow.getContext().getRecommendResult() : "";
                        emitter.send(SseEmitter.event().name("finish").data(reply != null ? reply : ""));
                        emitter.complete();
                        log.info("[SseService][FINISH] 流式会话完成: flowId={}", flowId);
                    } else if (newState == FlowStateEnum.STOPPED) {
                        emitter.send(SseEmitter.event().name("stopped").data("流程已被主动终止"));
                        emitter.complete();
                        log.info("[SseService][STOPPED] 流式会话流程已被主动终止: flowId={}", flowId);
                    } else if (newState == FlowStateEnum.ERROR) {
                        Throwable lastError = null;
                        if (strategyFlow.getContext() != null) {
                            Object errObj = strategyFlow.getContext().getProperty("lastError");
                            if (errObj instanceof Throwable t) {
                                lastError = t;
                            }
                        }
                        if (lastError != null) {
                            emitter.completeWithError(lastError);
                        } else {
                            emitter.completeWithError(new BizException("FLOW_EXECUTION_ERROR", "流程执行异常"));
                        }
                        log.error("[SseService][ERROR] 流式会话流程执行异常: flowId={}", flowId, lastError);
                    }
                } catch (Exception e) {
                    log.error("[SseService] 推送终态事件失败: flowId={}, state={}", flowId, newState, e);
                    try {
                        emitter.completeWithError(e);
                    } catch (Exception ignored) {
                    }
                }
            }
        });

        return emitter;
    }
}
