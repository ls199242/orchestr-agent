package com.shane.orchestragent.test.web;

import com.shane.orchestragent.common.annotation.Api;
import com.shane.orchestragent.common.enums.LogModuleEnum;
import com.shane.orchestragent.common.model.BaseResult;
import com.shane.orchestragent.repository.DictRepository;
import com.shane.orchestragent.web.aspect.ApiAspect;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * ApiAspect 日志切面功能单元测试
 *
 * @author Shane
 */
public class ApiAspectTest {

    static class SampleController {
        @Api(logModule = LogModuleEnum.API_INVOKE, desc = "测试接口")
        public BaseResult<String> testMethod(String input) {
            return BaseResult.ok("hello: " + input);
        }

        @Api(logModule = LogModuleEnum.API_TEST_INVOKE, desc = "异常接口")
        public BaseResult<String> errorMethod() {
            throw new RuntimeException("业务模拟异常");
        }
    }

    @Test
    @DisplayName("测试 ApiAspect 正常拦截与响应出参处理")
    public void testApiAspectSuccess() throws Throwable {
        ApiAspect aspect = new ApiAspect();
        DictRepository mockDictRepo = Mockito.mock(DictRepository.class);
        when(mockDictRepo.getValue(eq(DictRepository.Keys.KEY_LOG_FLAGS), anyBoolean())).thenReturn(true);
        ReflectionTestUtils.setField(aspect, "dictRepository", mockDictRepo);

        ProceedingJoinPoint pjp = Mockito.mock(ProceedingJoinPoint.class);
        MethodSignature signature = Mockito.mock(MethodSignature.class);
        Method method = SampleController.class.getMethod("testMethod", String.class);

        when(signature.getMethod()).thenReturn(method);
        when(pjp.getSignature()).thenReturn(signature);
        when(pjp.getArgs()).thenReturn(new Object[]{"world"});
        when(pjp.proceed()).thenReturn(BaseResult.ok("hello: world"));

        Object result = aspect.apiAround(pjp);
        Assertions.assertNotNull(result);
        Assertions.assertTrue(result instanceof BaseResult);
        Assertions.assertEquals("hello: world", ((BaseResult<?>) result).getData());
    }

    @Test
    @DisplayName("测试 ApiAspect 异常拦截与向外抛出")
    public void testApiAspectError() throws Throwable {
        ApiAspect aspect = new ApiAspect();
        DictRepository mockDictRepo = Mockito.mock(DictRepository.class);
        when(mockDictRepo.getValue(eq(DictRepository.Keys.KEY_LOG_FLAGS), anyBoolean())).thenReturn(true);
        ReflectionTestUtils.setField(aspect, "dictRepository", mockDictRepo);

        ProceedingJoinPoint pjp = Mockito.mock(ProceedingJoinPoint.class);
        MethodSignature signature = Mockito.mock(MethodSignature.class);
        Method method = SampleController.class.getMethod("errorMethod");

        when(signature.getMethod()).thenReturn(method);
        when(pjp.getSignature()).thenReturn(signature);
        when(pjp.getArgs()).thenReturn(new Object[]{});
        when(pjp.proceed()).thenThrow(new RuntimeException("业务模拟异常"));

        Assertions.assertThrows(RuntimeException.class, () -> aspect.apiAround(pjp));
    }
}
