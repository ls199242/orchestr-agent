package com.shane.orchestragent.biz.agent.flow;

import com.shane.orchestragent.biz.agent.base.BaseLlmAgent;
import com.shane.orchestragent.biz.context.StrategyContext;
import com.shane.orchestragent.biz.model.agent.LlmConfig;
import com.shane.orchestragent.biz.model.flow.EvaluatorResult;
import com.shane.orchestragent.biz.model.flow.FlowAgentMessage;
import com.shane.orchestragent.biz.service.LlmService;
import com.shane.orchestragent.common.enums.AgentTypeEnum;
import com.shane.orchestragent.common.exception.BizException;
import com.shane.orchestragent.common.utils.JsonUtils;
import com.shane.orchestragent.integration.llm.model.ChatMessageDTO;
import com.shane.orchestragent.integration.llm.model.SystemChatMessageDTO;
import com.shane.orchestragent.integration.llm.model.UserChatMessageDTO;
import com.shane.orchestragent.prompt.model.PromptTypeEnum;
import com.shane.orchestragent.prompt.service.PromptService;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 成果汇编汇报智能体 (提纯格式化输出，支持融入战略评估器的质量审计与降级提示)
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

        String template = getLlmConfig().getPromptTemplates() != null ?
                getLlmConfig().getPromptTemplates().get(PromptTypeEnum.REPORTER_SYSTEM_PROMPT) : null;
        if (StringUtils.isEmpty(template)) {
            template = promptService.loadClasspathTemplate("reporter");
        }
        if (StringUtils.isEmpty(template)) {
            template = "# ReporterAgent\n你负责将所有执行结果整合成条理分明、客观详实的最终汇报。";
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

        return Collections.singletonList(new UserChatMessageDTO(sb.toString()));
    }
}
