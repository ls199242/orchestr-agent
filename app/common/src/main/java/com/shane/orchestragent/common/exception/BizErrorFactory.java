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

    public BizException agentSystemPromptMissing(String agentName) {
        return new BizException("AGENT_CONFIG_ERROR", "智能体 [" + agentName + "] 未配置系统提示词 (systemPrompt)");
    }

    public BizException agentUserPromptMissing(String agentName) {
        return new BizException("AGENT_CONFIG_ERROR", "智能体 [" + agentName + "] 未配置用户提示词模板 (userPrompt)");
    }

    public BizException toolNotFound(String toolCode) {
        return new BizException("TOOL_NOT_FOUND", "未能找到指定工具配置: " + toolCode);
    }

    public BizException toolEndpointMissing(String toolName) {
        return new BizException("TOOL_ENDPOINT_MISSING", "工具 [" + toolName + "] 未配置调用端点 (endpoint)");
    }

    public BizException toolExecutionError(String toolName, String detail) {
        return new BizException("TOOL_EXECUTION_ERROR", "工具 [" + toolName + "] 执行异常: " + detail);
    }

    public BizException conductorDecisionEmpty() {
        return new BizException("CONDUCTOR_DECISION_EMPTY", "指挥调度器决策异常: 未能指定下一步执行动作或节点");
    }

    public BizException routerDecisionEmpty() {
        return new BizException("ROUTER_DECISION_EMPTY", "路由门禁决策异常: 未能获取有效决策结果");
    }

    public BizException routerReplyEmpty() {
        return new BizException("ROUTER_REPLY_EMPTY", "路由门禁决策为直接答复，但答复内容为空");
    }

    public BizException strategyNotFound(String strategyId) {
        return new BizException("STRATEGY_NOT_FOUND", "未能获取到指定策略配置: " + strategyId);
    }

    public FlowStoppedException strategyFlowStopped() {
        return new FlowStoppedException();
    }
}
