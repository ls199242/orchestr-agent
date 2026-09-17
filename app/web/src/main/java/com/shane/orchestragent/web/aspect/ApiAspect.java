package com.shane.orchestragent.web.aspect;

import com.shane.orchestragent.common.annotation.Api;
import com.shane.orchestragent.common.enums.LogModuleEnum;
import com.shane.orchestragent.common.utils.JsonUtils;
import com.shane.orchestragent.repository.DictRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * 核心 API 统一日志切面 (参考 laiye-expert-core 的 ApiAspect)
 * 拦截标注了 @Api 的控制器方法，结合 DictRepository 的 log_flag 控制开关，
 * 统一输出请求入参、响应出参、执行耗时和异常堆栈
 *
 * @author Shane
 */
@Aspect
@Component
@Slf4j
public class ApiAspect {

    @Autowired(required = false)
    private DictRepository dictRepository;

    @Around("@annotation(com.shane.orchestragent.common.annotation.Api) || @within(com.shane.orchestragent.common.annotation.Api)")
    public Object apiAround(ProceedingJoinPoint pjp) throws Throwable {
        long startTime = System.currentTimeMillis();
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        Method method = signature.getMethod();

        Api api = method.getAnnotation(Api.class);
        if (api == null) {
            api = pjp.getTarget().getClass().getAnnotation(Api.class);
        }

        String methodName = method.getName();
        String moduleTag = getModuleTag(api);
        String descTag = (api != null && StringUtils.isNotBlank(api.desc())) ? "(" + api.desc() + ")" : "";

        // 根据字典配置决定是否开启详细日志记录，默认为 true
        boolean logFlag = dictRepository == null || dictRepository.getValue(DictRepository.Keys.KEY_LOG_FLAGS, true);

        // 1. 记录请求入参
        if (logFlag && (api == null || api.recordParams())) {
            String requestJson = buildParamsJson(pjp.getArgs());
            log.info("[API][REQUEST]{} {}{}，入参 JSON={}", moduleTag, methodName, descTag, requestJson);
        }

        Object result;
        try {
            result = pjp.proceed();
        } catch (Throwable e) {
            long costMs = System.currentTimeMillis() - startTime;
            log.error("[API][ERROR]{} {}{}，耗时: {}ms，异常信息: {}", moduleTag, methodName, descTag, costMs, e.getMessage(), e);
            throw e;
        }

        long costMs = System.currentTimeMillis() - startTime;

        // 2. 记录响应出参 (排除 SSE 流式对象以避免阻塞与死锁)
        if (logFlag && (api == null || api.recordResponse())) {
            if (result instanceof SseEmitter) {
                log.info("[API][RESPONSE]{} {}{} [SSE流式建立]，耗时: {}ms", moduleTag, methodName, descTag, costMs);
            } else if (result != null) {
                String responseJson = JsonUtils.toJsonString(result);
                log.info("[API][RESPONSE]{} {}{}，耗时: {}ms，出参 JSON={}", moduleTag, methodName, descTag, costMs, responseJson);
            } else {
                log.info("[API][RESPONSE]{} {}{}，耗时: {}ms，出参=null", moduleTag, methodName, descTag, costMs);
            }
        }

        return result;
    }

    private String getModuleTag(Api api) {
        if (api != null && api.logModule() != null) {
            LogModuleEnum m = api.logModule();
            return "[" + m.getModule() + "][" + m.getCategory() + "]";
        }
        return "[API][DEFAULT]";
    }

    private String buildParamsJson(Object[] args) {
        if (args == null || args.length == 0) {
            return "[]";
        }
        List<Object> serializableArgs = new ArrayList<>();
        for (Object arg : args) {
            if (arg == null) {
                serializableArgs.add(null);
            } else if (arg instanceof HttpServletRequest || arg instanceof HttpServletResponse
                    || arg instanceof InputStream || arg instanceof OutputStream) {
                // 跳过无法直接序列化的底层网络 IO 对象
                serializableArgs.add(arg.getClass().getSimpleName());
            } else {
                serializableArgs.add(arg);
            }
        }
        try {
            return JsonUtils.toJsonString(serializableArgs.size() == 1 ? serializableArgs.get(0) : serializableArgs);
        } catch (Exception e) {
            return "[Serialization failed: " + e.getMessage() + "]";
        }
    }
}
