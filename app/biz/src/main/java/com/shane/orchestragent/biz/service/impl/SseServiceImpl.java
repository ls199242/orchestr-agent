package com.shane.orchestragent.biz.service.impl;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.shane.orchestragent.biz.service.SseService;
import com.shane.orchestragent.biz.sse.SseEmitterUTF8;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Duration;

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
}
