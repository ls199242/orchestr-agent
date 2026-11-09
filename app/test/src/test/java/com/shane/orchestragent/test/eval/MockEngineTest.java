package com.shane.orchestragent.test.eval;

import com.shane.orchestragent.biz.eval.mock.FaultInjector;
import com.shane.orchestragent.biz.eval.mock.MockEngine;
import com.shane.orchestragent.biz.eval.model.CaseMockBundleDTO;
import com.shane.orchestragent.biz.eval.trace.TraceCollector;
import com.shane.orchestragent.common.exception.BizException;
import com.shane.orchestragent.common.time.FixedTimeProvider;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

/**
 * Mock 匹配引擎、故障注入与固定时钟单元测试
 *
 * @author Shane
 */
public class MockEngineTest {

    /**
     * 测试 Mock 参数子集匹配、次序出队消费与严格模式未命中阻断
     */
    @Test
    @DisplayName("测试 Mock 参数匹配、次序出队与严格模式缺口拦截")
    public void testMockEngineMatchAndSequence() throws BizException {
        // 构造单次匹配规则与次序返回规则
        CaseMockBundleDTO.ToolMockRuleDTO r1 = CaseMockBundleDTO.ToolMockRuleDTO.builder()
                .ruleId("r1")
                .match(Map.of("departureDate", "2026-12-19"))
                .returnBody("{\"flightNo\":\"XX101\"}")
                .build();

        CaseMockBundleDTO.ToolMockRuleDTO r2 = CaseMockBundleDTO.ToolMockRuleDTO.builder()
                .ruleId("r2")
                .match(Map.of("departureDate", "2026-12-20"))
                .sequence(List.of("{\"attempt\": 1}", "{\"attempt\": 2}"))
                .build();

        Map<String, List<CaseMockBundleDTO.ToolMockRuleDTO>> toolMocks = Map.of(
                "query_flight_schedule_tool", List.of(r1, r2)
        );

        FixedTimeProvider timeProvider = new FixedTimeProvider("2026-11-07");
        TraceCollector collector = new TraceCollector("t1", "c1", "r1", 1, "FREE", timeProvider);

        MockEngine engine = new MockEngine(toolMocks, null, true, collector);

        // 1. 测试单次匹配 (传入包含额外参数的 JSON，子集匹配成功)
        String res1 = engine.matchToolMock("query_flight_schedule_tool", "{\"departureDate\":\"2026-12-19\",\"city\":\"SHA\"}");
        Assertions.assertEquals("{\"flightNo\":\"XX101\"}", res1);

        // 2. 测试次序队列出队 (支持回环用例首次部分返回、二次完整返回)
        String seq1 = engine.matchToolMock("query_flight_schedule_tool", "{\"departureDate\":\"2026-12-20\"}");
        Assertions.assertEquals("{\"attempt\": 1}", seq1);

        String seq2 = engine.matchToolMock("query_flight_schedule_tool", "{\"departureDate\":\"2026-12-20\"}");
        Assertions.assertEquals("{\"attempt\": 2}", seq2);

        // 3. 测试严格模式未命中抛出 Mock 缺口异常
        Assertions.assertThrows(BizException.class, () -> {
            engine.matchToolMock("query_flight_schedule_tool", "{\"departureDate\":\"2026-12-25\"}");
        });
        Assertions.assertEquals(1, collector.buildTrace().getTotals().getMockGapsCount());
    }

    /**
     * 测试故障注入器的指定轮次触发逻辑
     */
    @Test
    @DisplayName("测试指定轮次故障注入 (超时/报错)")
    public void testFaultInjector() {
        CaseMockBundleDTO.FaultInjectionRuleDTO f1 = CaseMockBundleDTO.FaultInjectionRuleDTO.builder()
                .target("pref_expert")
                .type("error")
                .onAttempt(1)
                .errorMessage("偏好分析超时异常")
                .build();

        FaultInjector injector = new FaultInjector(List.of(f1));

        // 第 1 次调用触发模拟故障
        BizException ex = Assertions.assertThrows(BizException.class, () -> {
            injector.checkAndTriggerFault("pref_expert");
        });
        Assertions.assertTrue(ex.getMessage().contains("偏好分析超时异常"));

        // 第 2 次调用恢复正常，不再触发
        Assertions.assertDoesNotThrow(() -> {
            injector.checkAndTriggerFault("pref_expert");
        });
    }

    /**
     * 测试固定冻结时间提供者的确定性输出
     */
    @Test
    @DisplayName("测试固定冻结时间提供者")
    public void testFixedTimeProvider() {
        FixedTimeProvider fixed = new FixedTimeProvider("2026-11-07");
        Assertions.assertTrue(fixed.currentIsoString().startsWith("2026-11-07"));
    }
}
