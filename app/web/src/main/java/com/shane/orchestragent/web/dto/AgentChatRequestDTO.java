package com.shane.orchestragent.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Map;

/**
 * 智能体流式多轮会话请求数据传输对象 (DTO)
 * 供 Controller 接收流式会话交互请求
 *
 * @author Shane
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentChatRequestDTO implements Serializable {

    /** 目标策略业务编码 (如: default, biz_travel 等) */
    private String strategyCode;

    /** 用户发送的会话消息或对话提问 */
    private String message;

    /** 会话唯一标识 (SessionId)，用于隔离会话历史上下文与记忆恢复 */
    private String sessionId;

    /** 外部调用方用户标识 */
    private String userId;

    /** 链路追踪标识 (TraceId) */
    private String traceId;

    /** 扩展属性字典 */
    private Map<String, Object> properties;
}
