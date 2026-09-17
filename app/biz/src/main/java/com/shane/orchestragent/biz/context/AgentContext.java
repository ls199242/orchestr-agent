package com.shane.orchestragent.biz.context;

import java.io.Serializable;
import java.util.Map;

/**
 * 智能体运行上下文根接口
 *
 * @author Shane
 */
public interface AgentContext extends Serializable {

    String getSessionId();

    String getFlowId();

    Map<String, Object> getProperties();

    void setProperties(String key, Object value);

    Object getProperty(String key);
}
