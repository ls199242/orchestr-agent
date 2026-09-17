package com.shane.orchestragent.common.annotation;

import com.shane.orchestragent.common.enums.LogModuleEnum;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口调用与日志跟踪注解 (参考 laiye-expert-core 的 @Api)
 * 配合 ApiAspect 统一拦截记录请求入参、响应出参、执行耗时与异常信息
 *
 * @author Shane
 */
@Target({ ElementType.TYPE, ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
public @interface Api {

    /**
     * 日志业务模块分类
     */
    LogModuleEnum logModule() default LogModuleEnum.API_INVOKE;

    /**
     * 接口中文描述
     */
    String desc() default "";

    /**
     * 是否记录请求参数
     */
    boolean recordParams() default true;

    /**
     * 是否记录响应结果 (非 SSE 情况下)
     */
    boolean recordResponse() default true;
}
