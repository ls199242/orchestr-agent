package com.shane.orchestragent.biz.agent.flow;

import com.shane.orchestragent.biz.agent.base.BaseLlmAgent;
import com.shane.orchestragent.biz.context.StrategyContext;
import com.shane.orchestragent.biz.model.agent.AgentResult;
import com.shane.orchestragent.biz.model.agent.LlmConfig;
import com.shane.orchestragent.biz.model.flow.EvaluatorResult;
import com.shane.orchestragent.biz.service.LlmService;
import com.shane.orchestragent.common.enums.AgentTypeEnum;
import com.shane.orchestragent.common.exception.BizException;
import com.shane.orchestragent.common.utils.JsonUtils;
import com.shane.orchestragent.integration.llm.model.ChatMessageDTO;
import com.shane.orchestragent.integration.llm.model.SystemChatMessageDTO;
import com.shane.orchestragent.integration.llm.model.UserChatMessageDTO;
import com.shane.orchestragent.prompt.model.PromptTypeEnum;
import com.shane.orchestragent.prompt.service.PromptService;
import org.apache.commons.lang3.StringUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 战略目标审查与自愈评估智能体 (全新引入: 负责宏观战略对齐、事实性抗幻觉审查与自愈批判)
 *
 * @author Shane
 */
public class EvaluatorAgent extends BaseLlmAgent<StrategyContext> {

    public EvaluatorAgent(LlmConfig llmConfig, LlmService llmService, PromptService promptService) {
        super(AgentTypeEnum.EVALUATOR, llmConfig, llmService, promptService);
    }

    @Override
    protected SystemChatMessageDTO buildSystemPrompt(StrategyContext context) throws BizException {
        Map<String, Object> properties = new HashMap<>(context.getProperties());
        properties.put("strategy_target", context.getStrategyTarget());
        properties.put("plan_steps", context.getPlanResult() != null ? JsonUtils.toJsonString(context.getPlanResult().getSteps()) : "[]");
        properties.put("execution_trace", JsonUtils.toJsonString(context.getChatMessages()));

        String template = getLlmConfig().getPromptTemplates() != null ?
                getLlmConfig().getPromptTemplates().get(PromptTypeEnum.EVALUATOR_SYSTEM_PROMPT) : null;
        if (StringUtils.isEmpty(template)) {
            template = promptService.loadClasspathTemplate("evaluator");
        }
        if (StringUtils.isEmpty(template)) {
            template = "# EvaluatorAgent\n请对比原始战略目标与实际执行产物，严格审计完整度与事实性。\n输出 JSON: {\"pass\": true/false, \"score\": 0-100, \"critique\": \"...\", \"suggestedRemedy\": \"...\"}";
        }
        String prompt = promptService.renderPrompt(template, properties);
        return new SystemChatMessageDTO(prompt);
    }

    @Override
    protected List<ChatMessageDTO> buildMessages(StrategyContext context) throws BizException {
        String query = "请对当前执行结果进行终局战略目标验收。对比目标: " + context.getStrategyTarget();
        return Collections.singletonList(new UserChatMessageDTO(query));
    }

    /**
     * 执行战略核验，并解析结构化 EvaluatorResult
     */
    public EvaluatorResult evaluate(StrategyContext context) throws BizException {
        AgentResult result = execute(context);
        if (result == null || StringUtils.isBlank(result.getOutput())) {
            return EvaluatorResult.defaultPass();
        }

        EvaluatorResult evaluatorResult = JsonUtils.parseObject(result.getOutput(), EvaluatorResult.class);
        if (evaluatorResult == null) {
            evaluatorResult = EvaluatorResult.defaultPass();
        }
        context.setLastEvaluatorResult(evaluatorResult);
        return evaluatorResult;
    }
}
