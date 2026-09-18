package com.shane.orchestragent.repository.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 编排策略配置实体
 * 定义协同流程的执行拓扑、参与智能体团队、可用工具、步数限制与质检配置
 *
 * @author Shane
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StrategyConfigDO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 策略唯一主键标识 */
    private String strategyId;

    /** 策略业务编码（如 "react_default_strategy"） */
    private String code;

    /** 策略中文名称 */
    private String name;

    /** 策略功能描述 */
    private String description;

    /** 策略核心目标提示词或系统说明 */
    private String prompt;

    /** 流程最大步数限制（防止死循环） */
    private Integer maxStep;

    /** 适用的业务 Worker 编码列表 (从 AgentConfigRepository 加载挂载) */
    private List<String> workerCodes;

    /** 适用的工具代码列表 (从 ToolConfigRepository 加载挂载) */
    private List<String> toolCodes;

    /** 策略拓扑类型 (REACT / MULTIPLE_CHAT) */
    private String flowTopologyType;

    /** 是否启用流式输出 (SSE) */
    @Builder.Default
    private Boolean stream = false;

    /** 扩展属性配置表 */
    private Map<String, Object> properties;
}
