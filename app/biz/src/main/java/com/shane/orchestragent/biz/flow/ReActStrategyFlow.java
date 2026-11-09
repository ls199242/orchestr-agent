package com.shane.orchestragent.biz.flow;

import com.shane.orchestragent.biz.agent.Agent;
import com.shane.orchestragent.biz.agent.flow.EvaluatorAgent;
import com.shane.orchestragent.biz.agent.flow.PlannerAgent;
import com.shane.orchestragent.biz.agent.flow.ReporterAgent;
import com.shane.orchestragent.biz.agent.flow.SupervisorAgent;
import com.shane.orchestragent.biz.context.StrategyContext;
import com.shane.orchestragent.biz.flow.dag.DagExecutionState;
import com.shane.orchestragent.biz.model.agent.AgentResult;
import com.shane.orchestragent.biz.model.flow.EvaluatorResult;
import com.shane.orchestragent.biz.model.flow.FlowDagStep;
import com.shane.orchestragent.biz.model.flow.PlanResult;
import com.shane.orchestragent.common.constant.PropertyKeys;
import com.shane.orchestragent.common.enums.AgentTypeEnum;
import com.shane.orchestragent.common.exception.BizErrorFactory;
import com.shane.orchestragent.common.exception.BizException;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * ReAct 策略流引擎 (纯代码 DAG 并发调度与 Evaluator 自愈闭环)
 *
 * @author Shane
 */
public class ReActStrategyFlow extends BaseStrategyFlow {

    /** 最大战略验收失败打回重试次数 (避免无限循环) */
    private final int maxEvalRetry;

    private final PlannerAgent plannerAgent;
    private final SupervisorAgent supervisorAgent;
    private final EvaluatorAgent evaluatorAgent;
    private final ReporterAgent reporterAgent;

    public ReActStrategyFlow(StrategyContext context, Map<AgentTypeEnum, Agent> systemAgents, int maxStep) {
        this(context, systemAgents, maxStep, 2);
    }

    public ReActStrategyFlow(StrategyContext context, Map<AgentTypeEnum, Agent> systemAgents, int maxStep, int maxEvalRetry) {
        super(context, maxStep);
        this.maxEvalRetry = maxEvalRetry > 0 ? maxEvalRetry : 2;

        this.plannerAgent = (PlannerAgent) systemAgents.get(AgentTypeEnum.PLANNER);
        this.supervisorAgent = (SupervisorAgent) systemAgents.get(AgentTypeEnum.SUPERVISOR);
        this.evaluatorAgent = (EvaluatorAgent) systemAgents.get(AgentTypeEnum.EVALUATOR);
        this.reporterAgent = (ReporterAgent) systemAgents.get(AgentTypeEnum.REPORTER);

        addAgent(this.plannerAgent);
        addAgent(this.supervisorAgent);
        addAgent(this.evaluatorAgent);
        addAgent(this.reporterAgent);
    }

    @Override
    protected String doExecute() throws BizException {
        // 1. 战略规划阶段: 拆解全局目标为有向无环依赖图 (DAG)
        PlanResult planResult = plan();

        // 2. 调度执行阶段: 纯代码并发调度驱动 DAG
        DagExecutionState dagState = executeDag(planResult);

        // 3. 验收自愈阶段: 战略目标核验与局部重跑闭环
        evaluateAndSelfCorrect(planResult, dagState);

        // 4. 汇总交付阶段: 汇编最终业务交付物
        return finish();
    }

    /**
     * 战略规划: 拆解目标为 DAG 步骤图
     */
    private PlanResult plan() throws BizException {
        if (!checkStep()) {
            throw BizErrorFactory.getInstance().handleMaxStepLimitExceeded();
        }

        // 评测模式支持: 优先检查是否有外部注入的固定 DAG
        PlanResult pinnedPlan = getPinnedPlanIfPresent();
        if (pinnedPlan != null) {
            return pinnedPlan;
        }

        PlanResult planResult = (PlanResult) executeStep(plannerAgent, PlanResult.class);
        validPlanResult(planResult);
        getContext().setPlanResult(planResult);

        recordPlannerTrace(planResult);

        return planResult;
    }

