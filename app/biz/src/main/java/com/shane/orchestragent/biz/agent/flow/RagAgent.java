package com.shane.orchestragent.biz.agent.flow;

import com.shane.orchestragent.biz.agent.base.BaseAgent;
import com.shane.orchestragent.biz.context.StrategyContext;
import com.shane.orchestragent.biz.model.agent.AgentResult;
import com.shane.orchestragent.biz.model.agent.RagAgentResult;
import com.shane.orchestragent.biz.model.flow.FlowAgentMessage;
import com.shane.orchestragent.common.constant.PropertyKeys;
import com.shane.orchestragent.common.enums.AgentTypeEnum;
import com.shane.orchestragent.common.exception.BizException;
import com.shane.orchestragent.prompt.model.AgentPO;

import java.util.Collections;

/**
 * 知识库检索增强智能体 (在 Worker 运行前注入 RAG 检索上下文)
 *
 * @author Shane
 */
public class RagAgent extends BaseAgent<StrategyContext> {

    public RagAgent() {
        super("rag_agent", "知识库检索增强智能体", AgentTypeEnum.RAG);
    }

    public RagAgent(String name, String description) {
        super(name, description, AgentTypeEnum.RAG);
    }

    @Override
    public AgentResult execute(StrategyContext context) throws BizException {
        if (isStopped()) {
            return null;
        }

        String target = context.getStrategyTarget();
        String ragResult = retrieveKnowledge(target, context);

        context.setProperties(PropertyKeys.KEY_RAG_RESULT, ragResult);

        FlowAgentMessage message = FlowAgentMessage.builder()
                .agentType(AgentTypeEnum.RAG)
                .agentName(getName())
                .output(ragResult)
                .timestamp(System.currentTimeMillis())
                .build();
        context.addChatMessage(message);

        return new RagAgentResult(ragResult, null, Collections.emptyMap());
    }

    protected String retrieveKnowledge(String query, StrategyContext context) {
        return "【知识库匹配内容】针对用户诉求 [" + query + "] 的业务规范与参考信息已成功检索。";
    }

    @SuppressWarnings("unchecked")
    @Override
    public <PO extends AgentPO> PO getPO() {
        return (PO) AgentPO.builder()
                .name(getName())
                .description(getDescription())
                .agentType(AgentTypeEnum.RAG.getCode())
                .build();
    }
}
