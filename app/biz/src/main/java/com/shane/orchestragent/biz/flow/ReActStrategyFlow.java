package com.shane.orchestragent.biz.flow;

import com.shane.orchestragent.biz.agent.Agent;
import com.shane.orchestragent.biz.agent.flow.ConductorAgent;
import com.shane.orchestragent.biz.agent.flow.EvaluatorAgent;
import com.shane.orchestragent.biz.agent.flow.PlannerAgent;
import com.shane.orchestragent.biz.agent.flow.RagAgent;
import com.shane.orchestragent.biz.agent.flow.ReporterAgent;
import com.shane.orchestragent.biz.context.StrategyContext;
import com.shane.orchestragent.biz.model.agent.WorkerErrorAgentResult;
import com.shane.orchestragent.common.enums.AgentTypeEnum;
import com.shane.orchestragent.biz.model.flow.ConductorResult;
import com.shane.orchestragent.biz.model.flow.EvaluatorResult;
import com.shane.orchestragent.biz.model.flow.FlowStep;
import com.shane.orchestragent.biz.model.flow.PlanResult;
import com.shane.orchestragent.common.constant.PropertyKeys;
import com.shane.orchestragent.common.exception.BizErrorFactory;
import com.shane.orchestragent.common.exception.BizException;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.Map;

/**
 * ReAct 策略流引擎 (核心创新: 深度整合 Conductor 步进调度与 Evaluator 战略目标核验自愈闭环)
 *
 * @author Shane
 */
public class ReActStrategyFlow extends BaseStrategyFlow {

    /** 最大战略验收失败打回重试次数 (避免无限循环) */
    private final int maxEvalRetry;

    private final PlannerAgent plannerAgent;
    private final ConductorAgent conductorAgent;
    private final EvaluatorAgent evaluatorAgent;
    private final ReporterAgent reporterAgent;

    public ReActStrategyFlow(StrategyContext context, Map<AgentTypeEnum, Agent> systemAgents, int maxStep) {
        this(context, systemAgents, maxStep, 2);
    }

    public ReActStrategyFlow(StrategyContext context, Map<AgentTypeEnum, Agent> systemAgents, int maxStep, int maxEvalRetry) {
        super(context, maxStep);
        this.maxEvalRetry = maxEvalRetry > 0 ? maxEvalRetry : 2;

        this.plannerAgent = (PlannerAgent) systemAgents.get(AgentTypeEnum.PLANNER);
        this.conductorAgent = (ConductorAgent) systemAgents.get(AgentTypeEnum.CONDUCTOR);
        this.evaluatorAgent = (EvaluatorAgent) systemAgents.get(AgentTypeEnum.EVALUATOR);
        this.reporterAgent = (ReporterAgent) systemAgents.get(AgentTypeEnum.REPORTER);

        addAgent(this.plannerAgent);
        addAgent(this.conductorAgent);
        addAgent(this.evaluatorAgent);
        addAgent(this.reporterAgent);
    }

    @Override
    protected String doExecute() throws BizException {
        // 1. 战略规划阶段: 拆解全局目标为有序步骤 (执行前由 checkStep 统一把关停止状态与步数)
        if (!checkStep()) {
            throw BizErrorFactory.getInstance().handleMaxStepLimitExceeded();
        }
        PlanResult planResult = plan();

        int evalRetry = 0;

        // 2. 步进协同与自愈循环
        while (checkStep()) {
            // 指挥调度: 单步审视进度与反馈，判定下一步
            ConductorResult conductorResult = conduct();

            if (conductorResult.isError()) {
                String errorReason = conductorResult.getRequest() != null && conductorResult.getRequest().get("reason") != null
                        ? String.valueOf(conductorResult.getRequest().get("reason"))
                        : "调度器检测到前序步骤或系统异常中断流程";
                log.error("[Flow: {}] Conductor 调度器检测到异常中断流程: {}", getFlowId(), errorReason);
                throw new BizException("CONDUCTOR_RETURN_ERROR", "指挥调度器检测到异常中断流程: " + errorReason);
            }

            // 当指挥官判定流程可结束时，由 Evaluator 进行终局战略目标审计
            if (conductorResult.isFinish()) {
                EvaluatorResult evalResult = evaluate();

                // 2.1 验收通过: 达标放行进入 Reporter 汇总交付
                if (evalResult.isPass()) {
                    log.info("[Flow: {}] Evaluator 验收通过 (得分: {}), 进入最终成果汇编", getFlowId(), evalResult.getScore());
                    return finish();
                }

                // 2.2 验收未通过且处于重试预算内: 触发自愈打回 (Self-Correction Loop)
                if (evalRetry < maxEvalRetry) {
                    evalRetry++;
                    log.warn("[Flow: {}] Evaluator 验收未通过 (第 {} 次自愈重试), 诊断意见: {}",
                            getFlowId(), evalRetry, evalResult.getCritique());

                    // 将批评与整改意见注入上下文，供 Conductor 下一轮调度 Worker 进行针对性数据修补
                    String feedback = evalResult.getCritique() +
                            (StringUtils.isNotEmpty(evalResult.getSuggestedRemedy()) ? " 建议动作: " + evalResult.getSuggestedRemedy() : "");
                    getContext().setEvaluationFeedback(feedback);
                    getContext().setEvaluationRetryCount(evalRetry);
                    continue;
                }

                // 2.3 达到重试上限: 触发优雅降级交付 (携带未达成风险透明告知用户)
                log.error("[Flow: {}] Evaluator 自愈重试达到上限，启动优雅降级交付", getFlowId());
                return finish();
            }

            // 调度 Worker 执行特定工单子任务
            executeWorker(conductorResult);
        }

        throw BizErrorFactory.getInstance().handleMaxStepLimitExceeded();
    }

