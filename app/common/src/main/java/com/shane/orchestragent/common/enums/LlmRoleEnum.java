package com.shane.orchestragent.common.enums;

import lombok.Getter;

/**
 * 大模型角色枚举
 *
 * @author Shane
 */
@Getter
public enum LlmRoleEnum {

    SYSTEM("system"),
    USER("user"),
    ASSISTANT("assistant"),
    FUNCTION("function"),
    TOOL("tool");

    private final String role;

    LlmRoleEnum(String role) {
        this.role = role;
    }
}
