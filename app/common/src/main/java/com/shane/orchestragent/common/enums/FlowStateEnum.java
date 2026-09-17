package com.shane.orchestragent.common.enums;

import lombok.Getter;

/**
 * 流程状态枚举
 *
 * @author Shane
 */
@Getter
public enum FlowStateEnum {

    /** 无状态 / 未开始 */
    NONE("NONE", false),

    /** 初始状态 */
    INITIAL("INITIAL", false),

    /** 运行中 */
    RUNNING("RUNNING", false),

    /** 成功完成 */
    FINISHED("FINISHED", true),

    /** 执行出错 */
    ERROR("ERROR", true),

    /** 终止取消 */
    STOPPED("STOPPED", true),

    /** 超出步数限制 */
    TIMEOUT("TIMEOUT", true);

    private final String code;
    private final boolean terminal;

    FlowStateEnum(String code, boolean terminal) {
        this.code = code;
        this.terminal = terminal;
    }

    public static boolean isFinished(FlowStateEnum state) {
        return state != null && state.isTerminal();
    }
}
