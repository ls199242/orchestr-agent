package com.shane.orchestragent.common.enums;

import lombok.Getter;

/**
 * 智能体类型枚举
 *
 * @author Shane
 */
@Getter
public enum AgentTypeEnum {

    /** 多轮对话意图识别智能体 */
    COORDINATOR("COORDINATOR", "多轮对话意图识别智能体"),

    /** 战略任务规划智能体 */
    PLANNER("PLANNER", "战略任务规划智能体"),

    /** 步进循环调度指挥智能体 */
    SUPERVISOR("SUPERVISOR", "步进循环调度指挥智能体"),

    /** 专精任务执行智能体 */
    EXPERT("EXPERT", "专精任务执行智能体"),

    /** 战略目标审查与自愈评估智能体 */
    EVALUATOR("EVALUATOR", "战略目标审查与自愈评估智能体"),

    /** 成果汇编汇报智能体 */
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
