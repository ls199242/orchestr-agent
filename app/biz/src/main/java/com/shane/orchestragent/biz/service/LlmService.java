package com.shane.orchestragent.biz.service;

import com.shane.orchestragent.common.exception.BizException;
import com.shane.orchestragent.integration.llm.model.ChatRequestDTO;
import com.shane.orchestragent.integration.llm.model.ChatResponseVO;

import java.util.concurrent.CompletableFuture;

/**
 * 业务层大模型调用服务接口
 *
 * @author Shane
 */
public interface LlmService {

    /**
     * 异步流式调用大模型
     *
     * @param request       请求参数
     * @param asyncCallback 异步回调监听
     * @return CompletableFuture 异步句柄
     * @throws BizException 异常
     */
    CompletableFuture<Void> asyncCall(ChatRequestDTO request, LlmAsyncCallback asyncCallback) throws BizException;

    /**
     * 同步调用大模型
     *
     * @param request 请求参数
     * @return 响应结果
     * @throws BizException 异常
     */
    ChatResponseVO chat(ChatRequestDTO request) throws BizException;

    /**
     * LLM 异步流式回调处理器
     */
    interface LlmAsyncCallback {

        void onData(ChatResponseVO data);

        void onError(Throwable e);

        void onComplete(ChatResponseVO data);
    }
}
