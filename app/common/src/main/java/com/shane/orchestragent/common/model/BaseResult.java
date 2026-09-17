package com.shane.orchestragent.common.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 统一通用响应封装
 *
 * @author Shane
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BaseResult<T> implements Serializable {

    private boolean success;
    private String code;
    private String message;
    private T data;

    public static <T> BaseResult<T> ok(T data) {
        return BaseResult.<T>builder()
                .success(true)
                .code("SUCCESS")
                .message("OK")
                .data(data)
                .build();
    }

    public static <T> BaseResult<T> fail(String code, String message) {
        return BaseResult.<T>builder()
                .success(false)
                .code(code)
                .message(message)
                .build();
    }
}
