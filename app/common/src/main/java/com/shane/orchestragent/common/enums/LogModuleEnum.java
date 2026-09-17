package com.shane.orchestragent.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 业务日志模块与类别枚举 (参考 expert 设计)
 *
 * @author Shane
 */
@Getter
@AllArgsConstructor
public enum LogModuleEnum {

    /** 异步调用入口 */
    API_INVOKE("API", "INVOKE"),

    /** 流式对话入口 */
    API_CHAT("API", "CHAT"),

    /** 同步测试入口 */
    API_TEST_INVOKE("API", "TEST_INVOKE"),

    /** 仓储配置管理入口 */
    API_CONFIG("API", "CONFIG"),

    /** 日志流式读取管理 */
    API_LOG_STREAM("API", "LOG_STREAM"),

    /** 策略工作流编排调度 */
    STRATEGY_FLOW("FLOW", "STRATEGY_FLOW"),

    /** 仓储配置加载与刷新 */
    CONFIG_CENTER("CONFIG", "CENTER");

    private final String module;
    private final String category;
}
