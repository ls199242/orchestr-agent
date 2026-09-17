package com.shane.orchestragent.biz.context;

import java.io.Serializable;
import java.util.Map;

/**
 * 智能体运行上下文根接口
 * 最基础的上下文抽象，仅包含全链路通用的元信息，与具体的流程编排和会话解耦。
 *
 * @author Shane
 */
public interface AgentContext extends Serializable {

    /**
     * 获取全链路追踪标识
     *
     * @return 链路追踪 traceId
     */
    String getTraceId();

    /**
     * 是否流式输出
     *
     * @return true-流式返回，false-同步阻塞返回
     */
    boolean isStream();

    /**
     * 获取运行时动态透传属性表
     *
     * @return 属性字典
     */
    Map<String, Object> getProperties();

    /**
     * 设置运行时属性
     *
     * @param key 属性键
     * @param value 属性值
     */
    void setProperties(String key, Object value);

    /**
     * 添加运行时属性（对齐 expert 习惯，等同于 setProperties）
     *
     * @param key 属性键
     * @param value 属性值
     */
    default void addProperty(String key, Object value) {
        setProperties(key, value);
    }

    /**
     * 获取指定运行时属性
     *
     * @param key 属性键
     * @return 属性值
     */
    Object getProperty(String key);

    /**
     * 获取当前智能体节点的输入参数
     *
     * @return 当前节点请求入参对象
     */
    Object getAgentRequest();
}
