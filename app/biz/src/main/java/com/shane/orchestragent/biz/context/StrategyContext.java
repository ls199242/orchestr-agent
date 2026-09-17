package com.shane.orchestragent.biz.context;

import com.shane.orchestragent.biz.agent.Agent;
import com.shane.orchestragent.biz.model.flow.ConductorResult;
import com.shane.orchestragent.biz.model.flow.EvaluatorResult;
import com.shane.orchestragent.biz.model.flow.FlowAgentMessage;
import com.shane.orchestragent.biz.model.flow.PlanResult;
import com.shane.orchestragent.biz.model.request.RecommendRequestVO;
import com.shane.orchestragent.memory.context.MemoryContext;
import com.shane.orchestragent.repository.model.StrategyConfigDO;

import java.util.List;
import java.util.Map;

/**
 * 策略编排运行全生命周期上下文接口
 *
 * @author Shane
 */
public interface StrategyContext extends AgentContext {

    String getFlowId();

    String getSessionId();

    String getUserId();

    void setUserId(String userId);

    RecommendRequestVO getRequest();

    StrategyConfigDO getConfig();

    Map<String, Object> getProperties();

    Map<String, Agent> getAgents();

    List<FlowAgentMessage> getChatMessages();

    void addChatMessage(FlowAgentMessage message);

    void registerAgent(Agent agent);

    PlanResult getPlanResult();

    void setPlanResult(PlanResult planResult);

    ConductorResult getLastConductorResult();

    void setLastConductorResult(ConductorResult result);

    EvaluatorResult getLastEvaluatorResult();

    void setLastEvaluatorResult(EvaluatorResult result);

    String getEvaluationFeedback();

    void setEvaluationFeedback(String feedback);

    int getEvaluationRetryCount();

    void setEvaluationRetryCount(int count);

    String getRecommendResult();

    void setRecommendResult(String result);

    String getLastAgentResult();

    void setLastAgentResult(String result);

    Map<String, Object> getNextAgentRequest();

    void setNextAgentRequest(Map<String, Object> nextRequest);

    MemoryContext getMemoryContext();

    void setMemoryContext(MemoryContext memoryContext);

    String getStrategyTarget();
}
