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
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

/**
 * 大模型驱动智能体抽象基类
 * <p>
 * 基于无状态化设计，封装了与大模型交互的核心全生命周期流程：
 * <ul>
 *   <li>系统提示词与对话上下文的动态渲染与编排</li>
 *   <li>流式响应推送与监听广播机制</li>
 *   <li>异步大模型调用、超时截断与主动取消中断（支持并发打断）</li>
 *   <li>基于 Fail-Fast 的返回结果合法性校验</li>
 *   <li>响应结果清洗与后置切面（{@link com.shane.orchestragent.biz.agent.AgentAdvisor}）增强</li>
 *   <li>执行结果沉淀至策略上下文（{@link StrategyContext}）</li>
 * </ul>
 *
 * @param <CONTEXT> 智能体执行上下文泛型，必须继承自 {@link AgentContext}
 * @author Shane
 */
public abstract class BaseLlmAgent<CONTEXT extends AgentContext> extends BaseAgent<CONTEXT> {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    /**
     * 大模型底层调用服务
     */
    protected final LlmService llmService;

    /**
     * 提示词工程与模板渲染服务
     */
    protected final PromptService promptService;

    /**
     * 当前智能体绑定的大模型超参配置（包含模型标识、温度参数、超时时间等）
     */
    protected final LlmConfig llmConfig;

    /**
     * 流式输出监听器列表（采用线程安全的写时复制集合）
     */
    protected final List<LlmAgentStreamListener> streamListeners = new CopyOnWriteArrayList<>();

    /**
     * 当前正在执行的大模型异步任务句柄，用于支持主动取消打断
     */
    protected volatile CompletableFuture<ChatResponseVO> chatFuture;

    /**
     * 底层网络通信调用异步句柄，用于在流程终止时级联取消底层 HTTP/RPC 请求
     */
    protected volatile CompletableFuture<Void> serviceFuture;

    /**
     * 构造大模型智能体（使用智能体类型枚举的名称和描述）
     *
     * @param type          智能体类型枚举
     * @param llmConfig     大模型参数配置
     * @param llmService    大模型调用服务
     * @param promptService 提示词服务
     */
    public BaseLlmAgent(AgentTypeEnum type, LlmConfig llmConfig, LlmService llmService, PromptService promptService) {
        super(type.name(), type.getDescription(), type);
        this.llmConfig = llmConfig;
        this.llmService = llmService;
        this.promptService = promptService;
    }

    /**
     * 构造大模型智能体（自定义名称和描述）
     *
     * @param name          智能体名称
     * @param description   智能体描述
     * @param type          智能体类型枚举
     * @param llmConfig     大模型参数配置
     * @param llmService    大模型调用服务
     * @param promptService 提示词服务
     */
    public BaseLlmAgent(String name, String description, AgentTypeEnum type, LlmConfig llmConfig, LlmService llmService, PromptService promptService) {
        super(name, description, type);
        this.llmConfig = llmConfig;
        this.llmService = llmService;
        this.promptService = promptService;
    }

    /**
     * 启动智能体，并重置内部异步任务 Future 句柄
     */
    @Override
    public void start() {
        super.start();
        synchronized (this) {
            this.chatFuture = null;
            this.serviceFuture = null;
        }
    }

    /**
     * 停止智能体
     * <p>
     * 设置停止标记，并立即级联取消当前正在执行中的异步大模型请求与网络调用。
     */
    @Override
    public void stop() {
        super.stop();
        synchronized (this) {
            if (this.chatFuture != null) {
                this.chatFuture.cancel(true);
                this.chatFuture = null;
            }
            if (this.serviceFuture != null) {
                this.serviceFuture.cancel(true);
                this.serviceFuture = null;
            }
        }
    }

    /**
     * 注册大模型流式响应监听器
     *
     * @param listener 流式监听器
     */
    public void registerStreamListener(LlmAgentStreamListener listener) {
        if (listener != null) {
            this.streamListeners.add(listener);
        }
    }

