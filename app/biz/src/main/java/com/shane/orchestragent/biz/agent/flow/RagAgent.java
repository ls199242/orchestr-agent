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
import org.apache.commons.collections4.CollectionUtils;

import java.util.Collections;
import java.util.List;

/**
 * 知识库检索增强智能体 (作为专精 Worker 的伴生节点，隐式伴随触发并注入知识)
 *
 * @author Shane
 */
public class RagAgent extends BaseAgent<StrategyContext> {

    private final List<String> datasets;

    public RagAgent(String name, List<String> datasets) {
        this(name, "知识库检索增强智能体: " + name, datasets);
    }

    public RagAgent(String name, String description, List<String> datasets) {
        super(name, description, AgentTypeEnum.RAG);
        this.datasets = datasets != null ? datasets : Collections.emptyList();
    }

    /**
     * 生成伴生 RAG 节点名称
     *
     * @param workerName 绑定的 Worker 名称
     * @return 伴生名称，如 "TicketWorker#RAG"
     */
    public static String buildRagName(String workerName) {
        return workerName + "#RAG";
    }

    @Override
    protected AgentResult doExecute(StrategyContext context) throws BizException {
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
        String dsInfo = CollectionUtils.isNotEmpty(datasets) ? String.join(",", datasets) : "无指定知识库";
        return "【知识库匹配内容(库: " + dsInfo + ")】针对用户诉求 [" + query + "] 的业务规范与参考信息已成功检索。";
    }

    public List<String> getDatasets() {
        return datasets;
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
