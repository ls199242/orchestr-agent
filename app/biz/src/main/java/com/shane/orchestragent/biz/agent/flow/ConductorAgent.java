package com.shane.orchestragent.biz.agent.flow;

import com.shane.orchestragent.biz.agent.base.BaseLlmAgent;
import com.shane.orchestragent.biz.context.StrategyContext;
import com.shane.orchestragent.biz.model.agent.LlmConfig;
import com.shane.orchestragent.biz.model.flow.FlowAgentMessage;
import com.shane.orchestragent.biz.service.LlmService;
import com.shane.orchestragent.common.enums.AgentTypeEnum;
import com.shane.orchestragent.common.exception.BizErrorFactory;
import com.shane.orchestragent.common.exception.BizException;
import com.shane.orchestragent.common.utils.JsonUtils;
import com.shane.orchestragent.integration.llm.model.AssistantChatMessageDTO;
import com.shane.orchestragent.integration.llm.model.ChatMessageDTO;
import com.shane.orchestragent.integration.llm.model.SystemChatMessageDTO;
import com.shane.orchestragent.integration.llm.model.UserChatMessageDTO;
import com.shane.orchestragent.prompt.service.PromptService;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 步进调度指挥智能体 (严格基于配置中心渲染，精准驱动状态机与自愈响应)
 *
 * @author Shane
 */
public class ConductorAgent extends BaseLlmAgent<StrategyContext> {

    public ConductorAgent(LlmConfig llmConfig, LlmService llmService, PromptService promptService) {
        super(AgentTypeEnum.CONDUCTOR, llmConfig, llmService, promptService);
    }

    @Override
    protected SystemChatMessageDTO buildSystemPrompt(StrategyContext context) throws BizException {
        Map<String, Object> properties = new HashMap<>(context.getProperties());
        properties.put("strategy_target", context.getStrategyTarget());

        if (context.getPlanResult() != null) {
            properties.put("plan_steps", JsonUtils.toJsonString(context.getPlanResult().getSteps()));
        } else {
            properties.put("plan_steps", "[]");
        }

        properties.put("history_messages", JsonUtils.toJsonString(context.getChatMessages()));

        // 提取当前流程中挂载的所有外部工具定义与入参 Schema
        List<Map<String, Object>> toolList = new ArrayList<>();
        if (context.getAgents() != null) {
            for (com.shane.orchestragent.biz.agent.Agent agent : context.getAgents().values()) {
                if (agent.getType() == AgentTypeEnum.TOOL && agent instanceof ToolAgent toolAgent) {
                    Map<String, Object> toolMap = new HashMap<>();
                    toolMap.put("name", toolAgent.getName());
                    toolMap.put("description", toolAgent.getDescription());
                    if (toolAgent.getToolConfig() != null) {
                        toolMap.put("title", toolAgent.getToolConfig().getTitle());
                        toolMap.put("requestJsonSchema", toolAgent.getToolConfig().getRequestJsonSchema());
                    }
                    toolList.add(toolMap);
                }
            }
        }
        properties.put("tool_list", JsonUtils.toJsonString(toolList));

        // 注入来自 Evaluator 的打回批评反馈 (如有)
        String feedback = context.getEvaluationFeedback();
        properties.put("evaluator_feedback", StringUtils.isNotEmpty(feedback) ? feedback : "无 (前序执行正常)");

        String template = llmConfig != null ? llmConfig.getSystemPrompt() : null;
        if (StringUtils.isBlank(template)) {
            throw BizErrorFactory.getInstance().agentSystemPromptMissing("CONDUCTOR");
        }
        String prompt = promptService.renderPrompt(template, properties);
        return new SystemChatMessageDTO(prompt);
    }

    @Override
    protected List<ChatMessageDTO> buildMessages(StrategyContext context) throws BizException {
        List<ChatMessageDTO> chatMessages = new ArrayList<>();

        for (FlowAgentMessage chatMessage : context.getChatMessages()) {
            if (chatMessage == null || chatMessage.getAgentType() == null) {
                continue;
            }
            switch (chatMessage.getAgentType()) {
                case PLANNER -> {
                    chatMessages.add(new UserChatMessageDTO("【任务规划步骤】: " + chatMessage.getOutput()));
                }
                case TOOL -> {
                    chatMessages.add(new UserChatMessageDTO("【工具 " + chatMessage.getAgentName() + " 真实调用返回结果】: " + chatMessage.getOutput()));
                }
                case CONDUCTOR -> {
                    chatMessages.add(new AssistantChatMessageDTO(chatMessage.getOutput()));
                }
                case WORKER -> {
                    chatMessages.add(new UserChatMessageDTO("【工作节点 " + chatMessage.getAgentName() + " 执行结果】: " + chatMessage.getOutput()));
                }
                case EVALUATOR -> {
                    chatMessages.add(new UserChatMessageDTO("【整改要求】: " + chatMessage.getOutput()));
                }
                default -> {
                    if (StringUtils.isNotEmpty(chatMessage.getOutput())) {
                        chatMessages.add(new UserChatMessageDTO(chatMessage.getOutput()));
                    }
                }
            }
        }

        // 如果存在打回自愈意见且尚未以 EVALUATOR 消息记录，注入提醒
        if (StringUtils.isNotEmpty(context.getEvaluationFeedback())) {
            boolean hasEvaluatorMsg = context.getChatMessages().stream()
                    .anyMatch(m -> m.getAgentType() == AgentTypeEnum.EVALUATOR);
            if (!hasEvaluatorMsg) {
                chatMessages.add(new UserChatMessageDTO("【特别注意】战略评估器提出整改要求: " + context.getEvaluationFeedback() + "\n请优先指派对应 Worker 执行修补！"));
            }
        }

        String conductorPrompt = llmConfig != null ? llmConfig.getUserPrompt() : null;
        if (StringUtils.isNotBlank(conductorPrompt)) {
            chatMessages.add(new UserChatMessageDTO("【调度指令与核验提醒】: " + conductorPrompt));
        } else if (chatMessages.isEmpty()) {
            throw BizErrorFactory.getInstance().agentUserPromptMissing("CONDUCTOR");
        }

        return chatMessages;
    }
}
