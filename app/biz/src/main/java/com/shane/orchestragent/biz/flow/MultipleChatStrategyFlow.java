package com.shane.orchestragent.biz.flow;

import com.shane.orchestragent.biz.agent.Agent;
import com.shane.orchestragent.biz.context.StrategyContext;
import com.shane.orchestragent.common.constant.PropertyKeys;
import com.shane.orchestragent.common.enums.AgentTypeEnum;
import com.shane.orchestragent.common.exception.BizException;
import com.shane.orchestragent.memory.model.ConversationCacheUnit;
import com.shane.orchestragent.memory.model.MemoryQueryCriteria;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Map;

/**
 * 多轮会话策略流 (多轮场景下的目标策略执行拓扑)
 * <p>
 * 意图识别与参数收集已前置至 {@link SessionOrchestrationFlow}（会话编排层），
 * 本流程仅负责注入会话历史后执行标准 ReAct 协同循环 (Planner -> Supervisor 循环 -> Evaluator -> Reporter)。
 *
 * @author Shane
 */
public class MultipleChatStrategyFlow extends ReActStrategyFlow {

    public MultipleChatStrategyFlow(StrategyContext context, Map<AgentTypeEnum, Agent> systemAgents, int maxStep) {
        super(context, systemAgents, maxStep);
    }

    @Override
    protected String doExecute() throws BizException {
        // 1. 读取并注入历史会话上下文
        loadChatHistory();

        // 2. 标准 ReAct 自愈协同循环
        return super.doExecute();
    }

    private void loadChatHistory() {
        String sessionId = getContext().getSessionId();
        if (StringUtils.isNotEmpty(sessionId) && getContext().getMemoryContext() != null) {
            try {
                MemoryQueryCriteria criteria = MemoryQueryCriteria.create()
                        .addCriteria(MemoryQueryCriteria.KEY_SESSION_ID, sessionId)
                        .limit(20);
                List<ConversationCacheUnit> cacheUnits = getContext().getMemoryContext().getCache(criteria);
                if (CollectionUtils.isNotEmpty(cacheUnits)) {
                    getContext().setProperties(PropertyKeys.KEY_CHAT_HISTORY, cacheUnits);
                }
            } catch (Exception e) {
                log.error("[Flow: {}] 加载会话历史异常", getFlowId(), e);
            }
        }
    }
}
