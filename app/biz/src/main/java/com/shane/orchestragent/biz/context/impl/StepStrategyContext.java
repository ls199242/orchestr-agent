package com.shane.orchestragent.biz.context.impl;

import com.shane.orchestragent.biz.agent.Agent;
import com.shane.orchestragent.biz.context.StrategyContext;
import com.shane.orchestragent.biz.model.flow.EvaluatorResult;
import com.shane.orchestragent.biz.model.flow.FlowAgentMessage;
import com.shane.orchestragent.biz.model.flow.PlanResult;
import com.shane.orchestragent.biz.model.flow.SupervisorResult;
import com.shane.orchestragent.biz.model.request.RecommendRequestVO;
import com.shane.orchestragent.common.constant.PropertyKeys;
import com.shane.orchestragent.memory.context.MemoryContext;
import com.shane.orchestragent.repository.model.StrategyConfigDO;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 单个 DAG 步骤执行线程隔离上下文
 * <p>
 * 包装全局 {@link StrategyContext}，隔离当前步骤的入参与局部属性（如伴生 RAG 检索结果、整改反馈等），
 * 防止多节点并发执行时入参相互覆盖或产生竞争条件。
 *
 * @author Shane
 */
public class StepStrategyContext implements StrategyContext {

    private final StrategyContext delegate;
    private final Map<String, Object> stepInputs;
    private final String stepFeedback;
    private final Map<String, Object> localProperties = new ConcurrentHashMap<>();

    public StepStrategyContext(StrategyContext delegate, Map<String, Object> stepInputs, String stepFeedback) {
        this.delegate = Objects.requireNonNull(delegate, "delegate context 不能为空");
        this.stepInputs = stepInputs != null ? stepInputs : Collections.emptyMap();
        this.stepFeedback = stepFeedback;
        if (stepFeedback != null) {
            localProperties.put(PropertyKeys.KEY_EVALUATOR_FEEDBACK, stepFeedback);
            localProperties.put("feedback", stepFeedback);
        }
    }

    @Override
    public String getFlowId() {
        return delegate.getFlowId();
    }

    @Override
    public String getSessionId() {
        return delegate.getSessionId();
    }

    @Override
    public String getUserId() {
        return delegate.getUserId();
    }

    @Override
    public void setUserId(String userId) {
        delegate.setUserId(userId);
    }

    @Override
    public RecommendRequestVO getRequest() {
        return delegate.getRequest();
    }

    @Override
    public StrategyConfigDO getConfig() {
        return delegate.getConfig();
    }

    @Override
    public Map<String, Agent> getAgents() {
        return delegate.getAgents();
    }

    @Override
    public void registerAgent(Agent agent) {
        delegate.registerAgent(agent);
    }

    @Override
    public List<FlowAgentMessage> getChatMessages() {
        return delegate.getChatMessages();
    }

    @Override
    public void addChatMessage(FlowAgentMessage message) {
        delegate.addChatMessage(message);
    }

    @Override
    public PlanResult getPlanResult() {
        return delegate.getPlanResult();
    }

    @Override
    public void setPlanResult(PlanResult planResult) {
        delegate.setPlanResult(planResult);
    }

    @Override
    public SupervisorResult getLastSupervisorResult() {
        return delegate.getLastSupervisorResult();
    }

    @Override
    public void setLastSupervisorResult(SupervisorResult result) {
        delegate.setLastSupervisorResult(result);
    }

    @Override
    public EvaluatorResult getLastEvaluatorResult() {
        return delegate.getLastEvaluatorResult();
    }

    @Override
    public void setLastEvaluatorResult(EvaluatorResult result) {
        delegate.setLastEvaluatorResult(result);
    }

    @Override
    public String getEvaluationFeedback() {
        if (stepFeedback != null) {
            return stepFeedback;
        }
        return delegate.getEvaluationFeedback();
    }

    @Override
    public void setEvaluationFeedback(String feedback) {
        delegate.setEvaluationFeedback(feedback);
    }

    @Override
    public int getEvaluationRetryCount() {
        return delegate.getEvaluationRetryCount();
    }

    @Override
    public void setEvaluationRetryCount(int count) {
        delegate.setEvaluationRetryCount(count);
    }

    @Override
    public String getRecommendResult() {
        return delegate.getRecommendResult();
    }

    @Override
    public void setRecommendResult(String result) {
        delegate.setRecommendResult(result);
    }

    @Override
    public String getLastAgentResult() {
        return delegate.getLastAgentResult();
    }

    @Override
    public void setLastAgentResult(String result) {
        delegate.setLastAgentResult(result);
    }

    @Override
    public Map<String, Object> getNextAgentRequest() {
        return stepInputs;
    }

    @Override
    public void setNextAgentRequest(Map<String, Object> nextRequest) {
        // 当前步骤作用域内保护
    }

    @Override
    public MemoryContext getMemoryContext() {
        return delegate.getMemoryContext();
    }

    @Override
    public void setMemoryContext(MemoryContext memoryContext) {
        delegate.setMemoryContext(memoryContext);
    }

    @Override
    public String getStrategyTarget() {
        return delegate.getStrategyTarget();
    }

    @Override
    public Map<String, Object> getProperties() {
        Map<String, Object> merged = new LinkedHashMap<>(delegate.getProperties());
        merged.putAll(localProperties);
        merged.putAll(stepInputs);
        return merged;
    }

    @Override
    public void setProperties(String key, Object value) {
        if (key != null) {
            if (value != null) {
                localProperties.put(key, value);
            } else {
                localProperties.remove(key);
            }
        }
    }

    @Override
    public Object getProperty(String key) {
        if (key == null) return null;
        if (localProperties.containsKey(key)) {
            return localProperties.get(key);
        }
        if (stepInputs.containsKey(key)) {
            return stepInputs.get(key);
        }
        return delegate.getProperty(key);
    }

    @Override
    public String getTraceId() {
        return delegate.getTraceId();
    }

    @Override
    public boolean isStream() {
        return delegate.isStream();
    }

    @Override
    public Object getAgentRequest() {
        return stepInputs;
    }
}
