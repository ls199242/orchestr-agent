package com.shane.orchestragent.integration.llm;

import com.shane.orchestragent.common.exception.BizException;
import com.shane.orchestragent.integration.llm.model.ChatRequestDTO;
import com.shane.orchestragent.integration.llm.model.ChatResponseVO;

import java.util.concurrent.CompletableFuture;

/**
 * 大模型客户端统一接口
 *
 * @author Shane
 */
public interface LlmClient {

    /**
     * 异步/流式调用大模型
     *
     * @param request  请求参数
     * @param callback 回调处理器
     * @return CompletableFuture 响应句柄
     * @throws BizException 异常
     */
    CompletableFuture<ChatResponseVO> asyncCall(ChatRequestDTO request, LlmCallback callback) throws BizException;

    /**
     * 底层 SSE 流式调用大模型
     *
     * @param requestJson 请求 JSON
     * @param listener    SSE 数据监听器
     * @return 异步句柄
     * @throws BizException 异常
     */
    CompletableFuture<Void> asyncCall(String requestJson, LlmSseDataListener listener) throws BizException;

    /**
     * 同步调用大模型
     *
     * @param request 请求参数
     * @return 响应结果
     * @throws BizException 异常
     */
    ChatResponseVO chat(ChatRequestDTO request) throws BizException;
}
