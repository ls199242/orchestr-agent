package com.shane.orchestragent.biz.agent.flow;

import com.shane.orchestragent.biz.agent.base.BaseAgent;
import com.shane.orchestragent.biz.context.AgentContext;
import com.shane.orchestragent.biz.context.StrategyContext;
import com.shane.orchestragent.biz.model.agent.AgentResult;
import com.shane.orchestragent.biz.model.flow.FlowAgentMessage;
import com.shane.orchestragent.common.enums.AgentTypeEnum;
import com.shane.orchestragent.common.exception.BizException;
import com.shane.orchestragent.common.utils.JsonUtils;
import com.shane.orchestragent.prompt.model.AgentPO;
import com.shane.orchestragent.repository.model.ToolConfigDO;
import org.apache.commons.lang3.StringUtils;

import java.util.Objects;

/**
 * 工具调用智能体 (基于 ToolConfigDO 执行外部能力接入与动作交互)
 *
 * @author Shane
 */
public class ToolAgent extends BaseAgent<AgentContext> {

    private final ToolConfigDO toolConfig;

    public ToolAgent(ToolConfigDO toolConfig) {
        super(Objects.requireNonNull(toolConfig, "toolConfig 不能为空").getName(),
                toolConfig.getDescription(),
                AgentTypeEnum.TOOL);
        if (StringUtils.isBlank(toolConfig.getName())) {
            throw new IllegalArgumentException("toolConfig.name 不能为空");
        }
        this.toolConfig = toolConfig;
    }

    @Override
    public AgentResult execute(AgentContext context) throws BizException {
        if (isStopped()) {
            return null;
        }

        Object request = context.getAgentRequest();
        if (request == null && context instanceof StrategyContext strategyContext) {
            request = strategyContext.getNextAgentRequest();
        }
        String requestJson = request != null ? JsonUtils.toJsonString(request) : "{}";

        String output = callTool(requestJson, context);

        if (context instanceof StrategyContext strategyContext) {
            FlowAgentMessage flowToolMessage = FlowAgentMessage.builder()
                    .agentType(AgentTypeEnum.TOOL)
                    .agentName(getName())
                    .output(output)
                    .timestamp(System.currentTimeMillis())
                    .build();
            strategyContext.addChatMessage(flowToolMessage);
        }

        return new AgentResult(output, null);
    }

    protected String callTool(String requestJson, AgentContext context) throws BizException {
        return "{\"status\": \"SUCCESS\", \"tool\": \"" + getName() + "\", \"data\": " + requestJson + "}";
    }

    public ToolConfigDO getToolConfig() {
        return toolConfig;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <PO extends AgentPO> PO getPO() {
        return (PO) AgentPO.builder()
                .name(getName())
                .description(getDescription())
                .agentType(AgentTypeEnum.TOOL.getCode())
                .build();
    }
}
