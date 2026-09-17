package com.shane.orchestragent.common.exception;

import lombok.Getter;

/**
 * 基础异常
 *
 * @author Shane
 */
@Getter
public class BaseException extends Exception {

    private final String errorCode;
    private final String errorMessage;

    public BaseException(String errorCode, String errorMessage) {
        super("[" + errorCode + "] " + errorMessage);
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }

    public BaseException(String errorCode, String errorMessage, Throwable cause) {
        super("[" + errorCode + "] " + errorMessage, cause);
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }

    public BaseException(Throwable cause) {
        super(cause);
        this.errorCode = "SYSTEM_ERROR";
        this.errorMessage = cause != null ? cause.getMessage() : "Unknown error";
    }
}
