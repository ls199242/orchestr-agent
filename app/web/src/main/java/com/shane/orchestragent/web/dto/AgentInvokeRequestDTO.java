package com.shane.orchestragent.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Map;

/**
 * 智能体编排调用请求数据传输对象 (DTO)
 * 供 Controller 接收外部 HTTP 调用请求
 *
 * @author Shane
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentInvokeRequestDTO implements Serializable {

    /** 目标策略业务编码 (如: default, biz_travel 等) */
    private String strategyCode;

    /** 用户本次输入的提示词、自然语言指令或核心任务目标 */
    private String message;

    /** 会话唯一标识 (SessionId)，用于隔离多轮对话历史上下文与长效记忆 */
    private String sessionId;

    /** 外部调用方用户标识 */
    private String userId;

    /** 链路追踪标识 (TraceId)，用于分布式全链路排查 */
    private String traceId;

    /** 流程唯一标识 (FlowId)，支持指定流程唯一标识或断点恢复 */
    private String flowId;

    /** 扩展运行属性字典，支持业务方定制化参数透传 */
    private Map<String, Object> properties;

    /** 兼容业务数据字典，支持特定参数映射 (如 query 等) */
    private Map<String, String> bizData;
}
