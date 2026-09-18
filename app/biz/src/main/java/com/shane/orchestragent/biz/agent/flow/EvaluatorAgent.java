package com.shane.orchestragent.biz.agent.flow;

import com.shane.orchestragent.biz.agent.base.BaseLlmAgent;
import com.shane.orchestragent.biz.context.StrategyContext;
import com.shane.orchestragent.biz.model.agent.AgentResult;
import com.shane.orchestragent.biz.model.agent.LlmConfig;
import com.shane.orchestragent.biz.model.flow.EvaluatorResult;
import com.shane.orchestragent.biz.service.LlmService;
import com.shane.orchestragent.common.enums.AgentTypeEnum;
import com.shane.orchestragent.common.exception.BizErrorFactory;
import com.shane.orchestragent.common.exception.BizException;
import com.shane.orchestragent.common.utils.JsonUtils;
import com.shane.orchestragent.integration.llm.model.ChatMessageDTO;
import com.shane.orchestragent.integration.llm.model.SystemChatMessageDTO;
import com.shane.orchestragent.integration.llm.model.UserChatMessageDTO;
import com.shane.orchestragent.prompt.service.PromptService;
import org.apache.commons.lang3.StringUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 战略目标审查与自愈评估智能体 (严格基于配置中心渲染，负责宏观战略对齐与自愈批判)
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

        String template = llmConfig != null ? llmConfig.getSystemPrompt() : null;
        if (StringUtils.isBlank(template)) {
            throw BizErrorFactory.getInstance().agentSystemPromptMissing("EVALUATOR");
        }
        String prompt = promptService.renderPrompt(template, properties);
        return new SystemChatMessageDTO(prompt);
    }

    @Override
    protected List<ChatMessageDTO> buildMessages(StrategyContext context) throws BizException {
        Map<String, Object> properties = new HashMap<>(context.getProperties());
        properties.put("strategy_target", context.getStrategyTarget());
        properties.put("strategyTarget", context.getStrategyTarget());
        properties.put("allAgentOutputs", JsonUtils.toJsonString(context.getChatMessages()));

        String userTemplate = llmConfig != null ? llmConfig.getUserPrompt() : null;
        if (StringUtils.isBlank(userTemplate)) {
            throw BizErrorFactory.getInstance().agentUserPromptMissing("EVALUATOR");
        }

        String userContent = promptService.renderPrompt(userTemplate, properties);
        return Collections.singletonList(new UserChatMessageDTO(userContent));
    }

    /**
     * 执行战略核验，并解析结构化 EvaluatorResult
     */
    public EvaluatorResult evaluate(StrategyContext context) throws BizException {
        AgentResult result = execute(context);
        if (result == null || StringUtils.isBlank(result.getOutput())) {
            throw new BizException("EVALUATOR_RESULT_EMPTY", "战略目标审查智能体返回内容为空");
        }

        EvaluatorResult evaluatorResult = JsonUtils.parseObject(result.getOutput(), EvaluatorResult.class);
        if (evaluatorResult == null) {
            throw new BizException("EVALUATOR_PARSE_ERROR", "战略目标审查智能体返回内容无法解析为有效验收结果: " + result.getOutput());
        }
        context.setLastEvaluatorResult(evaluatorResult);
        return evaluatorResult;
    }
}
