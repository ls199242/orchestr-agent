package com.shane.orchestragent.biz.agent.flow;

import com.shane.orchestragent.biz.agent.base.BaseLlmAgent;
import com.shane.orchestragent.biz.context.StrategyContext;
import com.shane.orchestragent.biz.model.agent.LlmConfig;
import com.shane.orchestragent.biz.service.LlmService;
import com.shane.orchestragent.common.constant.PropertyKeys;
import com.shane.orchestragent.common.enums.AgentTypeEnum;
import com.shane.orchestragent.common.exception.BizErrorFactory;
import com.shane.orchestragent.common.exception.BizException;
import com.shane.orchestragent.common.utils.JsonUtils;
import com.shane.orchestragent.integration.llm.model.ChatMessageDTO;
import com.shane.orchestragent.integration.llm.model.SystemChatMessageDTO;
import com.shane.orchestragent.integration.llm.model.UserChatMessageDTO;
import com.shane.orchestragent.prompt.model.WorkerPO;
import com.shane.orchestragent.prompt.service.PromptService;
import com.shane.orchestragent.repository.model.AgentConfigDO;
import org.apache.commons.lang3.StringUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 专精工作智能体 (基于业务规范 Spec 与伴生 RAG 检索知识驱动)
 *
 * @author Shane
 */
public class WorkerAgent extends BaseLlmAgent<StrategyContext> {

    protected final AgentConfigDO agentConfig;

    public WorkerAgent(AgentConfigDO agentConfig, LlmConfig llmConfig, LlmService llmService, PromptService promptService) {
        super(agentConfig.getName(), agentConfig.getDescription(), AgentTypeEnum.WORKER, llmConfig, llmService, promptService);
        this.agentConfig = agentConfig;
    }

    @Override
    protected SystemChatMessageDTO buildSystemPrompt(StrategyContext context) throws BizException {
        String template = llmConfig != null ? llmConfig.getSystemPrompt() : null;
        if (StringUtils.isBlank(template) && agentConfig != null) {
            template = agentConfig.getSystemPrompt();
        }
        if (StringUtils.isBlank(template)) {
            throw BizErrorFactory.getInstance().agentSystemPromptMissing(getName());
        }

        Map<String, Object> model = new HashMap<>(context.getProperties());
        model.put("worker_name", getName());
        model.put("worker_description", getDescription());
        model.put("spec", StringUtils.defaultString(template));

        Object ragResult = context.getProperty(PropertyKeys.KEY_RAG_RESULT);
        model.put("rag_result", ragResult != null ? ragResult : "无特定参考知识");

        String prompt = promptService.renderPrompt(template, model);
        return new SystemChatMessageDTO(prompt);
    }

    @Override
    protected List<ChatMessageDTO> buildMessages(StrategyContext context) throws BizException {
        Map<String, Object> request = context.getNextAgentRequest();
        String content = request != null ? JsonUtils.toJsonString(request) : "执行本次工单任务";

        String userTemplate = llmConfig != null ? llmConfig.getUserPrompt() : null;
        if (StringUtils.isBlank(userTemplate) && agentConfig != null) {
            userTemplate = agentConfig.getUserPrompt();
        }

        if (StringUtils.isNotBlank(userTemplate)) {
            Map<String, Object> model = new HashMap<>(context.getProperties());
            model.put("strategy_target", context.getStrategyTarget());
            model.put("strategyTarget", context.getStrategyTarget());
            model.put("agent_request", content);
            model.put("request", request != null ? request : Collections.emptyMap());
            content = promptService.renderPrompt(userTemplate, model);
        }

        return Collections.singletonList(new UserChatMessageDTO(content));
    }

    @SuppressWarnings("unchecked")
    @Override
    public WorkerPO getPO() {
        WorkerPO po = new WorkerPO();
        po.setName(getName());
        po.setDescription(getDescription());
        po.setAgentType(getType().getCode());
        po.setSpec(agentConfig != null ? agentConfig.getSystemPrompt() : "");
        return po;
    }

    public AgentConfigDO getAgentConfig() {
        return agentConfig;
    }
}
