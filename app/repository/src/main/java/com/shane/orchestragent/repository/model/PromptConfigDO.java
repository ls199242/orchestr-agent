package com.shane.orchestragent.repository.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 提示词模板配置实体
 * 封装各智能体角色使用的 System/User Prompt 模板文本与业务归属域
 *
 * @author Shane
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PromptConfigDO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 提示词模板名称（如 "PLANNER_SYSTEM"） */
    private String name;

    /** 提示词唯一编码 */
    private String code;

    /** 提示词模板正文内容（支持 Mustache / Freemarker 变量插槽） */
    private String prompt;

    /** 业务归属域 */
    private String domain;

    /** 模板功能说明 */
    private String description;
}
