package com.shane.orchestragent.biz.flow;

import com.shane.orchestragent.biz.agent.Agent;
import com.shane.orchestragent.biz.agent.flow.RouterAgent;
import com.shane.orchestragent.biz.context.StrategyContext;
import com.shane.orchestragent.common.enums.AgentTypeEnum;
import com.shane.orchestragent.biz.model.flow.RouterResult;
import com.shane.orchestragent.common.constant.PropertyKeys;
import com.shane.orchestragent.common.exception.BizErrorFactory;
import com.shane.orchestragent.common.exception.BizException;
import com.shane.orchestragent.memory.model.ConversationCacheUnit;
import com.shane.orchestragent.memory.model.MemoryQueryCriteria;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Map;

/**
 * 多轮会话策略流 (由 RouterAgent 负责首轮意图分流: 简单直答 or 复杂协同流水线)
 *
 * @author Shane
 */
public class MultipleChatStrategyFlow extends ReActStrategyFlow {

    private final RouterAgent routerAgent;

    public MultipleChatStrategyFlow(StrategyContext context, Map<AgentTypeEnum, Agent> systemAgents, int maxStep) {
        super(context, systemAgents, maxStep);
        this.routerAgent = (RouterAgent) systemAgents.get(AgentTypeEnum.ROUTER);
        if (this.routerAgent != null) {
            addAgent(this.routerAgent);
        }
    }

    @Override
    protected String doExecute() throws BizException {
        // 1. 读取并注入历史会话上下文
        loadChatHistory();

        // 2. 路由门禁意图决策
        if (routerAgent != null) {
            RouterResult routerResult = (RouterResult) executeStep(routerAgent, RouterResult.class);
            if (routerResult == null) {
                throw BizErrorFactory.getInstance().routerDecisionEmpty();
            }
            if (!routerResult.isHandoffToPlanner()) {
                String reply = routerResult.getReply();
                if (StringUtils.isBlank(reply)) {
                    throw BizErrorFactory.getInstance().routerReplyEmpty();
                }
                getContext().setRecommendResult(reply);
                getContext().setProperties(PropertyKeys.KEY_RECOMMEND_RESULT, reply);
                log.info("[Flow: {}][FLOW_ROUTER] RouterAgent 判定为日常闲聊或简单查询，执行直接答复", getFlowId());
                return reply;
            } else {
                log.info("[Flow: {}][FLOW_ROUTER] RouterAgent 判定为复杂目标任务，移交 Planner 启动 ReAct 自愈协同循环", getFlowId());
            }
        }

        // 3. 复杂任务: 移交规划器与 ReAct 自愈循环
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
