package com.shane.orchestragent.biz.context;

import com.shane.orchestragent.biz.agent.Agent;
import com.shane.orchestragent.biz.model.flow.ConductorResult;
import com.shane.orchestragent.biz.model.flow.EvaluatorResult;
import com.shane.orchestragent.biz.model.flow.FlowAgentMessage;
import com.shane.orchestragent.biz.model.flow.PlanResult;
import com.shane.orchestragent.biz.model.request.RecommendRequestVO;
import com.shane.orchestragent.memory.context.MemoryContext;
import com.shane.orchestragent.repository.model.StrategyConfigDO;

import java.util.List;
import java.util.Map;

/**
 * 策略编排运行全生命周期上下文接口
 * 继承 AgentContext 基础上下文，扩展流程 ID、会话 ID、用户 ID 及全流程协作状态。
 *
 * @author Shane
 */
public interface StrategyContext extends AgentContext {

    /**
     * 获取流程运行唯一标识
     */
    String getFlowId();

    /**
     * 获取当前会话 ID
     */
    String getSessionId();

    /**
     * 获取业务调用用户 ID
     */
    String getUserId();

    /**
     * 设置业务调用用户 ID
     */
    void setUserId(String userId);

    /**
     * 获取原始推荐请求参数
     */
    RecommendRequestVO getRequest();

    /**
     * 获取策略配置数据
     */
    StrategyConfigDO getConfig();

    /**
     * 获取策略纳管的所有智能体实例映射 (name -> Agent)
     */
    Map<String, Agent> getAgents();

    /**
     * 注册智能体到上下文
     */
    void registerAgent(Agent agent);

    /**
     * 获取流程执行全链路消息历史记录
     */
    List<FlowAgentMessage> getChatMessages();

    /**
     * 追加流转消息/思考记录
     */
    void addChatMessage(FlowAgentMessage message);

    /**
     * 获取规划结果
     */
    PlanResult getPlanResult();

    /**
     * 设置规划结果
     */
    void setPlanResult(PlanResult planResult);

    /**
     * 获取最近一次协调指挥结果
     */
    ConductorResult getLastConductorResult();

    /**
     * 设置最近一次协调指挥结果
     */
    void setLastConductorResult(ConductorResult result);

    /**
     * 获取最近一次质检评估结果
     */
    EvaluatorResult getLastEvaluatorResult();

    /**
     * 设置最近一次质检评估结果
     */
    void setLastEvaluatorResult(EvaluatorResult result);

    /**
     * 获取评估反馈详情
     */
    String getEvaluationFeedback();

    /**
     * 设置评估反馈详情
     */
    void setEvaluationFeedback(String feedback);

    /**
     * 获取质检自愈重试次数
     */
    int getEvaluationRetryCount();

    /**
     * 设置质检自愈重试次数
     */
    void setEvaluationRetryCount(int count);

    /**
     * 获取策略最终推荐结果
     */
    String getRecommendResult();

    /**
     * 设置策略最终推荐结果
     */
    void setRecommendResult(String result);

    /**
     * 获取上一个智能体节点的执行输出
     */
    String getLastAgentResult();

    /**
     * 设置上一个智能体节点的执行输出
     */
    void setLastAgentResult(String result);

    /**
     * 获取传递给下一个智能体节点的入参
     */
    Map<String, Object> getNextAgentRequest();

    /**
     * 设置传递给下一个智能体节点的入参
     */
    void setNextAgentRequest(Map<String, Object> nextRequest);

    /**
     * 获取多轮/长期会话记忆上下文
     */
    MemoryContext getMemoryContext();

    /**
     * 设置多轮/长期会话记忆上下文
     */
    void setMemoryContext(MemoryContext memoryContext);

    /**
     * 获取用户诉求/策略目标
     */
    String getStrategyTarget();
}