    /**
     * 执行大模型驱动智能体的核心业务逻辑
     * <p>
     * 包含提示词装配、流式调用、超时等待、外部中断感知、响应非空校验、后置切面增强及上下文回写。
     *
     * @param context 执行上下文
     * @return 大模型智能体产出结果 {@link LlmAgentResult}
     * @throws BizException 若调用超时、被外部中断或模型返回空响应
     */
    @Override
    protected AgentResult doExecute(CONTEXT context) throws BizException {
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
        CompletableFuture<Void> callFuture = null;
        synchronized (this) {
            this.chatFuture = future;
        }
        try {
            callFuture = llmService.asyncCall(chatRequest, new LlmService.LlmAsyncCallback() {
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
            synchronized (this) {
                this.serviceFuture = callFuture;
            }
            responseVO = future.get(llmConfig.getNodeWaitTime(), TimeUnit.MILLISECONDS);
        } catch (CancellationException | InterruptedException e) {
            log.info("[Agent: {}] LLM 执行被主动中断或取消", getName());
            throw BizErrorFactory.getInstance().strategyFlowStopped();
        } catch (Exception e) {
            if (future.isCancelled() || (callFuture != null && callFuture.isCancelled())) {
                log.info("[Agent: {}] LLM 执行被主动中断或取消", getName());
                throw BizErrorFactory.getInstance().strategyFlowStopped();
            }
            log.error("[Agent: {}] LLM 执行异常或超时", getName(), e);
            throw new BizException(BizErrorFactory.getInstance().llmAgentTimeoutOrCancel());
        } finally {
            synchronized (this) {
                if (this.chatFuture == future) {
                    this.chatFuture = null;
                }
                if (this.serviceFuture == callFuture) {
                    this.serviceFuture = null;
                }
            }
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

    /**
     * 编排组装发送给大模型的完整消息序列（包含系统提示词和用户/上下文消息）
     * <p>
     * 同时触发流式监听器广播发送给大模型的 Prompt 事件。
     *
     * @param context 执行上下文
     * @return 组装完成的消息列表
     * @throws BizException 若提示词构建过程发生异常
     */
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

    /**
     * 格式化并获取系统提示词消息（可用于外部预览或调试）
     *
     * @param context 执行上下文
     * @return 系统消息 DTO
     * @throws BizException 若提示词构建失败
     */
    public SystemChatMessageDTO formatSystemMessage(CONTEXT context) throws BizException {
        return buildSystemPrompt(context);
    }

    /**
     * 格式化并获取完整的会话消息列表（可用于外部预览或调试）
     *
     * @param context 执行上下文
     * @return 完整的消息 DTO 列表
     * @throws BizException 若提示词构建失败
     */
    public List<ChatMessageDTO> formatMessages(CONTEXT context) throws BizException {
        return buildChatMessages(context);
    }

    /**
     * 处理切面后置增强并格式化最终字符串
     *
     * @param context  执行上下文
     * @param response 大模型产出的原始文本响应
     * @return 增强处理后的最终响应文本
     * @throws BizException 若切面校验失败或执行异常
     */
    protected String afterExtensionHandler(CONTEXT context, String response) throws BizException {
        Object result = afterExtension(response, context);
        return result != null ? result.toString().trim() : "";
    }

    /**
     * 将智能体输出写入策略上下文历史消息列表中
     *
     * @param context 上下文对象
     * @param output  智能体产出文本
     */
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

    /**
     * 获取大模型配置对象
     *
     * @return 大模型超参配置
     */
    public LlmConfig getLlmConfig() {
        return llmConfig;
    }

    /**
     * 构建系统提示词 (System Prompt)
     * <p>
     * 由具体智能体子类根据业务角色与提示词模板实现。
     *
     * @param context 执行上下文
     * @return 系统提示词消息对象
     * @throws BizException 若构建失败
     */
    protected abstract SystemChatMessageDTO buildSystemPrompt(CONTEXT context) throws BizException;

    /**
     * 构建用户消息与对话历史 (User Messages)
     * <p>
     * 由具体智能体子类根据当前流程上下文与任务目标组装。
     *
     * @param context 执行上下文
     * @return 用户消息列表
     * @throws BizException 若构建失败
     */
    protected abstract List<ChatMessageDTO> buildMessages(CONTEXT context) throws BizException;
}
