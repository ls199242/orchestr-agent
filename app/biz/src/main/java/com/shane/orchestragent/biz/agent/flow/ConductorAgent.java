package com.shane.orchestragent.biz.agent.flow;

import com.shane.orchestragent.biz.agent.base.BaseLlmAgent;
import com.shane.orchestragent.biz.context.StrategyContext;
import com.shane.orchestragent.biz.model.agent.LlmConfig;
import com.shane.orchestragent.biz.model.flow.FlowAgentMessage;
import com.shane.orchestragent.biz.service.LlmService;
import com.shane.orchestragent.common.enums.AgentTypeEnum;
import com.shane.orchestragent.common.exception.BizException;
import com.shane.orchestragent.common.utils.JsonUtils;
import com.shane.orchestragent.integration.llm.model.AssistantChatMessageDTO;
import com.shane.orchestragent.integration.llm.model.ChatMessageDTO;
import com.shane.orchestragent.integration.llm.model.SystemChatMessageDTO;
import com.shane.orchestragent.integration.llm.model.UserChatMessageDTO;
import com.shane.orchestragent.prompt.PromptPropertyConstant;
import com.shane.orchestragent.prompt.model.PromptTypeEnum;
import com.shane.orchestragent.prompt.service.PromptService;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 步进调度指挥智能体 (基于多轮对话拓扑精准驱动状态机与自愈响应)
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

        if (context.getPlanResult() != null) {
            properties.put("plan_steps", JsonUtils.toJsonString(context.getPlanResult().getSteps()));
        } else {
            properties.put("plan_steps", "[]");
        }

        properties.put("history_messages", JsonUtils.toJsonString(context.getChatMessages()));

        // 注入来自 Evaluator 的打回批评反馈 (如有)
        String feedback = context.getEvaluationFeedback();
        properties.put("evaluator_feedback", StringUtils.isNotEmpty(feedback) ? feedback : "无 (前序执行正常)");

        String template = getLlmConfig().getPromptTemplates() != null ?
                getLlmConfig().getPromptTemplates().get(PromptTypeEnum.CONDUCTOR_SYSTEM_PROMPT) : null;
        if (StringUtils.isEmpty(template)) {
            template = promptService.loadClasspathTemplate("conductor");
        }
        if (StringUtils.isEmpty(template)) {
            template = "# ConductorAgent\n负责根据当前进度调度下一个 Worker 或输出 FINISH。输出 JSON: {\"next\": \"...\", \"request\": {}}";
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
                    String planPrompt = renderConductorPlannerUserPrompt(chatMessage.getOutput(), context.getProperties());
                    chatMessages.add(new UserChatMessageDTO(planPrompt));
                }
                case TOOL -> {
                    String toolPrompt = renderConductorToolUserPrompt(chatMessage.getAgentName(), chatMessage.getOutput(), context.getProperties());
                    chatMessages.add(new UserChatMessageDTO(toolPrompt));
                }
                case CONDUCTOR -> {
                    chatMessages.add(new AssistantChatMessageDTO(chatMessage.getOutput()));
                }
                case WORKER -> {
                    String workerPrompt = renderConductorWorkerUserPrompt(chatMessage.getAgentName(), chatMessage.getOutput(), context.getProperties());
                    chatMessages.add(new UserChatMessageDTO(workerPrompt));
                }
                case EVALUATOR -> {
                    String evalPrompt = renderConductorEvaluatorUserPrompt(chatMessage.getOutput(), context.getProperties());
                    chatMessages.add(new UserChatMessageDTO(evalPrompt));
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

        if (chatMessages.isEmpty()) {
            chatMessages.add(new UserChatMessageDTO("请依据计划调度下一步。若全部步骤已完成，请输出 FINISH。"));
        }

        return chatMessages;
    }

    public String renderConductorPlannerUserPrompt(String planResult, Map<String, Object> properties) throws BizException {
        Map<String, Object> dataModel = new HashMap<>();
        dataModel.put(PromptPropertyConstant.PLANNER.KEY_PLAN_RESULT, planResult);
        if (properties != null) {
            dataModel.putAll(properties);
        }
        String template = getPromptTemplate(PromptTypeEnum.CONDUCTOR_PLANNER_USER_PROMPT, "conductor_planner_user");
        return promptService.renderPrompt(template, dataModel);
    }

    public String renderConductorToolUserPrompt(String toolName, String toolResponse, Map<String, Object> properties) throws BizException {
        Map<String, Object> dataModel = new HashMap<>();
        dataModel.put(PromptPropertyConstant.TOOL.KEY_NAME, toolName);
        dataModel.put(PromptPropertyConstant.TOOL.KEY_RESPONSE, toolResponse);
        if (properties != null) {
            dataModel.putAll(properties);
        }
        String template = getPromptTemplate(PromptTypeEnum.CONDUCTOR_TOOL_USER_PROMPT, "conductor_tool_user");
        return promptService.renderPrompt(template, dataModel);
    }

    public String renderConductorWorkerUserPrompt(String workerName, String workerResponse, Map<String, Object> properties) throws BizException {
        Map<String, Object> dataModel = new HashMap<>();
        dataModel.put(PromptPropertyConstant.WORKER.KEY_NAME, workerName);
        dataModel.put(PromptPropertyConstant.WORKER.KEY_RESPONSE, workerResponse);
        if (properties != null) {
            dataModel.putAll(properties);
        }
        String template = getPromptTemplate(PromptTypeEnum.CONDUCTOR_WORKER_USER_PROMPT, "conductor_worker_user");
        return promptService.renderPrompt(template, dataModel);
    }

    public String renderConductorEvaluatorUserPrompt(String critique, Map<String, Object> properties) throws BizException {
        Map<String, Object> dataModel = new HashMap<>();
        dataModel.put(PromptPropertyConstant.EVALUATOR.KEY_CRITIQUE, critique);
        dataModel.put(PromptPropertyConstant.EVALUATOR.KEY_SUGGESTED_REMEDY, "");
        if (properties != null) {
            dataModel.putAll(properties);
        }
        String template = getPromptTemplate(PromptTypeEnum.CONDUCTOR_EVALUATOR_USER_PROMPT, "conductor_evaluator_user");
        return promptService.renderPrompt(template, dataModel);
    }

    private String getPromptTemplate(PromptTypeEnum type, String fallbackClasspath) {
        String template = getLlmConfig().getPromptTemplates() != null ?
                getLlmConfig().getPromptTemplates().get(type) : null;
        if (StringUtils.isEmpty(template)) {
            template = promptService.loadClasspathTemplate(fallbackClasspath);
        }
        return StringUtils.isNotEmpty(template) ? template : "";
    }
}
