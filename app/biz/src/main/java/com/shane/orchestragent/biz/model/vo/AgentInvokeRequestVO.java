package com.shane.orchestragent.biz.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Map;

/**
 * 智能体编排调用请求业务对象 (VO)
 *
 * @author Shane
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentInvokeRequestVO implements Serializable {

    /** 目标策略唯一编码标识，用于定位具体的策略配置与工作流拓扑 */
    private String strategyId;

    /** 用户本次输入的提示词、自然语言指令或对话核心内容 */
    private String message;

    /** 会话唯一标识，用于维护多轮对话历史上下文与会话记忆隔离 */
    private String sessionId;

    /** 外部调用方用户身份标识 */
    private String userId;

    /** 全链路追踪唯一标识 (TraceId)，用于日志全链路串联与可观测性 */
    private String traceId;

    /** 流程执行唯一标识，支持调用方指定或用于幂等查询与断点恢复 */
    private String flowId;

    /** 自定义编排上下文运行属性字典，支持动态向智能体注入上下文变量 */
    private Map<String, Object> properties;

    /** 业务方透传数据键值对字典，用于特定业务参数传递 (如 query 等) */
    private Map<String, String> bizData;
}
