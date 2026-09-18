package com.shane.orchestragent.repository.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 智能体元数据配置实体
 * 纯粹描述智能体的角色定位、模型参数及运行约束（模型超参，彻底解耦提示词模板）
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

    /** 智能体系统提示词（固定描述、角色人设、行为约束与输出结构规范） */
    private String systemPrompt;

    /** 智能体用户提示词模板（包含占位符，动态插值渲染） */
    private String userPrompt;

    /** 关联知识库/数据集 ID 列表 (专精 Worker 配置时触发伴生 RAG 智能体) */
    private List<String> datasets;

    /** 适用的特定工具列表 */
    private List<String> tools;

    /** 额外扩展属性表 */
    private Map<String, Object> extraProperties;

    public String getEffectiveSystemPrompt() {
        return systemPrompt;
    }

    public String getEffectiveUserPrompt() {
        return userPrompt;
    }
}
