package com.shane.orchestragent.biz.agent.base;

import com.shane.orchestragent.biz.agent.Agent;
import com.shane.orchestragent.biz.context.AgentContext;
import com.shane.orchestragent.common.enums.AgentTypeEnum;
import com.shane.orchestragent.prompt.model.AgentPO;

import com.shane.orchestragent.biz.agent.AgentAdvisor;
import com.shane.orchestragent.biz.model.agent.AgentResult;
import com.shane.orchestragent.common.exception.BizErrorFactory;
import com.shane.orchestragent.common.exception.BizException;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 智能体通用抽象基类
 * <p>
 * 定义所有智能体（包括系统智能体和业务专精智能体）的基础属性与生命周期方法。
 * 封装了智能体状态控制（启动、停止、打断检测）、身份标识、元数据获取以及基于 {@link AgentAdvisor} 的后置增强扩展机制。
 *
 * @param <CONTEXT> 智能体执行上下文泛型，必须继承自 {@link AgentContext}
 * @author Shane
 */
public abstract class BaseAgent<CONTEXT extends AgentContext> implements Agent<CONTEXT> {

    /**
     * 智能体唯一名称/标识
     */
    protected final String name;

    /**
     * 智能体职责与功能描述
     */
    protected final String description;

    /**
     * 智能体类型枚举
     */
    protected final AgentTypeEnum agentType;

    /**
     * 运行状态标记，true 表示已被主动停止/中断
     */
    protected final AtomicBoolean stopped = new AtomicBoolean(false);

    /**
     * 智能体切面/后置增强器，用于在执行完成后对产出进行校验、清洗或加工
     */
    protected AgentAdvisor agentAdvisor;

    /**
     * 构造基础智能体
     *
     * @param name        智能体名称
     * @param description 智能体描述
     * @param agentType   智能体类型
     */
    public BaseAgent(String name, String description, AgentTypeEnum agentType) {
        this.name = name;
        this.description = description;
        this.agentType = agentType;
    }

    /**
     * 启动智能体，重置停止状态
     */
    @Override
    public void start() {
        this.stopped.set(false);
    }

    /**
     * 停止智能体，设置停止标记
     */
    @Override
    public void stop() {
        this.stopped.set(true);
    }

    /**
     * 检查当前智能体是否已被停止
     *
     * @return true 若处于停止状态，否则 false
     */
    @Override
    public boolean isStopped() {
        return this.stopped.get();
    }

    /**
     * 智能体执行模板方法核心入口（Agent 级别的全局唯一停止检查点）
     * <p>
     * 统一校验停止状态，若未停止则委托具体子类执行核心逻辑 {@link #doExecute(AgentContext)}。
     *
     * @param context 执行上下文
     * @return 智能体执行产物结果 {@link AgentResult}
     * @throws BizException 若智能体已被主动停止或执行异常
     */
    @Override
    public AgentResult execute(CONTEXT context) throws BizException {
        if (isStopped()) {
            throw BizErrorFactory.getInstance().strategyFlowStopped();
        }
        return doExecute(context);
    }

    /**
     * 智能体核心业务执行逻辑（由具体子类实现，内部无需再重复做停止状态检查）
     *
     * @param context 执行上下文
     * @return 智能体执行结果
     * @throws BizException 若执行异常
     */
    protected abstract AgentResult doExecute(CONTEXT context) throws BizException;

    /**
     * 获取智能体唯一标识（默认与名称一致）
     *
     * @return 智能体唯一标识
     */
    @Override
    public String getIdentity() {
        return this.name;
    }

    /**
     * 获取智能体名称
     *
     * @return 智能体名称
     */
    @Override
    public String getName() {
        return this.name;
    }

    /**
     * 获取智能体职责描述
     *
     * @return 智能体描述
     */
    @Override
    public String getDescription() {
        return this.description;
    }

    /**
     * 获取智能体类型
     *
     * @return 智能体类型枚举
     */
    @Override
    public AgentTypeEnum getType() {
        return this.agentType;
    }

    /**
     * 注册切面增强通知器
     *
     * @param extension 切面增强器
     */
    public void registerExtension(AgentAdvisor extension) {
        this.agentAdvisor = extension;
    }

    /**
     * 执行切面后置增强
     * <p>
     * 若已注册 {@link AgentAdvisor}，则委托其执行 {@code afterAdvice}；否则原样返回。
     *
     * @param response 智能体原始执行响应
     * @param context  当前执行上下文
     * @return 经过增强处理后的响应对象
     * @throws BizException 若切面校验失败或执行异常
     */
    protected Object afterExtension(Object response, CONTEXT context) throws BizException {
        if (this.agentAdvisor != null) {
            return this.agentAdvisor.afterAdvice(this, response, context);
        }
        return response;
    }

    /**
     * 获取智能体持久化/传输对象 (AgentPO)
     *
     * @param <PO> AgentPO 子类型
     * @return 构建的 AgentPO 对象
     */
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
