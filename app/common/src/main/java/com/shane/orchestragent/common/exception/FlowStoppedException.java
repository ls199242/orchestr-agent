package com.shane.orchestragent.common.exception;

/**
 * 流程主动终止/取消异常 (用于统一阻断执行链路并由模板方法顶层捕获收口)
 *
 * @author Shane
 */
public class FlowStoppedException extends BizException {

    public static final String ERROR_CODE = "FLOW_STOPPED";

    public FlowStoppedException() {
        super(ERROR_CODE, "策略流程已被主动终止");
    }

    public FlowStoppedException(String message) {
        super(ERROR_CODE, message);
    }

    public FlowStoppedException(String message, Throwable cause) {
        super(ERROR_CODE, message, cause);
    }
}
