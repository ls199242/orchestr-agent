package com.shane.orchestragent.biz.agent.flow;

import com.shane.orchestragent.biz.agent.Agent;
import com.shane.orchestragent.biz.agent.base.BaseAgent;
import com.shane.orchestragent.biz.context.StrategyContext;
import com.shane.orchestragent.biz.flow.dag.DagExecutionState;
import com.shane.orchestragent.biz.flow.dag.DagSupervisorEngine;
import com.shane.orchestragent.biz.model.agent.AgentResult;
import com.shane.orchestragent.biz.model.flow.PlanResult;
import com.shane.orchestragent.biz.model.flow.SupervisorResult;
import com.shane.orchestragent.common.enums.AgentTypeEnum;
import com.shane.orchestragent.common.exception.BizException;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.function.BiConsumer;

/**
 * 纯代码 DAG 并发调度指挥智能体
 * <p>
 * 彻底剥离大模型推理耗时与幻觉风险，基于 Kahn 算法静态校验 DAG，
 * 通过线程池并发驱动就绪节点，机械化解析数据引用，并支持基于传递闭包的局部自愈重跑。
 *
 * @author Shane
 */
public class SupervisorAgent extends BaseAgent<StrategyContext> {

    private final DagSupervisorEngine dagEngine;
    private final ThreadLocal<BiConsumer<Agent, AgentResult>> stepCallbackHolder = new ThreadLocal<>();

    public SupervisorAgent() {
        this(null);
    }

    public SupervisorAgent(ExecutorService executorService) {
        super(AgentTypeEnum.SUPERVISOR.name(), AgentTypeEnum.SUPERVISOR.getDescription(), AgentTypeEnum.SUPERVISOR);
        this.dagEngine = new DagSupervisorEngine(executorService);
    }

    /**
     * 首次调度执行完整 DAG
     *
     * @param plan    全局规划 Plan
     * @param context 策略运行上下文
     * @return 执行黑板状态
     * @throws BizException 若发生依赖缺失、环依赖或执行错误
     */
    public DagExecutionState run(PlanResult plan, StrategyContext context) throws BizException {
        return dagEngine.run(plan, context, stepCallbackHolder.get());
    }

    /**
     * 首次调度执行完整 DAG (携带单步执行结果监听回调)
     *
     * @param plan         全局规划 Plan
     * @param context      策略运行上下文
     * @param stepCallback 单步完成回调
     * @return 执行黑板状态
     * @throws BizException 若发生依赖缺失、环依赖或执行错误
     */
    public DagExecutionState run(PlanResult plan, StrategyContext context,
                                 BiConsumer<Agent, AgentResult> stepCallback) throws BizException {
        stepCallbackHolder.set(stepCallback);
        try {
            return run(plan, context);
        } finally {
            stepCallbackHolder.remove();
        }
    }

    /**
     * Evaluator 打回后的局部自愈重跑
     *
     * @param plan     全局规划 Plan
     * @param state    已有执行黑板
     * @param feedback 针对被点名节点的整改意见
     * @param context  策略运行上下文
     * @return 更新后的执行黑板状态
     * @throws BizException 若重跑失败
     */
    public DagExecutionState rerun(PlanResult plan, DagExecutionState state,
                                   Map<String, String> feedback, StrategyContext context) throws BizException {
        return dagEngine.rerun(plan, state, feedback, context, stepCallbackHolder.get());
    }

    /**
     * Evaluator 打回后的局部自愈重跑 (携带单步执行结果监听回调)
     *
     * @param plan         全局规划 Plan
     * @param state        已有执行黑板
     * @param feedback     针对被点名节点的整改意见
     * @param context      策略运行上下文
     * @param stepCallback 单步完成回调
     * @return 更新后的执行黑板状态
     * @throws BizException 若重跑失败
     */
    public DagExecutionState rerun(PlanResult plan, DagExecutionState state,
                                   Map<String, String> feedback, StrategyContext context,
                                   BiConsumer<Agent, AgentResult> stepCallback) throws BizException {
        stepCallbackHolder.set(stepCallback);
        try {
            return rerun(plan, state, feedback, context);
        } finally {
            stepCallbackHolder.remove();
        }
    }

    @Override
    protected AgentResult doExecute(StrategyContext context) throws BizException {
        PlanResult plan = context.getPlanResult();
        if (plan == null) {
            throw new BizException("PLAN_RESULT_MISSING", "Supervisor 缺少待调度的全局任务 Plan");
        }
        DagExecutionState state = run(plan, context);
        return new AgentResult(state.isAllSucceeded() ? "DAG_SUCCESS" : "DAG_FAILED", null);
    }

    @Override
    public void stop() {
        super.stop();
        dagEngine.shutdown();
    }
}
