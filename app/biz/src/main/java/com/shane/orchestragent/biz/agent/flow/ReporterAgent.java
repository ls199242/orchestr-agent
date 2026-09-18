package com.shane.orchestragent.biz.agent.flow;

import com.shane.orchestragent.biz.agent.base.BaseLlmAgent;
import com.shane.orchestragent.biz.context.StrategyContext;
import com.shane.orchestragent.biz.model.agent.LlmConfig;
import com.shane.orchestragent.biz.model.flow.EvaluatorResult;
import com.shane.orchestragent.biz.model.flow.FlowAgentMessage;
import com.shane.orchestragent.biz.service.LlmService;
import com.shane.orchestragent.common.enums.AgentTypeEnum;
import com.shane.orchestragent.common.exception.BizErrorFactory;
import com.shane.orchestragent.common.exception.BizException;
import com.shane.orchestragent.integration.llm.model.ChatMessageDTO;
import com.shane.orchestragent.integration.llm.model.SystemChatMessageDTO;
import com.shane.orchestragent.integration.llm.model.UserChatMessageDTO;
import com.shane.orchestragent.prompt.service.PromptService;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 成果汇编汇报智能体 (严格基于配置中心渲染，提纯格式化输出并融合质检审计)
 *
 * @author Shane
 */
public class ReporterAgent extends BaseLlmAgent<StrategyContext> {

    public ReporterAgent(LlmConfig llmConfig, LlmService llmService, PromptService promptService) {
        super(AgentTypeEnum.REPORTER, llmConfig, llmService, promptService);
    }

    @Override
    protected SystemChatMessageDTO buildSystemPrompt(StrategyContext context) throws BizException {
        Map<String, Object> properties = new HashMap<>(context.getProperties());
        properties.put("strategy_target", context.getStrategyTarget());

        String template = llmConfig != null ? llmConfig.getSystemPrompt() : null;
        if (StringUtils.isBlank(template)) {
            throw BizErrorFactory.getInstance().agentSystemPromptMissing("REPORTER");
        }
        String prompt = promptService.renderPrompt(template, properties);
        return new SystemChatMessageDTO(prompt);
    }

    @Override
    protected List<ChatMessageDTO> buildMessages(StrategyContext context) throws BizException {
        // 安全过滤 Worker 与 Tool 产出，杜绝老代码 NPE
        List<FlowAgentMessage> messages = context.getChatMessages().stream()
                .filter(m -> m != null && (m.getAgentType() == AgentTypeEnum.WORKER || m.getAgentType() == AgentTypeEnum.TOOL))
                .collect(Collectors.toList());

        StringBuilder sb = new StringBuilder();
        sb.append("【原始需求】: ").append(context.getStrategyTarget()).append("\n\n");
        sb.append("【执行数据】:\n");
        if (CollectionUtils.isNotEmpty(messages)) {
            for (FlowAgentMessage msg : messages) {
                sb.append("- [").append(msg.getAgentName()).append("]: ").append(msg.getOutput()).append("\n");
            }
        } else {
            sb.append("无具体执行流水记录\n");
        }

        // 融入 Evaluator 验收结论与风险说明 (如有)
        EvaluatorResult evalResult = context.getLastEvaluatorResult();
        if (evalResult != null && !evalResult.isPass()) {
            sb.append("\n【质量风险/降级说明】:\n");
            sb.append("评估器指出未完全满足的项: ").append(evalResult.getCritique()).append("\n");
            sb.append("请在回复中向用户进行诚实、客观的补充说明，提升系统透明度与可信度。");
        }

        Map<String, Object> properties = new HashMap<>(context.getProperties());
        properties.put("strategy_target", context.getStrategyTarget());
        properties.put("strategyTarget", context.getStrategyTarget());
        properties.put("flowExecutionSummary", sb.toString());

        String userTemplate = llmConfig != null ? llmConfig.getUserPrompt() : null;
        if (StringUtils.isBlank(userTemplate)) {
            throw BizErrorFactory.getInstance().agentUserPromptMissing("REPORTER");
        }

        String userContent = promptService.renderPrompt(userTemplate, properties);
        return Collections.singletonList(new UserChatMessageDTO(userContent));
    }
}
