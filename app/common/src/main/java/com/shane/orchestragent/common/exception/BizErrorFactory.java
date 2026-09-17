package com.shane.orchestragent.common.exception;

/**
 * 业务错误工厂
 *
 * @author Shane
 */
public class BizErrorFactory {

    public static final String STRATEGY_NOT_FOUND = "STRATEGY_NOT_FOUND";

    private static final BizErrorFactory INSTANCE = new BizErrorFactory();

    private BizErrorFactory() {
    }

    public static BizErrorFactory getInstance() {
        return INSTANCE;
    }

    public BizException plannerReturnEmpty() {
        return new BizException("PLANNER_RETURN_EMPTY", "规划器生成的执行步骤为空");
    }

    public BizException plannerReturnAgentEmpty(String agentName) {
        return new BizException("PLANNER_AGENT_NOT_FOUND", "规划器返回的智能体不存在: " + agentName);
    }

    public BizException plannerTargetNotFound() {
        return new BizException("PLANNER_TARGET_NOT_FOUND", "缺少战略目标输入参数");
    }

    public BizException conductorReturnError() {
        return new BizException("CONDUCTOR_RETURN_ERROR", "指挥调度器状态异常或返回ERROR");
    }

    public BizException workerReturnException(String code, String msg) {
        return new BizException("WORKER_EXECUTION_ERROR", "工作智能体执行异常: [" + code + "] " + msg);
    }

    public BizException evaluatorReturnError(String msg) {
        return new BizException("EVALUATOR_AUDIT_FAILED", "战略验收未通过: " + msg);
    }

    public BizException handleMaxStepLimitExceeded() {
        return new BizException("MAX_STEP_LIMIT_EXCEEDED", "流程超过最大执行步长限制");
    }

    public BizException llmAgentTimeoutOrCancel() {
        return new BizException("LLM_TIMEOUT_OR_CANCELLED", "大模型调用超时或已被主动取消");
    }

    public BizException agentNotFound(String name) {
        return new BizException("AGENT_NOT_FOUND", "未能找到指定名称的智能体: " + name);
    }

    public BizException strategyNotFound(String strategyId) {
        return new BizException("STRATEGY_NOT_FOUND", "未能获取到指定策略配置: " + strategyId);
    }
}
