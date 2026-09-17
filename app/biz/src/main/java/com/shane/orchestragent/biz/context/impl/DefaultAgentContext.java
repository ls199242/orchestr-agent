package com.shane.orchestragent.biz.context.impl;

import com.shane.orchestragent.biz.context.AgentContext;
import lombok.Getter;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 默认基础智能体运行上下文实现类
 * 适用于单 Agent 独立执行、测试或轻量级调用场景。
 *
 * @author Shane
 */
@Getter
public class DefaultAgentContext implements AgentContext {

    private final Map<String, Object> properties = new ConcurrentHashMap<>();
    private final String traceId;
    private final boolean stream;
    private Object agentRequest;

    public DefaultAgentContext() {
        this(UUID.randomUUID().toString(), false, null);
    }

    public DefaultAgentContext(String traceId) {
        this(traceId, false, null);
    }

    public DefaultAgentContext(String traceId, boolean stream) {
        this(traceId, stream, null);
    }

    public DefaultAgentContext(String traceId, boolean stream, Object agentRequest) {
        this.traceId = traceId != null ? traceId : UUID.randomUUID().toString();
        this.stream = stream;
        this.agentRequest = agentRequest;
    }

    @Override
    public boolean isStream() {
        return stream;
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
    public Object getAgentRequest() {
        return agentRequest;
    }

    public void setAgentRequest(Object agentRequest) {
        this.agentRequest = agentRequest;
    }
}
