package com.shane.orchestragent.biz.service;

import com.shane.orchestragent.biz.flow.StrategyFlow;
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

    /**
     * 将策略工作流与 SSE 客户端绑定
     * 自动注册智能体增量思考片段 (agent_chunk) 监听与终态生命周期流转事件 (finish / stopped / error)
     *
     * @param strategyFlow 策略工作流实例
     * @return 绑定完成的 SseEmitterUTF8 发射器
     */
    SseEmitterUTF8 attachFlow(StrategyFlow strategyFlow);
}