    /**
     * 评测固定 DAG 检查与 Trace 记录
     */
    private PlanResult getPinnedPlanIfPresent() {
        Object pinned = getContext().getProperty("pinnedDag");
        if (pinned instanceof PlanResult pinnedPlan && CollectionUtils.isNotEmpty(pinnedPlan.getSteps())) {
            log.info("[Flow: {}] 评测模式检测到固定 DAG (pinnedDag)，跳过 Planner 规划", getFlowId());
            getContext().setPlanResult(pinnedPlan);
            if (getContext().getEvalCaseContext() != null && getContext().getEvalCaseContext().getTraceCollector() != null) {
                getContext().getEvalCaseContext().getTraceCollector().recordPlannerAttempt(
                        1, pinnedPlan, true, Collections.emptyList(), 0L, 0, 0
                );
            }
            return pinnedPlan;
        }
        return null;
    }

    /**
     * 记录 Planner 规划产出到 Trace
     */
    private void recordPlannerTrace(PlanResult planResult) {
        if (getContext().getEvalCaseContext() != null && getContext().getEvalCaseContext().getTraceCollector() != null) {
            Number lat = (Number) getContext().getProperty("_lastLatencyMs_PLANNER");
            Number tIn = (Number) getContext().getProperty("_lastTokensIn_PLANNER");
            Number tOut = (Number) getContext().getProperty("_lastTokensOut_PLANNER");
            getContext().getEvalCaseContext().getTraceCollector().recordPlannerAttempt(
                    1,
                    planResult,
                    true,
                    Collections.emptyList(),
                    lat != null ? lat.longValue() : 0L,
                    tIn != null ? tIn.intValue() : 0,
                    tOut != null ? tOut.intValue() : 0
            );
        }
    }

    private void validPlanResult(PlanResult planResult) throws BizException {
        if (planResult == null || CollectionUtils.isEmpty(planResult.getSteps())) {
            throw BizErrorFactory.getInstance().plannerReturnEmpty();
        }
    }

    /**
     * 调度执行: 驱动 DAG 并发调度
     */
    private DagExecutionState executeDag(PlanResult planResult) throws BizException {
        notifyPlanTopology(planResult);
        return supervisorAgent.run(planResult, getContext(), (agent, result) ->
                agentEventListeners.forEach(l -> l.onAfter(this, agent, result))
        );
    }

    /**
     * 发送调度拓扑通知事件给监听器
     */
    private void notifyPlanTopology(PlanResult planResult) {
        StringBuilder planSummary = new StringBuilder();
        planSummary.append("【Supervisor 纯代码并发调度拓扑】\n共包含 ").append(planResult.getSteps().size()).append(" 个 DAG 步骤:\n");
        for (FlowDagStep s : planResult.getSteps()) {
            planSummary.append("• [").append(s.getId()).append("] ").append(s.getAgent())
                    .append(" - 依赖: ").append(s.allDeps().isEmpty() ? "无 (首轮并发)" : s.allDeps())
                    .append(", 目标: ").append(s.getGoal())
                    .append("\n");
        }
        AgentResult topologyResult = new AgentResult(planSummary.toString().trim(), null);
        agentEventListeners.forEach(l -> l.onAfter(this, supervisorAgent, topologyResult));
    }

    /**
     * 验收自愈循环: 核验是否达成目标，未达成且在预算内则局部重跑闭环
     */
    private void evaluateAndSelfCorrect(PlanResult planResult, DagExecutionState dagState) throws BizException {
        int evalRetry = 0;
        while (checkStep()) {
            EvaluatorResult evalResult = evaluate(evalRetry);

            // 验收通过
            if (evalResult.isPass()) {
                log.info("[Flow: {}] 目标验收通过 (得分: {})", getFlowId(), evalResult.getScore());
                return;
            }

            // 验收未通过且处于重试预算内: 触发局部自愈重跑
            if (evalRetry < maxEvalRetry) {
                evalRetry++;
                log.warn("[Flow: {}] 目标验收未通过 (第 {}/{} 次重试): {}, 点名步骤: {}",
                        getFlowId(), evalRetry, maxEvalRetry, evalResult.getCritique(), evalResult.getBlamedStepIds());
                dagState = selfCorrect(planResult, dagState, evalResult, evalRetry);
                continue;
            }

            // 达到重试上限: 优雅降级交付
            log.warn("[Flow: {}] 自愈重试达到上限 ({})，降级交付", getFlowId(), maxEvalRetry);
            return;
        }

        throw BizErrorFactory.getInstance().handleMaxStepLimitExceeded();
    }

