package com.shane.orchestragent.biz.agent;

import com.shane.orchestragent.biz.agent.base.BaseAgent;
import com.shane.orchestragent.biz.context.AgentContext;

/**
 * 智能体执行后扩展切面接口
 *
 * @author Shane
 */
public interface AgentAdvisor {

    /**
     * 智能体执行后置处理钩子
     *
     * @param agent 触发的智能体
     * @param agentResult 执行产出结果
     * @param agentContext 上下文
     * @return 增强或转换后的结果
     */
    Object afterAdvice(BaseAgent<?> agent, Object agentResult, AgentContext agentContext);
}
