package com.shane.orchestragent.prompt.model;

import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

/**
 * 提示词类型枚举
 *
 * @author Shane
 */
@Getter
public enum PromptTypeEnum {

    /** 路由系统提示词 */
    ROUTER_SYSTEM_PROMPT("ROUTER_SYSTEM_PROMPT", "路由系统提示词"),
    /** 路由用户提示词 */
    ROUTER_USER_PROMPT("ROUTER_USER_PROMPT", "路由用户提示词"),

    /** 规划器系统提示词 */
    PLANNER_SYSTEM_PROMPT("PLANNER_SYSTEM_PROMPT", "规划器系统提示词"),
    /** 规划器用户提示词 */
    PLANNER_USER_PROMPT("PLANNER_USER_PROMPT", "规划器用户提示词"),

    /** 指挥调度系统提示词 */
    CONDUCTOR_SYSTEM_PROMPT("CONDUCTOR_SYSTEM_PROMPT", "指挥调度系统提示词"),
    /** 指挥调度规划反馈提示词 */
    CONDUCTOR_PLANNER_USER_PROMPT("CONDUCTOR_PLANNER_USER_PROMPT", "指挥调度规划反馈提示词"),
    /** 指挥调度工具反馈提示词 */
    CONDUCTOR_TOOL_USER_PROMPT("CONDUCTOR_TOOL_USER_PROMPT", "指挥调度工具反馈提示词"),
    /** 指挥调度工作反馈提示词 */
    CONDUCTOR_WORKER_USER_PROMPT("CONDUCTOR_WORKER_USER_PROMPT", "指挥调度工作反馈提示词"),
    /** 指挥调度用户标签提示词 */
    CONDUCTOR_USER_TAGS_USER_PROMPT("CONDUCTOR_USER_TAGS_USER_PROMPT", "指挥调度用户标签提示词"),
    /** 指挥调度评估器批评整改提示词 */
    CONDUCTOR_EVALUATOR_USER_PROMPT("CONDUCTOR_EVALUATOR_USER_PROMPT", "指挥调度评估器批评整改提示词"),

    /** 工作智能体系统提示词 */
    WORKER_SYSTEM_PROMPT("WORKER_SYSTEM_PROMPT", "工作智能体系统提示词"),
    /** 工作智能体用户提示词 */
    WORKER_USER_PROMPT("WORKER_USER_PROMPT", "工作智能体用户提示词"),
    /** 工作智能体 RAG 增强提示词 */
    WORKER_RAG_USER_PROMPT("WORKER_RAG_USER_PROMPT", "工作智能体 RAG 增强提示词"),

    /** 战略目标核验系统提示词 */
    EVALUATOR_SYSTEM_PROMPT("EVALUATOR_SYSTEM_PROMPT", "战略目标核验系统提示词"),
    /** 战略目标核验用户提示词 */
    EVALUATOR_USER_PROMPT("EVALUATOR_USER_PROMPT", "战略目标核验用户提示词"),

    /** 成果汇报系统提示词 */
    REPORTER_SYSTEM_PROMPT("REPORTER_SYSTEM_PROMPT", "成果汇报系统提示词"),
    /** 成果汇报用户提示词 */
    REPORTER_USER_PROMPT("REPORTER_USER_PROMPT", "成果汇报用户提示词"),

    /** 提示词调优 */
    PROMPT_OPTIMIZE("PROMPT_OPTIMIZE", "提示词调优"),

    /** 对话历史摘要系统提示词 */
    CHAT_SUMMARY_SYSTEM_PROMPT("CHAT_SUMMARY_SYSTEM_PROMPT", "对话历史摘要系统提示词"),
    /** 对话历史摘要用户提示词 */
    CHAT_SUMMARY_USER_PROMPT("CHAT_SUMMARY_USER_PROMPT", "对话历史摘要用户提示词");

    private final String code;
    private final String description;

    private static final Map<String, PromptTypeEnum> CACHE = new HashMap<>();

    static {
        for (PromptTypeEnum typeEnum : PromptTypeEnum.values()) {
            CACHE.put(typeEnum.name(), typeEnum);
            CACHE.put(typeEnum.getCode(), typeEnum);
        }
    }

    PromptTypeEnum(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public static PromptTypeEnum of(String name) {
        return CACHE.get(name);
    }
}
