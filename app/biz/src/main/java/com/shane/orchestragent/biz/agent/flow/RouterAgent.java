package com.shane.orchestragent.biz.agent.flow;

import com.shane.orchestragent.biz.agent.base.BaseLlmAgent;
import com.shane.orchestragent.biz.context.StrategyContext;
import com.shane.orchestragent.biz.model.agent.LlmConfig;
import com.shane.orchestragent.biz.service.LlmService;
import com.shane.orchestragent.common.enums.AgentTypeEnum;
import com.shane.orchestragent.common.exception.BizErrorFactory;
import com.shane.orchestragent.common.exception.BizException;
import com.shane.orchestragent.integration.llm.model.ChatMessageDTO;
import com.shane.orchestragent.integration.llm.model.SystemChatMessageDTO;
import com.shane.orchestragent.integration.llm.model.UserChatMessageDTO;
import com.shane.orchestragent.prompt.service.PromptService;
import org.apache.commons.lang3.StringUtils;

import java.util.Collections;
import java.util.List;

/**
 * 路由门禁智能体 (严格基于配置中心渲染，聚焦意图识别与复杂任务分流)
 *
 * @author Shane
 */
public class RouterAgent extends BaseLlmAgent<StrategyContext> {

    public RouterAgent(LlmConfig llmConfig, LlmService llmService, PromptService promptService) {
        super(AgentTypeEnum.ROUTER, llmConfig, llmService, promptService);
    }

    @Override
    protected SystemChatMessageDTO buildSystemPrompt(StrategyContext context) throws BizException {
        String template = llmConfig != null ? llmConfig.getSystemPrompt() : null;
        if (StringUtils.isBlank(template)) {
            throw BizErrorFactory.getInstance().agentSystemPromptMissing("ROUTER");
        }
        String prompt = promptService.renderPrompt(template, context.getProperties());
        return new SystemChatMessageDTO(prompt);
    }

    @Override
    protected List<ChatMessageDTO> buildMessages(StrategyContext context) throws BizException {
        String template = llmConfig != null ? llmConfig.getUserPrompt() : null;
        if (StringUtils.isBlank(template)) {
            throw BizErrorFactory.getInstance().agentUserPromptMissing("ROUTER");
        }
        String prompt = promptService.renderPrompt(template, context.getProperties());
        return Collections.singletonList(new UserChatMessageDTO(prompt));
    }
}
