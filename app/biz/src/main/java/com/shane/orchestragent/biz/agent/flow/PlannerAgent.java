package com.shane.orchestragent.biz.agent.flow;

import com.shane.orchestragent.biz.agent.base.BaseLlmAgent;
import com.shane.orchestragent.biz.context.StrategyContext;
import com.shane.orchestragent.biz.model.agent.LlmConfig;
import com.shane.orchestragent.biz.service.LlmService;
import com.shane.orchestragent.common.enums.AgentTypeEnum;
import com.shane.orchestragent.common.exception.BizErrorFactory;
import com.shane.orchestragent.common.exception.BizException;
import com.shane.orchestragent.common.utils.JsonUtils;
import com.shane.orchestragent.integration.llm.model.ChatMessageDTO;
import com.shane.orchestragent.integration.llm.model.SystemChatMessageDTO;
import com.shane.orchestragent.integration.llm.model.UserChatMessageDTO;
import com.shane.orchestragent.prompt.service.PromptService;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 战略任务规划智能体 (严格基于配置中心渲染，Fail-Fast 治理)
 *
 * @author Shane
 */
public class PlannerAgent extends BaseLlmAgent<StrategyContext> {

    public PlannerAgent(LlmConfig llmConfig, LlmService llmService, PromptService promptService) {
        super(AgentTypeEnum.PLANNER, llmConfig, llmService, promptService);
    }

    @Override
    protected SystemChatMessageDTO buildSystemPrompt(StrategyContext context) throws BizException {
        Map<String, Object> properties = new HashMap<>(context.getProperties());

        // 注入可用的业务专精 Expert 与外部 Tool 列表描述 (包含 Tool 的 Schema，供 DAG 规划精准入参绑定)
        List<Map<String, Object>> agentDescriptions = context.getAgents().values().stream()
                .filter(a -> a.getType() == AgentTypeEnum.EXPERT || a.getType() == AgentTypeEnum.TOOL)
                .map(a -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("name", a.getName());
                    map.put("description", a.getDescription());
                    map.put("type", a.getType().name());
                    if (a instanceof ToolAgent toolAgent && toolAgent.getToolConfig() != null) {
                        map.put("requestSchema", toolAgent.getToolConfig().getRequestJsonSchema());
                        map.put("responseSchema", toolAgent.getToolConfig().getResponseJsonSchema());
                    }
                    return map;
                })
                .collect(Collectors.toList());
        properties.put("agent_list", JsonUtils.toJsonString(agentDescriptions));
        properties.put("strategy_target", context.getStrategyTarget());
        if (context.getConfig() != null && CollectionUtils.isNotEmpty(context.getConfig().getParams())) {
            properties.put("available_global_params", JsonUtils.toJsonString(context.getConfig().getParams()));
        }

        String template = llmConfig != null ? llmConfig.getSystemPrompt() : null;
        if (StringUtils.isBlank(template)) {
            throw BizErrorFactory.getInstance().agentSystemPromptMissing("PLANNER");
        }
        String prompt = promptService.renderPrompt(template, properties);
        return new SystemChatMessageDTO(prompt);
    }

    @Override
    protected List<ChatMessageDTO> buildMessages(StrategyContext context) throws BizException {
        String strategyTarget = context.getStrategyTarget();
        if (StringUtils.isBlank(strategyTarget)) {
            throw BizErrorFactory.getInstance().plannerTargetNotFound();
        }

        Map<String, Object> properties = new HashMap<>(context.getProperties());
        properties.put("strategy_target", strategyTarget);
        properties.put("strategyTarget", strategyTarget);

        String userTemplate = llmConfig != null ? llmConfig.getUserPrompt() : null;
        if (StringUtils.isBlank(userTemplate)) {
            throw BizErrorFactory.getInstance().agentUserPromptMissing("PLANNER");
        }

        String userContent = promptService.renderPrompt(userTemplate, properties);
        return Collections.singletonList(new UserChatMessageDTO(userContent));
    }
}
