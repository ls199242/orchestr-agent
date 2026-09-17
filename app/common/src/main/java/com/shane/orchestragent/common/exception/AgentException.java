package com.shane.orchestragent.common.exception;

/**
 * 智能体专用异常
 *
 * @author Shane
 */
public class AgentException extends BizException {

    public AgentException(String errorCode, String errorMessage) {
        super(errorCode, errorMessage);
    }

    public AgentException(String errorCode, String errorMessage, Throwable cause) {
        super(errorCode, errorMessage, cause);
    }
}
