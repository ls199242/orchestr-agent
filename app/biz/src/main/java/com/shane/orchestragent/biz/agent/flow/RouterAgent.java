package com.shane.orchestragent.biz.agent.flow;

import com.shane.orchestragent.biz.agent.base.BaseLlmAgent;
import com.shane.orchestragent.biz.context.StrategyContext;
import com.shane.orchestragent.biz.model.agent.LlmConfig;
import com.shane.orchestragent.biz.service.LlmService;
import com.shane.orchestragent.common.constant.PropertyKeys;
import com.shane.orchestragent.common.enums.AgentTypeEnum;
import com.shane.orchestragent.common.exception.BizException;
import com.shane.orchestragent.integration.llm.model.ChatMessageDTO;
import com.shane.orchestragent.integration.llm.model.SystemChatMessageDTO;
import com.shane.orchestragent.integration.llm.model.UserChatMessageDTO;
import com.shane.orchestragent.prompt.model.PromptTypeEnum;
import com.shane.orchestragent.prompt.service.PromptService;
import org.apache.commons.lang3.StringUtils;

import java.util.Collections;
import java.util.List;

/**
 * 路由门禁智能体 (原 CoordinatorAgent 改名，聚焦意图识别与复杂任务分流)
 *
 * @author Shane
 */
public class RouterAgent extends BaseLlmAgent<StrategyContext> {

    public RouterAgent(LlmConfig llmConfig, LlmService llmService, PromptService promptService) {
        super(AgentTypeEnum.ROUTER, llmConfig, llmService, promptService);
    }

    @Override
    protected SystemChatMessageDTO buildSystemPrompt(StrategyContext context) throws BizException {
        String template = getLlmConfig().getPromptTemplates() != null ?
                getLlmConfig().getPromptTemplates().get(PromptTypeEnum.ROUTER_SYSTEM_PROMPT) : null;
        if (StringUtils.isEmpty(template)) {
            template = promptService.loadClasspathTemplate("router");
        }
        if (StringUtils.isEmpty(template)) {
            template = "# RouterAgent\n请分析用户意图，判断是否需移交 Planner 规划器。输出 JSON: {\"handoffToPlanner\": true, \"reply\": \"...\"}";
        }
        String prompt = promptService.renderPrompt(template, context.getProperties());
        return new SystemChatMessageDTO(prompt);
    }

    @Override
    protected List<ChatMessageDTO> buildMessages(StrategyContext context) throws BizException {
        String template = getLlmConfig().getPromptTemplates() != null ?
                getLlmConfig().getPromptTemplates().get(PromptTypeEnum.ROUTER_USER_PROMPT) : null;
        String prompt;
        if (StringUtils.isNotEmpty(template)) {
            prompt = promptService.renderPrompt(template, context.getProperties());
        } else {
            Object history = context.getProperty(PropertyKeys.KEY_CHAT_HISTORY);
            Object target = context.getProperty(PropertyKeys.KEY_STRATEGY_TARGET);
            prompt = "用户当前诉求: " + (target != null ? target : "") + "\n历史记录: " + (history != null ? history : "无");
        }
        return Collections.singletonList(new UserChatMessageDTO(prompt));
    }
}
