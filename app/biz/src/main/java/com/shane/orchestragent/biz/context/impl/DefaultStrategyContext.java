package com.shane.orchestragent.biz.context.impl;

import com.shane.orchestragent.biz.agent.Agent;
import com.shane.orchestragent.biz.context.StrategyContext;
import com.shane.orchestragent.biz.model.flow.ConductorResult;
import com.shane.orchestragent.biz.model.flow.EvaluatorResult;
import com.shane.orchestragent.biz.model.flow.FlowAgentMessage;
import com.shane.orchestragent.biz.model.flow.PlanResult;
import com.shane.orchestragent.biz.model.request.RecommendRequestVO;
import com.shane.orchestragent.common.constant.PropertyKeys;
import com.shane.orchestragent.common.utils.JsonUtils;
import com.shane.orchestragent.memory.context.MemoryContext;
import com.shane.orchestragent.repository.model.StrategyConfigDO;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 默认策略运行上下文实现类
 *
 * @author Shane
 */
@Getter
@Setter
public class DefaultStrategyContext implements StrategyContext {

    private final String flowId;
    private final String sessionId;
    private String userId;
    private final RecommendRequestVO request;
    private final StrategyConfigDO config;

    private final Map<String, Object> properties = new ConcurrentHashMap<>();
    private final Map<String, Agent> agents = new ConcurrentHashMap<>();
    private final List<FlowAgentMessage> chatMessages = new CopyOnWriteArrayList<>();

    private PlanResult planResult;
    private ConductorResult lastConductorResult;
    private EvaluatorResult lastEvaluatorResult;
    private String evaluationFeedback;
    private int evaluationRetryCount = 0;

    private MemoryContext memoryContext;
    private String recommendResult;
    private String lastAgentResult;
    private Map<String, Object> nextAgentRequest;

    public DefaultStrategyContext(StrategyConfigDO config, String flowId, String sessionId, RecommendRequestVO request) {
        this.config = config;
        this.flowId = StringUtils.isNotBlank(flowId) ? flowId : UUID.randomUUID().toString();
        this.sessionId = StringUtils.isNotBlank(sessionId) ? sessionId : UUID.randomUUID().toString();
        this.request = request != null ? request : RecommendRequestVO.builder().flowId(this.flowId).sessionId(this.sessionId).build();

        if (config != null) {
            this.properties.put(PropertyKeys.KEY_STRATEGY, config);
        }
        if (request != null) {
            if (request.getBizData() != null) {
                this.properties.put(PropertyKeys.KEY_REQUEST_BIZ_DATA, request.getBizData());
                this.properties.put(PropertyKeys.KEY_REQUEST_BIZ_DATA_JSON, JsonUtils.toJsonString(request.getBizData()));
            }
            if (StringUtils.isNotBlank(request.getTraceId())) {
                this.properties.put("traceId", request.getTraceId());
            }
        }
        this.properties.put(PropertyKeys.KEY_AGENT_MESSAGE_HISTORY, chatMessages);
    }

    public DefaultStrategyContext() {
        this(null, UUID.randomUUID().toString(), UUID.randomUUID().toString(), null);
    }

    @Override
    public void setProperties(String key, Object value) {
        if (key != null) {
            if (value != null) {
                properties.put(key, value);
            } else {
                properties.remove(key);
            }
        }
    }

    @Override
    public Object getProperty(String key) {
        return key != null ? properties.get(key) : null;
    }

    @Override
    public String getStrategyTarget() {
        Object val = properties.get(PropertyKeys.KEY_STRATEGY_TARGET);
        return val != null ? val.toString() : "";
    }

    @Override
    public void addChatMessage(FlowAgentMessage message) {
        if (message != null) {
            if (message.getTimestamp() == null) {
                message.setTimestamp(System.currentTimeMillis());
            }
            chatMessages.add(message);
        }
    }

    @Override
    public String getTraceId() {
        if (request != null && StringUtils.isNotBlank(request.getTraceId())) {
            return request.getTraceId();
        }
        Object trace = properties.get("traceId");
        return trace != null ? trace.toString() : null;
    }

    @Override
    public boolean isStream() {
        return request != null && request.isStream();
    }

    @Override
    public Object getAgentRequest() {
        return nextAgentRequest != null ? nextAgentRequest : properties.get("agentRequest");
    }

    @Override
    public void registerAgent(Agent agent) {
        if (agent != null && agent.getName() != null) {
            agents.put(agent.getName(), agent);
        }
    }
}