    /**
     * 执行战略验收
     */
    private EvaluatorResult evaluate(int evalRetry) throws BizException {
        EvaluatorResult evalResult = (EvaluatorResult) executeStep(evaluatorAgent, EvaluatorResult.class);
        if (evalResult == null) {
            throw new BizException("EVALUATOR_PARSE_ERROR", "战略目标审查智能体返回内容为空");
        }
        getContext().setLastEvaluatorResult(evalResult);

        recordEvaluatorTrace(evalResult, evalRetry);

        return evalResult;
    }

    /**
     * 记录 Evaluator 评估轮次到 Trace
     */
    private void recordEvaluatorTrace(EvaluatorResult evalResult, int evalRetry) {
        if (getContext().getEvalCaseContext() != null && getContext().getEvalCaseContext().getTraceCollector() != null) {
            Number lat = (Number) getContext().getProperty("_lastLatencyMs_EVALUATOR");
            Number tIn = (Number) getContext().getProperty("_lastTokensIn_EVALUATOR");
            Number tOut = (Number) getContext().getProperty("_lastTokensOut_EVALUATOR");
            getContext().getEvalCaseContext().getTraceCollector().recordEvaluatorRound(
                    evalRetry,
                    evalResult.isPass() ? "pass" : "insufficient",
                    evalResult.getScore(),
                    evalResult.getCritique(),
                    evalResult.getBlamedStepIds(),
                    evalResult.getSuggestedRemedy(),
                    lat != null ? lat.longValue() : 0L,
                    tIn != null ? tIn.intValue() : 0,
                    tOut != null ? tOut.intValue() : 0
            );
        }
    }

    /**
     * 触发 DAG 局部重跑
     */
    private DagExecutionState selfCorrect(PlanResult planResult, DagExecutionState dagState,
                                          EvaluatorResult evalResult, int evalRetry) throws BizException {
        String feedback = evalResult.getCritique() +
                (StringUtils.isNotEmpty(evalResult.getSuggestedRemedy()) ? " 建议动作: " + evalResult.getSuggestedRemedy() : "");
        getContext().setEvaluationFeedback(feedback);
        getContext().setEvaluationRetryCount(evalRetry);

        Map<String, String> feedbackMap = buildFeedbackMap(planResult, evalResult, feedback);
        return supervisorAgent.rerun(planResult, dagState, feedbackMap, getContext(), (agent, result) ->
                agentEventListeners.forEach(l -> l.onAfter(this, agent, result))
        );
    }

    /**
     * 构造被点名重跑步骤的反馈字典
     */
    private Map<String, String> buildFeedbackMap(PlanResult planResult, EvaluatorResult evalResult, String feedback) {
        Map<String, String> feedbackMap = new HashMap<>();
        if (CollectionUtils.isNotEmpty(evalResult.getBlamedStepIds())) {
            for (String blamedId : evalResult.getBlamedStepIds()) {
                feedbackMap.put(blamedId, feedback);
            }
        } else {
            planResult.getSteps().forEach(s -> feedbackMap.put(s.getId(), feedback));
        }
        return feedbackMap;
    }

    /**
     * 最终成果汇编交付
     */
    private String finish() throws BizException {
        String output = (String) executeStep(reporterAgent, String.class);
        getContext().setRecommendResult(output);
        getContext().setProperties(PropertyKeys.KEY_RECOMMEND_RESULT, output);
        return output;
    }
}
