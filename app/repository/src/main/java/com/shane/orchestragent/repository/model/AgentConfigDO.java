package com.shane.orchestragent.repository.model;

import com.shane.orchestragent.prompt.model.PromptTypeEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Map;

/**
 * 智能体元数据配置实体
 * 描述智能体的角色定位、模型参数、提示词模板及特定运行约束
 *
 * @author Shane
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentConfigDO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 智能体名称唯一标识（如 PLANNER, CONDUCTOR, ROUTER） */
    private String name;

    /** 智能体业务代码 */
    private String code;

    /** 智能体角色功能描述 */
    private String description;

    /** 智能体分类类型（如 SYSTEM, WORKER） */
    private String agentType;

    /** 绑定的模型名称（如 gpt-4o, deepseek-chat） */
    private String model;

    /** 模型发散采样温度 (0.0 ~ 2.0) */
    private Double temperature;

    /** 生成 Token 上限 */
    private Integer maxTokens;

    /** 节点执行超时等待毫秒数 */
    private Long nodeWaitTime;

    /** 绑定的提示词模板集合 (PROMPT_SYSTEM, PROMPT_USER) */
    private Map<PromptTypeEnum, String> promptTemplates;

    /** 额外扩展属性表 */
    private Map<String, Object> extraProperties;
}
