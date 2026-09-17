package com.shane.orchestragent.biz.agent;

import com.shane.orchestragent.biz.context.AgentContext;
import com.shane.orchestragent.biz.model.agent.AgentResult;
import com.shane.orchestragent.common.enums.AgentTypeEnum;
import com.shane.orchestragent.common.exception.BizException;
import com.shane.orchestragent.prompt.model.AgentPO;

/**
 * 智能体根接口
 *
 * @author Shane
 */
public interface Agent<CONTEXT extends AgentContext> extends AgentLifecycle {

    /**
     * 运行智能体
     */
    AgentResult execute(CONTEXT context) throws BizException;

    /**
     * 获取当前智能体的 Prompt PO 元数据
     */
    <PO extends AgentPO> PO getPO();

    /**
     * 身份唯一标识
     */
    String getIdentity();

    /**
     * 智能体名称
     */
    String getName();

    /**
     * 智能体职能描述
     */
    String getDescription();

    /**
     * 智能体类型
     */
    AgentTypeEnum getType();

    /**
     * 注册智能体执行切面扩展点
     *
     * @param extension 扩展切面
     */
    void registerExtension(AgentAdvisor extension);
}