    private PlanResult plan() throws BizException {
        PlanResult planResult = (PlanResult) executeStep(plannerAgent, PlanResult.class);
        validPlanResult(planResult);
        getContext().setPlanResult(planResult);
        log.info("[Flow: {}][FLOW_PLAN] Planner 规划拆解完成: 共 {} 个工单步骤: {}",
                getFlowId(), planResult.getSteps().size(),
                planResult.getSteps().stream().map(s -> s.getStep() + "." + s.getDescription() + "(" + s.getAgent() + ")").toList());
        return planResult;
    }

    private void validPlanResult(PlanResult planResult) throws BizException {
        if (planResult == null || CollectionUtils.isEmpty(planResult.getSteps())) {
            throw BizErrorFactory.getInstance().plannerReturnEmpty();
        }
        for (FlowStep step : planResult.getSteps()) {
            if (!getAgents().containsKey(step.getAgent())) {
                log.warn("[Plan Validation] 提示: 规划步骤包含外部或动态 Worker: {}", step.getAgent());
            }
        }
    }

    private ConductorResult conduct() throws BizException {
        ConductorResult conductorResult = (ConductorResult) executeStep(conductorAgent, ConductorResult.class);
        if (conductorResult == null || StringUtils.isBlank(conductorResult.getNext())) {
            throw BizErrorFactory.getInstance().conductorDecisionEmpty();
        }
        getContext().setLastConductorResult(conductorResult);
        getContext().setNextAgentRequest(conductorResult.getRequest());
        log.info("[Flow: {}][FLOW_CONDUCT] Conductor 调度决策: next={}, targetAgentRequest='{}'",
                getFlowId(), conductorResult.getNext(), conductorResult.getRequest());
        return conductorResult;
    }

    private void executeWorker(ConductorResult conductorResult) throws BizException {
        String nextAgentName = conductorResult != null ? conductorResult.getNext() : null;
        if (StringUtils.isBlank(nextAgentName)) {
            log.warn("[Flow: {}] Conductor 未指定下一个执行节点，跳过", getFlowId());
            return;
        }
        Agent targetAgent = getAgents().get(nextAgentName);
        if (targetAgent == null) {
            throw BizErrorFactory.getInstance().agentNotFound(nextAgentName);
        }

        // 1. 伴生 RAG 隐式前置调度：若目标为 Worker 且存在对应伴生 RAG 节点，先触发知识检索并压入上下文
        if (targetAgent.getType() == AgentTypeEnum.WORKER) {
            String companionRagName = RagAgent.buildRagName(nextAgentName);
            Agent companionRag = getAgents().get(companionRagName);
            if (companionRag != null) {
                log.info("[Flow: {}][FLOW_RAG_EXEC] 触发专精节点 [{}] 的伴生 RAG [{}] 知识检索...", getFlowId(), nextAgentName, companionRagName);
                executeStep(companionRag, String.class);
            }
        }

        // 2. 调度执行目标节点 (WorkerAgent 或 ToolAgent)
        log.info("[Flow: {}][FLOW_AGENT_EXEC] 调度节点 [{}] (类型: {}) 执行工单...", getFlowId(), nextAgentName, targetAgent.getType());
        String output = (String) executeStep(targetAgent, String.class);
        validStepResult(targetAgent, output);
        getContext().setLastAgentResult(output);
    }

    private void validStepResult(Agent agent, String output) throws BizException {
        if (StringUtils.isBlank(output)) {
            throw BizErrorFactory.getInstance().toolExecutionError(agent.getName(), "节点产出结果为空");
        }
        if (agent.getType() == AgentTypeEnum.WORKER) {
            WorkerErrorAgentResult errorResult = WorkerErrorAgentResult.format(output);
            if (errorResult != null) {
                throw BizErrorFactory.getInstance().workerReturnException(errorResult.getErrorCode(), errorResult.getErrorMessage());
            }
        }
    }

    private EvaluatorResult evaluate() throws BizException {
        EvaluatorResult evalResult = evaluatorAgent.evaluate(getContext());
        if (evalResult == null) {
            throw new BizException("EVALUATOR_PARSE_ERROR", "战略目标审查智能体返回内容为空");
        }
        log.info("[Flow: {}][FLOW_EVALUATE] Evaluator 验收审计: score={}, pass={}, critique='{}', remedy='{}'",
                getFlowId(), evalResult.getScore(), evalResult.isPass(), evalResult.getCritique(), evalResult.getSuggestedRemedy());
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
