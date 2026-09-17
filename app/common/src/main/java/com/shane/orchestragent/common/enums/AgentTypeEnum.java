package com.shane.orchestragent.common.enums;

import lombok.Getter;

/**
 * 智能体类型枚举
 *
 * @author Shane
 */
@Getter
public enum AgentTypeEnum {

    /** 意图路由门禁 (原 Coordinator) */
    ROUTER("ROUTER", "意图识别与路由智能体"),

    /** 任务规划 */
    PLANNER("PLANNER", "战略任务规划智能体"),

    /** 步进调度指挥 (原 Supervisor) */
    CONDUCTOR("CONDUCTOR", "步进循环调度指挥智能体"),

    /** 专精任务执行 (原 Expert) */
    WORKER("WORKER", "专精任务执行工作智能体"),

    /** 战略目标评估与批判 (新增) */
    EVALUATOR("EVALUATOR", "战略目标审查与自愈评估智能体"),

    /** 成果汇编汇报 */
    REPORTER("REPORTER", "成果汇编汇报智能体"),

    /** 外部工具 */
    TOOL("TOOL", "工具调用智能体"),

    /** 知识检索 */
    RAG("RAG", "知识库检索增强智能体"),

    /** 会话摘要 */
    SUMMARY("SUMMARY", "会话历史摘要智能体");

    private final String code;
    private final String description;

    AgentTypeEnum(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public static AgentTypeEnum getByCode(String code) {
        for (AgentTypeEnum type : values()) {
            if (type.code.equalsIgnoreCase(code)) {
                return type;
            }
        }
        return null;
    }
}
