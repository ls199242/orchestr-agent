package com.shane.orchestragent.common.exception;

/**
 * 业务异常
 *
 * @author Shane
 */
public class BizException extends BaseException {

    public BizException(String errorCode, String errorMessage) {
        super(errorCode, errorMessage);
    }

    public BizException(String errorCode, String errorMessage, Throwable cause) {
        super(errorCode, errorMessage, cause);
    }

    public BizException(Throwable cause) {
        super(cause);
    }
}
