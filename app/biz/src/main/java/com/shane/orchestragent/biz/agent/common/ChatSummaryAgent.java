package com.shane.orchestragent.biz.agent.common;

import com.shane.orchestragent.biz.agent.base.BaseLlmAgent;
import com.shane.orchestragent.biz.context.AgentContext;
import com.shane.orchestragent.biz.model.agent.LlmConfig;
import com.shane.orchestragent.biz.service.LlmService;
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
 * 会话历史摘要智能体
 *
 * @author Shane
 */
public class ChatSummaryAgent extends BaseLlmAgent<AgentContext> {

    public ChatSummaryAgent(LlmConfig llmConfig, LlmService llmService, PromptService promptService) {
        super(AgentTypeEnum.SUMMARY, llmConfig, llmService, promptService);
    }

    @Override
    protected SystemChatMessageDTO buildSystemPrompt(AgentContext context) throws BizException {
        String template = getLlmConfig().getPromptTemplates() != null ?
                getLlmConfig().getPromptTemplates().get(PromptTypeEnum.CHAT_SUMMARY_SYSTEM_PROMPT) : null;
        if (StringUtils.isEmpty(template)) {
            template = "# ChatSummaryAgent\n请对提供的多轮会话记录进行关键信息提炼与无损摘要。";
        }
        String prompt = promptService.renderPrompt(template, context.getProperties());
        return new SystemChatMessageDTO(prompt);
    }

    @Override
    protected List<ChatMessageDTO> buildMessages(AgentContext context) throws BizException {
        String template = getLlmConfig().getPromptTemplates() != null ?
                getLlmConfig().getPromptTemplates().get(PromptTypeEnum.CHAT_SUMMARY_USER_PROMPT) : null;
        String prompt;
        if (StringUtils.isNotEmpty(template)) {
            prompt = promptService.renderPrompt(template, context.getProperties());
        } else {
            prompt = "请对当前上下文所有会话与产出生成浓缩摘要。";
        }
        return Collections.singletonList(new UserChatMessageDTO(prompt));
    }
}
