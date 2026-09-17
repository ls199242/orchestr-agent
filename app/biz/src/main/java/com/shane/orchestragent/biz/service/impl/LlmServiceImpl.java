package com.shane.orchestragent.biz.service.impl;

import com.shane.orchestragent.biz.service.LlmService;
import com.shane.orchestragent.biz.stream.ChatStreamResponseBuilder;
import com.shane.orchestragent.biz.stream.impl.DefaultChatStreamResponseBuilder;
import com.shane.orchestragent.common.exception.BizException;
import com.shane.orchestragent.common.utils.JsonUtils;
import com.shane.orchestragent.integration.llm.LlmClient;
import com.shane.orchestragent.integration.llm.LlmSseDataListener;
import com.shane.orchestragent.integration.llm.model.ChatRequestDTO;
import com.shane.orchestragent.integration.llm.model.ChatResponseVO;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/**
 * 业务层大模型调用服务实现
 *
 * @author Shane
 */
@Service
public class LlmServiceImpl implements LlmService {

    private final LlmClient llmClient;

    public LlmServiceImpl(LlmClient llmClient) {
        this.llmClient = llmClient;
    }

    @Override
    public CompletableFuture<Void> asyncCall(ChatRequestDTO request, LlmAsyncCallback asyncCallback) throws BizException {
        ChatStreamResponseBuilder streamBuilder = new DefaultChatStreamResponseBuilder();
        String requestJson = JsonUtils.toJsonString(request);

        return llmClient.asyncCall(requestJson, new LlmSseDataListener() {
            @Override
            public void onEvent(String event, String data) {
                if (data != null && data.contains("[DONE]")) {
                    ChatResponseVO chatResponse = streamBuilder.build();
                    if (asyncCallback != null) {
                        asyncCallback.onComplete(chatResponse);
                    }
                    return;
                }

                try {
                    ChatResponseVO partialResponse = streamBuilder.append(data);
                    if (asyncCallback != null && partialResponse != null) {
                        asyncCallback.onData(partialResponse);
                    }
                } catch (Exception e) {
                    if (asyncCallback != null) {
                        asyncCallback.onError(e);
                    }
                }
            }

            @Override
            public void onError(Throwable e) {
                if (asyncCallback != null) {
                    asyncCallback.onError(e);
                }
            }
        });
    }

    @Override
    public ChatResponseVO chat(ChatRequestDTO request) throws BizException {
        CompletableFuture<ChatResponseVO> future = new CompletableFuture<>();
        asyncCall(request, new LlmAsyncCallback() {
            @Override
            public void onData(ChatResponseVO data) {
            }

            @Override
            public void onError(Throwable e) {
                future.completeExceptionally(e);
            }

            @Override
            public void onComplete(ChatResponseVO data) {
                future.complete(data);
            }
        });

        try {
            return future.get();
        } catch (Exception e) {
            throw new BizException("LLM_CALL_FAILED", "模型同步调用失败: " + e.getMessage(), e);
        }
    }
}
