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

import java.util.HashMap;
import java.util.Map;

/**
 * ReAct 策略流引擎 (核心升级: 纯代码 DAG 并发调度中枢与 Evaluator 战略目标核验自愈闭环)
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
        // 1. 战略规划阶段: 拆解全局目标为有向无环依赖图 (执行前由 checkStep 统一把关停止状态与步数)
        if (!checkStep()) {
            throw BizErrorFactory.getInstance().handleMaxStepLimitExceeded();
        }
        PlanResult planResult = plan();

        int evalRetry = 0;

        // 2. 纯代码 DAG 并发调度引擎首次全量驱动
        log.info("[Flow: {}][FLOW_DAG_EXEC] 启动纯代码 DAG 并发调度中枢...", getFlowId());

        StringBuilder planSummary = new StringBuilder();
        planSummary.append("【Supervisor 纯代码并发调度拓扑】\n共包含 ").append(planResult.getSteps().size()).append(" 个 DAG 步骤:\n");
        for (FlowDagStep s : planResult.getSteps()) {
            planSummary.append("• [").append(s.getId()).append("] ").append(s.getAgent())
                    .append(" - 依赖: ").append(s.allDeps().isEmpty() ? "无 (首轮并发)" : s.allDeps())
                    .append(", 目标: ").append(s.getGoal())
                    .append("\n");
        }
        agentEventListeners.forEach(l -> l.onAfter(this, supervisorAgent, new AgentResult(planSummary.toString().trim(), null)));

        DagExecutionState dagState = supervisorAgent.run(planResult, getContext(), (agent, result) -> {
            agentEventListeners.forEach(l -> l.onAfter(this, agent, result));
        });

        // 3. 终局战略验收与自愈打回循环
        while (checkStep()) {
            EvaluatorResult evalResult = evaluate();

            // 3.1 验收通过: 达标放行进入 Reporter 汇总交付
            if (evalResult.isPass()) {
                log.info("[Flow: {}] Evaluator 验收通过 (得分: {}), 进入最终成果汇编", getFlowId(), evalResult.getScore());
                return finish();
            }

            // 3.2 验收未通过且处于重试预算内: 触发基于闭包的局部自愈打回 (Self-Correction Loop)
            if (evalRetry < maxEvalRetry) {
                evalRetry++;
                log.warn("[Flow: {}] Evaluator 验收未通过 (第 {} 次自愈重试), 诊断意见: {}, 点名步骤: {}",
                        getFlowId(), evalRetry, evalResult.getCritique(), evalResult.getBlamedStepIds());

                // 将批评与整改意见注入上下文，供重跑节点读取
                String feedback = evalResult.getCritique() +
                        (StringUtils.isNotEmpty(evalResult.getSuggestedRemedy()) ? " 建议动作: " + evalResult.getSuggestedRemedy() : "");
                getContext().setEvaluationFeedback(feedback);
                getContext().setEvaluationRetryCount(evalRetry);

                // 构造针对被点名步骤的反馈映射
                Map<String, String> feedbackMap = new HashMap<>();
                if (CollectionUtils.isNotEmpty(evalResult.getBlamedStepIds())) {
                    for (String blamedId : evalResult.getBlamedStepIds()) {
                        feedbackMap.put(blamedId, feedback);
                    }
                } else {
                    // 若 Evaluator 未明确指出具体节点，默认反馈给全部节点
                    planResult.getSteps().forEach(s -> feedbackMap.put(s.getId(), feedback));
                }

                // 触发 DAG 传递下游闭包局部重跑 (仅重跑失效范围，未受影响的上游节点直接复用)
                dagState = supervisorAgent.rerun(planResult, dagState, feedbackMap, getContext(), (agent, result) -> {
                    agentEventListeners.forEach(l -> l.onAfter(this, agent, result));
                });
                continue;
            }

            // 3.3 达到重试上限: 触发优雅降级交付 (携带未达成风险透明告知用户)
            log.error("[Flow: {}] Evaluator 自愈重试达到上限，启动优雅降级交付", getFlowId());
            return finish();
        }

        throw BizErrorFactory.getInstance().handleMaxStepLimitExceeded();
    }

    private PlanResult plan() throws BizException {
        PlanResult planResult = (PlanResult) executeStep(plannerAgent, PlanResult.class);
        validPlanResult(planResult);
        getContext().setPlanResult(planResult);
        log.info("[Flow: {}][FLOW_PLAN] Planner 规划拆解完成: 共 {} 个 DAG 步骤: {}",
                getFlowId(), planResult.getSteps().size(),
                planResult.getSteps().stream().map(s -> s.getId() + "." + s.getGoal() + "(" + s.getAgent() + ")").toList());
        return planResult;
    }

    private void validPlanResult(PlanResult planResult) throws BizException {
        if (planResult == null || CollectionUtils.isEmpty(planResult.getSteps())) {
            throw BizErrorFactory.getInstance().plannerReturnEmpty();
        }
        for (FlowDagStep step : planResult.getSteps()) {
            if (!getAgents().containsKey(step.getAgent())) {
                log.warn("[Plan Validation] 提示: 规划步骤包含外部或动态 Expert: {}", step.getAgent());
            }
        }
    }

    private EvaluatorResult evaluate() throws BizException {
        EvaluatorResult evalResult = (EvaluatorResult) executeStep(evaluatorAgent, EvaluatorResult.class);
        if (evalResult == null) {
            throw new BizException("EVALUATOR_PARSE_ERROR", "战略目标审查智能体返回内容为空");
        }
        getContext().setLastEvaluatorResult(evalResult);
        log.info("[Flow: {}][FLOW_EVALUATE] Evaluator 验收审计: score={}, pass={}, critique='{}', remedy='{}', blamedSteps={}",
                getFlowId(), evalResult.getScore(), evalResult.isPass(), evalResult.getCritique(),
                evalResult.getSuggestedRemedy(), evalResult.getBlamedStepIds());
        return evalResult;
    }

    private String finish() throws BizException {
        log.info("[Flow: {}][FLOW_FINISH] Reporter 正在汇总最终交付物...", getFlowId());
        String output = (String) executeStep(reporterAgent, String.class);
        getContext().setRecommendResult(output);
        getContext().setProperties(PropertyKeys.KEY_RECOMMEND_RESULT, output);
        log.info("[Flow: {}][FLOW_FINISH] 最终交付物汇编完毕，字数: {}", getFlowId(), output != null ? output.length() : 0);
        return output;
    }
}
