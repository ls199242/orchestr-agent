package com.shane.orchestragent.repository.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Map;

/**
 * 提示词组编排绑定实体
 * 将特定的策略编码、流程类型与各智能体所需的提示词版本进行聚合绑定
 *
 * @author Shane
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PromptGroupConfigDO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 提示词组编码 */
    private String code;

    /** 提示词组名称 */
    private String name;

    /** 关联的策略编码（如 "react_default_strategy"） */
    private String strategyCode;

    /** 关联的流程拓扑类型（如 "REACT", "MULTIPLE_CHAT"） */
    private String flowType;

    /** 版本号（如 "v1.0.0"） */
    private String version;

    /** 智能体角色到对应提示词模板编码的映射关系 (AgentName -> PromptCode) */
    private Map<String, String> promptBindings;
}
