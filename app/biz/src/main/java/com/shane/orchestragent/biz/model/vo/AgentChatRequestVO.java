package com.shane.orchestragent.biz.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Map;

/**
 * 智能体流式会话请求业务对象 (VO)
 *
 * @author Shane
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentChatRequestVO implements Serializable {

    /** 目标策略业务编码 */
    private String strategyCode;

    /** 用户本次发送的自然语言对话消息 */
    private String message;

    /** 会话唯一标识，用于多轮对话历史维护与状态持久化隔离 */
    private String sessionId;

    /** 调用方用户身份标识 */
    private String userId;

    /** 链路追踪唯一标识 (TraceId) */
    private String traceId;

    /** 自定义编排上下文运行属性字典 */
    private Map<String, Object> properties;
}
