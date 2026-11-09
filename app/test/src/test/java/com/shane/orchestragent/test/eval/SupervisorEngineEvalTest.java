package com.shane.orchestragent.test.eval;

import com.shane.orchestragent.biz.agent.Agent;
import com.shane.orchestragent.biz.agent.base.BaseAgent;
import com.shane.orchestragent.biz.context.AgentContext;
import com.shane.orchestragent.biz.context.StrategyContext;
import com.shane.orchestragent.biz.context.impl.DefaultStrategyContext;
import com.shane.orchestragent.biz.eval.context.EvalCaseContext;
import com.shane.orchestragent.biz.eval.model.CaseMockBundleDTO;
import com.shane.orchestragent.biz.eval.model.ExecutionTraceDTO;
import com.shane.orchestragent.biz.flow.dag.DagExecutionState;
import com.shane.orchestragent.biz.flow.dag.DagSupervisorEngine;
import com.shane.orchestragent.biz.model.agent.AgentResult;
import com.shane.orchestragent.biz.model.flow.DagStepStatus;
import com.shane.orchestragent.biz.model.flow.FlowDagStep;
import com.shane.orchestragent.biz.model.flow.OnFailureAction;
import com.shane.orchestragent.biz.model.flow.PlanResult;
import com.shane.orchestragent.common.enums.AgentTypeEnum;
import com.shane.orchestragent.common.exception.BizException;
import com.shane.orchestragent.prompt.model.AgentPO;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.*;

/**
 * DAG 调度引擎评测与容灾机制单元测试
 * 覆盖场景：
 * 1. 链式 DAG 步骤顺利执行及 Trace 调度流水记录
 * 2. 非必要节点失败跳过（OnFailureAction.SKIP）与下游输入缺失标记（[MISSING: stepId]）注入
 *
 * @author Shane
 */
public class SupervisorEngineEvalTest {

    /**
     * 用于仿真 DAG 步骤执行的 Mock Agent
     */
    static class MockAgent extends BaseAgent<AgentContext> {
        private final String output;
        private final boolean fail;

        MockAgent(String name, String output, boolean fail) {
            super(name, name, AgentTypeEnum.EXPERT);
            this.output = output;
            this.fail = fail;
        }

        @Override
        protected AgentResult doExecute(AgentContext context) throws BizException {
            if (fail) {
                throw new BizException("MOCK_FAIL", "执行模拟失败");
            }
            return new AgentResult(output, null);
        }

        @Override
        public <PO extends AgentPO> PO getPO() {
            return null;
        }
    }

    /**
     * 测试两步顺序依赖执行成功，并验证评测 Trace 的事件流水与节点记录是否正常捕获
     *
     * @throws BizException 业务异常
     */
    @Test
    public void testChainExecutionAndTrace() throws BizException {
        DefaultStrategyContext context = new DefaultStrategyContext();
        CaseMockBundleDTO bundle = CaseMockBundleDTO.builder().caseId("c1").build();
        EvalCaseContext evalContext = new EvalCaseContext("c1", "r1", 1, "FREE", bundle);
        context.setEvalCaseContext(evalContext);
        context.setTimeProvider(evalContext.getTimeProvider());

        context.registerAgent(new MockAgent("agent1", "{\"pref\":\"direct\"}", false));
        context.registerAgent(new MockAgent("agent2", "{\"rec\":\"XX201\"}", false));

        FlowDagStep s1 = FlowDagStep.builder().id("s1").agent("agent1").build();
        FlowDagStep s2 = FlowDagStep.builder().id("s2").agent("agent2")
                .dependsOn(List.of("s1"))
                .inputs(Map.of("pref", "$s1.output.pref"))
                .build();

        PlanResult plan = PlanResult.builder().steps(List.of(s1, s2)).build();

        DagSupervisorEngine engine = new DagSupervisorEngine();
        DagExecutionState state = engine.run(plan, context);

        Assertions.assertTrue(state.isAllSucceeded());
        Assertions.assertEquals(DagStepStatus.SUCCEEDED, state.getStatus().get("s1"));
        Assertions.assertEquals(DagStepStatus.SUCCEEDED, state.getStatus().get("s2"));

        ExecutionTraceDTO trace = evalContext.getTraceCollector().buildTrace();
        Assertions.assertFalse(trace.getSchedEvents().isEmpty());
        Assertions.assertEquals(2, trace.getNodes().size());
    }

    /**
     * 测试非必要节点（如偏好分析）失败时策略配置为 SKIP：
     * 1. Supervisor 将该节点置为 SKIPPED 而非中断整体流程
     * 2. 下游依赖该节点输出的表达式能安全解析为 "[MISSING: s1]"，防止下游空指针或因缺失而崩溃
     *
     * @throws BizException 业务异常
     */
    @Test
    public void testOptionalFailureSkipAndMissingMarker() throws BizException {
        DefaultStrategyContext context = new DefaultStrategyContext();
        CaseMockBundleDTO bundle = CaseMockBundleDTO.builder().caseId("c2").build();
        EvalCaseContext evalContext = new EvalCaseContext("c2", "r1", 1, "FREE", bundle);
        context.setEvalCaseContext(evalContext);
        context.setTimeProvider(evalContext.getTimeProvider());

        // s1 失败，但配置了 SKIP
        context.registerAgent(new MockAgent("agent1", "", true));
        context.registerAgent(new MockAgent("agent2", "{\"ok\":true}", false));

        FlowDagStep s1 = FlowDagStep.builder()
                .id("s1")
                .agent("agent1")
                .onFailure(OnFailureAction.SKIP)
                .build();

        FlowDagStep s2 = FlowDagStep.builder()
                .id("s2")
                .agent("agent2")
                .dependsOn(List.of("s1"))
                .inputs(Map.of("pref", "$s1.output"))
                .build();

        PlanResult plan = PlanResult.builder().steps(List.of(s1, s2)).build();

        DagSupervisorEngine engine = new DagSupervisorEngine();
        DagExecutionState state = engine.run(plan, context);

        // s1 为 SKIPPED，s2 正常执行成功
        Assertions.assertEquals(DagStepStatus.SKIPPED, state.getStatus().get("s1"));
        Assertions.assertEquals(DagStepStatus.SUCCEEDED, state.getStatus().get("s2"));

        // 验证 missing marker
        Object resolvedInput = DagSupervisorEngine.resolveExpr("$s1.output", state, context);
        Assertions.assertEquals("[MISSING: s1]", resolvedInput);
    }
}
