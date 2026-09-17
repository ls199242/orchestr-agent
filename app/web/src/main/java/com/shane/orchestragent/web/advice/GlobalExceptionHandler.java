package com.shane.orchestragent.web.advice;

import com.shane.orchestragent.common.exception.BizException;
import com.shane.orchestragent.common.model.BaseResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局控制器异常统一拦截处理
 *
 * @author Shane
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BizException.class)
    public BaseResult<Void> handleBizException(BizException e) {
        log.warn("业务拦截异常: [{}] {}", e.getErrorCode(), e.getErrorMessage());
        return BaseResult.fail(e.getErrorCode(), e.getErrorMessage());
    }

    @ExceptionHandler(Exception.class)
    public BaseResult<Void> handleException(Exception e) {
        log.error("未捕获全局异常", e);
        return BaseResult.fail("SYSTEM_ERROR", e.getMessage() != null ? e.getMessage() : "系统运行异常");
    }
}
