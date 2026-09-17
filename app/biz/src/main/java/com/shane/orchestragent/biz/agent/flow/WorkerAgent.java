package com.shane.orchestragent.biz.agent.flow;

import com.shane.orchestragent.biz.agent.base.BaseLlmAgent;
import com.shane.orchestragent.biz.context.StrategyContext;
import com.shane.orchestragent.biz.model.agent.LlmConfig;
import com.shane.orchestragent.biz.service.LlmService;
import com.shane.orchestragent.common.enums.AgentTypeEnum;
import com.shane.orchestragent.common.exception.BizException;
import com.shane.orchestragent.common.utils.JsonUtils;
import com.shane.orchestragent.integration.llm.model.ChatMessageDTO;
import com.shane.orchestragent.integration.llm.model.SystemChatMessageDTO;
import com.shane.orchestragent.integration.llm.model.UserChatMessageDTO;
import com.shane.orchestragent.prompt.model.PromptTypeEnum;
import com.shane.orchestragent.prompt.model.WorkerPO;
import com.shane.orchestragent.prompt.service.PromptService;
import com.shane.orchestragent.repository.model.StrategyWorkerDO;
import org.apache.commons.lang3.StringUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 专精工作智能体 (原 ExpertAgent 改名，专注任务执行与工具集成)
 *
 * @author Shane
 */
public class WorkerAgent extends BaseLlmAgent<StrategyContext> {

    protected final StrategyWorkerDO workerConfig;

    public WorkerAgent(StrategyWorkerDO workerConfig, LlmConfig llmConfig, LlmService llmService, PromptService promptService) {
        super(workerConfig.getName(), workerConfig.getDescription(), AgentTypeEnum.WORKER, llmConfig, llmService, promptService);
        this.workerConfig = workerConfig;
    }

    @Override
    protected SystemChatMessageDTO buildSystemPrompt(StrategyContext context) throws BizException {
        Map<String, Object> model = new HashMap<>(context.getProperties());
        model.put("spec", StringUtils.defaultString(workerConfig.getPrompt()));

        String template = getLlmConfig().getPromptTemplates() != null ?
                getLlmConfig().getPromptTemplates().get(PromptTypeEnum.WORKER_SYSTEM_PROMPT) : null;
        if (StringUtils.isEmpty(template)) {
            template = promptService.loadClasspathTemplate("worker");
        }
        if (StringUtils.isEmpty(template)) {
            template = "# WorkerAgent: " + getName() + "\n规范说明: ${spec}\n请严格依据输入参数执行任务。";
        }
        String prompt = promptService.renderPrompt(template, model);
        return new SystemChatMessageDTO(prompt);
    }

    @Override
    protected List<ChatMessageDTO> buildMessages(StrategyContext context) throws BizException {
        Map<String, Object> request = context.getNextAgentRequest();
        String content = request != null ? JsonUtils.toJsonString(request) : "执行本次工单任务";
        return Collections.singletonList(new UserChatMessageDTO(content));
    }

    @SuppressWarnings("unchecked")
    @Override
    public WorkerPO getPO() {
        WorkerPO po = new WorkerPO();
        po.setName(getName());
        po.setDescription(getDescription());
        po.setAgentType(getType().getCode());
        po.setSpec(workerConfig.getPrompt());
        return po;
    }
}
