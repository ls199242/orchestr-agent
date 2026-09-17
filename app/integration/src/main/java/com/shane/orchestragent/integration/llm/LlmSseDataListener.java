package com.shane.orchestragent.integration.llm;

/**
 * 大模型 SSE 流式数据监听器
 *
 * @author Shane
 */
public interface LlmSseDataListener {

    /**
     * 收到 SSE 事件数据块
     *
     * @param event 事件名
     * @param data 数据内容
     */
    void onEvent(String event, String data);

    /**
     * 异常发生
     *
     * @param e 异常
     */
    void onError(Throwable e);
}
