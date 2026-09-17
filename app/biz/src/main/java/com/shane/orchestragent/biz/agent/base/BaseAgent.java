package com.shane.orchestragent.biz.agent.base;

import com.shane.orchestragent.biz.agent.Agent;
import com.shane.orchestragent.biz.context.AgentContext;
import com.shane.orchestragent.common.enums.AgentTypeEnum;
import com.shane.orchestragent.prompt.model.AgentPO;

import com.shane.orchestragent.biz.agent.AgentAdvisor;
import com.shane.orchestragent.common.exception.BizException;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 智能体通用基类
 *
 * @author Shane
 */
public abstract class BaseAgent<CONTEXT extends AgentContext> implements Agent<CONTEXT> {

    protected final String name;
    protected final String description;
    protected final AgentTypeEnum agentType;
    protected final AtomicBoolean stopped = new AtomicBoolean(false);
    protected AgentAdvisor agentAdvisor;

    public BaseAgent(String name, String description, AgentTypeEnum agentType) {
        this.name = name;
        this.description = description;
        this.agentType = agentType;
    }

    @Override
    public void start() {
        this.stopped.set(false);
    }

    @Override
    public void stop() {
        this.stopped.set(true);
    }

    @Override
    public boolean isStopped() {
        return this.stopped.get();
    }

    @Override
    public String getIdentity() {
        return this.name;
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public String getDescription() {
        return this.description;
    }

    @Override
    public AgentTypeEnum getType() {
        return this.agentType;
    }

    public void registerExtension(AgentAdvisor extension) {
        this.agentAdvisor = extension;
    }

    protected Object afterExtension(Object response, CONTEXT context) throws BizException {
        if (this.agentAdvisor != null) {
            return this.agentAdvisor.afterAdvice(this, response, context);
        }
        return response;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <PO extends AgentPO> PO getPO() {
        return (PO) AgentPO.builder()
                .name(name)
                .description(description)
                .agentType(agentType.getCode())
                .build();
    }
}
