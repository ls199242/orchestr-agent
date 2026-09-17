package com.shane.orchestragent.biz.agent.base;

import com.shane.orchestragent.biz.context.AgentContext;
import com.shane.orchestragent.biz.context.StrategyContext;
import com.shane.orchestragent.biz.listener.LlmAgentStreamListener;
import com.shane.orchestragent.biz.model.agent.AgentResult;
import com.shane.orchestragent.biz.model.agent.LlmAgentResult;
import com.shane.orchestragent.biz.model.agent.LlmConfig;
import com.shane.orchestragent.biz.model.flow.FlowAgentMessage;
import com.shane.orchestragent.biz.service.LlmService;
import com.shane.orchestragent.common.enums.AgentTypeEnum;
import com.shane.orchestragent.common.enums.LlmRoleEnum;
import com.shane.orchestragent.common.exception.BizErrorFactory;
import com.shane.orchestragent.common.exception.BizException;
import com.shane.orchestragent.integration.llm.model.*;
import com.shane.orchestragent.prompt.service.PromptService;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

/**
 * 大模型驱动智能体基类 (彻底无状态化改造，消除并发覆盖与状态污染技术债)
 *
 * @author Shane
 */
public abstract class BaseLlmAgent<CONTEXT extends AgentContext> extends BaseAgent<CONTEXT> {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    protected final LlmService llmService;
    protected final PromptService promptService;
    protected final LlmConfig llmConfig;

    /** 流式输出监听器列表 (修正拼写 steam -> stream) */
    protected final List<LlmAgentStreamListener> streamListeners = new CopyOnWriteArrayList<>();

    public BaseLlmAgent(AgentTypeEnum type, LlmConfig llmConfig, LlmService llmService, PromptService promptService) {
        super(type.name(), type.getDescription(), type);
        this.llmConfig = llmConfig;
        this.llmService = llmService;
        this.promptService = promptService;
    }

    public BaseLlmAgent(String name, String description, AgentTypeEnum type, LlmConfig llmConfig, LlmService llmService, PromptService promptService) {
        super(name, description, type);
        this.llmConfig = llmConfig;
        this.llmService = llmService;
        this.promptService = promptService;
    }

    public void registerStreamListener(LlmAgentStreamListener listener) {
        if (listener != null) {
            this.streamListeners.add(listener);
        }
    }

    @Override
    public AgentResult execute(CONTEXT context) throws BizException {
        if (isStopped()) {
            log.info("[Agent: {}] 处于停止状态，跳过执行", getName());
            return null;
        }
        return doChat(context);
    }

    protected AgentResult doChat(CONTEXT context) throws BizException {
        List<ChatMessageDTO> messages = buildChatMessages(context);

        ChatRequestDTO chatRequest = ChatRequestDTO.builder()
                .model(llmConfig.getModel())
                .messages(messages)
                .temperature(llmConfig.getTemperature())
                .maxTokens(llmConfig.getMaxTokens())
                .presencePenalty(llmConfig.getPresencePenalty())
                .stream(true)
                .build();

        ChatResponseVO responseVO;
        CompletableFuture<ChatResponseVO> future = new CompletableFuture<>();
        try {
            llmService.asyncCall(chatRequest, new LlmService.LlmAsyncCallback() {
                @Override
                public void onData(ChatResponseVO data) {
                    streamListeners.forEach(listener -> listener.onChatResponse(BaseLlmAgent.this, data, null));
                }

                @Override
                public void onComplete(ChatResponseVO data) {
                    future.complete(data);
                }

                @Override
                public void onError(Throwable e) {
                    streamListeners.forEach(listener -> listener.onChatResponse(BaseLlmAgent.this, null, e));
                    future.completeExceptionally(e);
                }
            });
            responseVO = future.get(llmConfig.getNodeWaitTime(), TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            log.error("[Agent: {}] LLM 执行异常或超时", getName(), e);
            throw new BizException(BizErrorFactory.getInstance().llmAgentTimeoutOrCancel());
        }

        if (responseVO == null || CollectionUtils.isEmpty(responseVO.getChatMessages())) {
            throw new BizException("LLM_EMPTY_RESPONSE", "大模型返回结果为空");
        }

        ChatMessageVO chatMessage = responseVO.getFirstMessage();
        String content = chatMessage != null ? com.shane.orchestragent.biz.tool.ChatJsonFormattingUtils.format(chatMessage.getContent()) : "";
        String reasoning = chatMessage != null ? com.shane.orchestragent.biz.tool.ChatJsonFormattingUtils.format(chatMessage.getReasoningContent()) : null;

        // 扩展点处理与上下文沉淀
        String finalResponse = afterExtensionHandler(context, content);
        addAgentResponse(context, finalResponse);

        return new LlmAgentResult(finalResponse, reasoning, responseVO);
    }

    protected List<ChatMessageDTO> buildChatMessages(CONTEXT context) throws BizException {
        SystemChatMessageDTO systemPrompt = buildSystemPrompt(context);
        if (systemPrompt != null && StringUtils.isNotEmpty(systemPrompt.getContent())) {
            streamListeners.forEach(p -> p.onUserPrompt(this, LlmRoleEnum.SYSTEM, systemPrompt.getContent()));
        }

        List<ChatMessageDTO> userMessages = buildMessages(context);
        if (CollectionUtils.isNotEmpty(userMessages)) {
            streamListeners.forEach(p -> p.onUserPrompt(this, LlmRoleEnum.USER, userMessages.get(userMessages.size() - 1).getContent()));
        }

        List<ChatMessageDTO> messages = new ArrayList<>();
        if (systemPrompt != null) {
            messages.add(systemPrompt);
        }
        if (userMessages != null) {
            messages.addAll(userMessages);
        }
        return messages;
    }

    protected String afterExtensionHandler(CONTEXT context, String response) throws BizException {
        Object result = afterExtension(response, context);
        return result != null ? result.toString().trim() : "";
    }

    protected void addAgentResponse(CONTEXT context, String output) {
        if (context instanceof StrategyContext strategyContext) {
            FlowAgentMessage message = FlowAgentMessage.builder()
                    .agentType(agentType)
                    .agentName(getName())
                    .output(output)
                    .timestamp(System.currentTimeMillis())
                    .build();
            strategyContext.addChatMessage(message);
        }
    }

    public LlmConfig getLlmConfig() {
        return llmConfig;
    }

    protected abstract SystemChatMessageDTO buildSystemPrompt(CONTEXT context) throws BizException;

    protected abstract List<ChatMessageDTO> buildMessages(CONTEXT context) throws BizException;
}
