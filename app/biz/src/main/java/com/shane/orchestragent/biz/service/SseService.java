package com.shane.orchestragent.biz.service;

import com.shane.orchestragent.biz.sse.SseEmitterUTF8;

/**
 * SSE 流式通信服务
 *
 * @author Shane
 */
public interface SseService {

    SseEmitterUTF8 createSseClient(String clientId);

    void send(String clientId, String eventName, Object data);

    void close(String clientId);
}
