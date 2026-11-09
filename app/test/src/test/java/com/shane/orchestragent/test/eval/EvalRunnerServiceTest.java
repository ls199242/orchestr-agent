package com.shane.orchestragent.test.eval;

import com.shane.orchestragent.biz.eval.client.EvalClient;
import com.shane.orchestragent.biz.eval.model.CaseMockBundleDTO;
import com.shane.orchestragent.biz.eval.model.EvalRunRequestDTO;
import com.shane.orchestragent.biz.eval.model.ExecutionTraceDTO;
import com.shane.orchestragent.biz.eval.service.EvalRunnerService;
import com.shane.orchestragent.biz.eval.trace.TraceRecorder;
import com.shane.orchestragent.biz.flow.StrategyFlow;
import com.shane.orchestragent.biz.flow.StrategyFlowFactory;
import com.shane.orchestragent.biz.model.flow.FlowDagStep;
import com.shane.orchestragent.biz.model.flow.PlanResult;
import com.shane.orchestragent.common.exception.BizException;
import com.shane.orchestragent.repository.AgentConfigRepository;
import com.shane.orchestragent.repository.StrategyConfigRepository;
import com.shane.orchestragent.repository.model.AgentConfigDO;
import com.shane.orchestragent.repository.model.StrategyConfigDO;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 评测执行服务单元测试
 * 验证固定 DAG 模式（PINNED_PLAN）下评测用例的组装、上下文隔离与执行流程，以及 TraceRecorder 异步上报联动
 *
 * @author Shane
 */
public class EvalRunnerServiceTest {

    /**
     * 测试在 PINNED_PLAN 模式下，直接使用指定的固定 DAG 计划注入执行上下文并构建出 Trace 数据，并异步上报至外部 eval 系统
     *
     * @throws BizException 业务异常
     */
    @Test
    @DisplayName("测试评测用例执行完毕后，TraceCollector 产出 Trace 并异步触发 TraceRecorder 上报联动")
    public void testRunCaseWithPinnedDagAndTraceRecorder() throws BizException {
        EvalClient mockEvalClient = Mockito.mock(EvalClient.class);
        StrategyConfigRepository strategyRepo = Mockito.mock(StrategyConfigRepository.class);
        AgentConfigRepository agentRepo = Mockito.mock(AgentConfigRepository.class);
        StrategyFlowFactory flowFactory = Mockito.mock(StrategyFlowFactory.class);
        TraceRecorder mockTraceRecorder = Mockito.mock(TraceRecorder.class);

        Mockito.when(mockTraceRecorder.recordTraceAsync(Mockito.any()))
                .thenReturn(CompletableFuture.completedFuture(Map.of("saved", true)));

        StrategyFlow mockFlow = Mockito.mock(StrategyFlow.class);
        Mockito.when(flowFactory.createReActFlow(Mockito.any(), Mockito.any())).thenReturn(mockFlow);

        CaseMockBundleDTO bundle = CaseMockBundleDTO.builder()
                .caseId("flight_001")
                .session(CaseMockBundleDTO.SessionConfig.builder().userId("U001").frozenTime("2026-11-07").build())
                .toolMocks(Map.of())
                .build();
        Mockito.when(mockEvalClient.getCaseBundle("flight_001", "run_1", 1)).thenReturn(bundle);

        StrategyConfigDO strat = StrategyConfigDO.builder()
                .code("flight_nearby_recommend_strategy")
                .name("航班推荐")
                .flowTopologyType("REACT")
                .maxStep(10)
                .build();
        Mockito.when(strategyRepo.getByCode("flight_nearby_recommend_strategy")).thenReturn(strat);

        // 创建被测服务
        EvalRunnerService runner = new EvalRunnerService(mockEvalClient, flowFactory, strategyRepo, agentRepo, mockTraceRecorder);

        // 使用固定 DAG
        FlowDagStep s1 = FlowDagStep.builder().id("s1").agent("pref_expert").build();
        PlanResult pinned = PlanResult.builder().steps(List.of(s1)).build();

        EvalRunRequestDTO req = EvalRunRequestDTO.builder()
                .caseId("flight_001")
                .runId("run_1")
                .repeatNo(1)
                .mode("PINNED_PLAN")
                .strategyCode("flight_nearby_recommend_strategy")
                .pinnedDag(pinned)
                .build();

        ExecutionTraceDTO trace = runner.runCase(req);

        Assertions.assertNotNull(trace);
        Assertions.assertEquals("flight_001", trace.getCaseId());
        Assertions.assertEquals("run_1", trace.getRunId());
        Assertions.assertEquals("completed", trace.getFinalStatus());
        Assertions.assertEquals("test", trace.getEnv());
        Assertions.assertEquals("EVAL", trace.getSource());

        // 核心验证：验证 runCase 执行完毕后，异步触发了 traceRecorder.recordTraceAsync 调用且传递了正确的 trace
        Mockito.verify(mockTraceRecorder, Mockito.times(1)).recordTraceAsync(Mockito.argThat(t ->
                "flight_001".equals(t.getCaseId()) && "run_1".equals(t.getRunId())
        ));
    }
}
