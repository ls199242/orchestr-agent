package com.shane.orchestragent.biz.agent.flow;

import com.shane.orchestragent.biz.agent.Agent;
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
import com.shane.orchestragent.prompt.model.PromptTypeEnum;
import com.shane.orchestragent.prompt.service.PromptService;
import org.apache.commons.lang3.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 战略任务规划智能体 (修复原代码潜在 NPE，支持动态智能体池注入)
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

        // 注入可用的 Worker 智能体列表描述
        List<Map<String, String>> agentDescriptions = context.getAgents().values().stream()
                .filter(a -> a.getType() == AgentTypeEnum.WORKER || a.getType() == AgentTypeEnum.TOOL || a.getType() == AgentTypeEnum.RAG)
                .map(a -> {
                    Map<String, String> map = new HashMap<>();
                    map.put("name", a.getName());
                    map.put("description", a.getDescription());
                    return map;
                })
                .collect(Collectors.toList());
        properties.put("agent_list", JsonUtils.toJsonString(agentDescriptions));

        String template = getLlmConfig().getPromptTemplates() != null ?
                getLlmConfig().getPromptTemplates().get(PromptTypeEnum.PLANNER_SYSTEM_PROMPT) : null;
        if (StringUtils.isEmpty(template)) {
            template = promptService.loadClasspathTemplate("planner");
        }
        if (StringUtils.isEmpty(template)) {
            template = "# PlannerAgent\n请分析目标，输出规划步骤: {\"steps\": [{\"step\": 1, \"agent\": \"...\", \"description\": \"...\"}]}";
        }
        String prompt = promptService.renderPrompt(template, properties);
        return new SystemChatMessageDTO(prompt);
    }

    @Override
    protected List<ChatMessageDTO> buildMessages(StrategyContext context) throws BizException {
        String strategyTarget = context.getStrategyTarget();
        // 防御性检查：杜绝老项目中直接 .toString() 抛出的 NPE
        if (StringUtils.isBlank(strategyTarget)) {
            throw BizErrorFactory.getInstance().plannerTargetNotFound();
        }
        return Collections.singletonList(new UserChatMessageDTO("战略目标: " + strategyTarget));
    }
}
