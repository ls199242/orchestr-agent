package com.shane.orchestragent.biz.flow;

import com.shane.orchestragent.biz.agent.Agent;
import com.shane.orchestragent.biz.context.StrategyContext;
import com.shane.orchestragent.biz.flow.store.FlowStore;
import com.shane.orchestragent.biz.listener.FlowStateChangeListener;
import com.shane.orchestragent.biz.listener.FlowStreamListener;
import com.shane.orchestragent.biz.model.agent.AgentResult;
import com.shane.orchestragent.common.enums.FlowStateEnum;
import com.shane.orchestragent.common.exception.BizErrorFactory;
import com.shane.orchestragent.common.exception.BizException;
import com.shane.orchestragent.common.exception.FlowStoppedException;
import com.shane.orchestragent.common.utils.JsonUtils;
import com.shane.orchestragent.memory.model.ConversationCacheUnit;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 策略编排抽象基类
 * <p>
 * 定义多智能体工作流执行的核心生命周期与调度框架。采用模板方法模式，规范了工作流的以下关键机制：
 * <ul>
 *   <li>生命周期管理：初始化 (INITIAL) -> 运行中 (RUNNING) -> 终态 (FINISHED / STOPPED / ERROR)</li>
 *   <li>状态防篡改：终态（FINISHED / STOPPED / ERROR）一旦达成不可逆，防止并发非法覆盖</li>
 *   <li>步骤步进控制：防死循环与最大步数（{@code maxStep}）超限保护</li>
 *   <li>主动打断与停止传播：支持多线程级联中断当前流程及所有绑定的智能体</li>
 *   <li>智能体调度模板：统一的前后置事件派发（{@code FlowAgentEventListener}）、耗时统计与结果反序列化</li>
 *   <li>会话记忆归档：自动将策略目标与最终产出持久化至记忆上下文（{@code MemoryContext}）</li>
 * </ul>
 *
 * @author Shane
 */
public abstract class BaseStrategyFlow implements StrategyFlow {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    /**
     * 策略执行上下文（持有入参、全局状态、中间数据及产出）
     */
    protected final StrategyContext context;

    /**
     * 最大允许执行步数（兜底防止智能体调度死循环）
     */
    protected final int maxStep;

    /**
     * 当前已执行的步骤计数器
     */
    protected int currentStep = 0;

    /**
     * 当前工作流状态
     */
    protected FlowStateEnum state = FlowStateEnum.INITIAL;

    /**
     * 工作流主动停止/中断原子标记
     */
    protected final AtomicBoolean stopped = new AtomicBoolean(false);

    /**
     * 工作流绑定的智能体字典 (key: agentName, value: Agent)
     */
    protected final Map<String, Agent> agentsMap = new ConcurrentHashMap<>();

    /**
     * 流式输出监听器列表
     */
    protected final List<FlowStreamListener> streamListeners = new CopyOnWriteArrayList<>();

    /**
     * 流程状态流转监听器列表
     */
    protected final List<FlowStateChangeListener> stateChangeListeners = new CopyOnWriteArrayList<>();

    /**
     * 智能体执行生命周期事件监听器列表 (执行前/后/异常)
     */
    protected final List<com.shane.orchestragent.biz.listener.FlowAgentEventListener> agentEventListeners = new CopyOnWriteArrayList<>();

    /**
     * 流程服务（用于外部状态同步与结果沉淀）
     */
    protected com.shane.orchestragent.biz.service.FlowService flowService;

    /**
     * 构造策略编排流程，并自动将当前流程注册至 {@link FlowStore}
     *
     * @param context 策略执行上下文
     * @param maxStep 最大允许执行步数（若小于等于0则默认设为20）
     */
    public BaseStrategyFlow(StrategyContext context, int maxStep) {
        this.context = context;
        this.maxStep = maxStep > 0 ? maxStep : 20;
        FlowStore.add(getFlowId(), this);
    }

    /**
     * 获取流程唯一标识
     *
     * @return 流程 Flow ID
     */
    @Override
    public String getFlowId() {
        return context.getFlowId();
    }

    /**
     * 获取策略执行上下文
     *
     * @return 策略上下文 {@link StrategyContext}
     */
    @Override
    public StrategyContext getContext() {
        return context;
    }

    /**
     * 获取当前工作流状态
     *
     * @return 流程状态枚举 {@link FlowStateEnum}
     */
    @Override
    public FlowStateEnum getState() {
        return state;
    }

    /**
     * 向流程中注册智能体，并同步在上下文中进行登记
     *
     * @param agent 待注册的智能体实例
     */
    public void addAgent(Agent agent) {
        if (agent != null) {
            this.agentsMap.put(agent.getName(), agent);
            this.context.registerAgent(agent);
        }
    }

    /**
     * 获取流程绑定的所有智能体字典映射
     *
     * @return 智能体字典 Map (name -> Agent)
     */
    public Map<String, Agent> getAgents() {
        return agentsMap;
    }

    /**
     * 注册流式输出监听器
     *
     * @param listener 流式监听器
     */
    @Override
    public void addStreamListener(FlowStreamListener listener) {
        if (listener != null) {
            this.streamListeners.add(listener);
        }
    }

    /**
     * 注册流程状态流转监听器
     *
     * @param listener 状态变更监听器
     */
    @Override
    public void addStateChangeListener(FlowStateChangeListener listener) {
        if (listener != null) {
            this.stateChangeListeners.add(listener);
        }
    }

    /**
     * 注册智能体执行事件监听器
     *
     * @param listener 智能体事件监听器
     */
    @Override
    public void addAgentEventListener(com.shane.orchestragent.biz.listener.FlowAgentEventListener listener) {
        if (listener != null) {
            this.agentEventListeners.add(listener);
        }
    }

    /**
     * 初始化流程
     * <p>
     * 校验停止状态、重置流程状态为 {@link FlowStateEnum#INITIAL} 并启动所有绑定的智能体。
     *
     * @throws BizException 若流程已被停止
     */
    @Override
    public void initialize() throws BizException {
        markFlowState(FlowStateEnum.INITIAL);
        for (Agent agent : agentsMap.values()) {
            agent.start();
        }
    }

    /**
     * 注入流程服务实例
     *
     * @param flowService 流程服务
     */
    public void setFlowService(com.shane.orchestragent.biz.service.FlowService flowService) {
        this.flowService = flowService;
    }

    /**
     * 工作流执行模板方法核心入口
     * <p>
     * 编排流程全生命周期流转：
     * 1. 记录开始时间
     * 2. 执行流程初始化 {@link #initialize()}
     * 3. 标记状态为 RUNNING 并调用子类业务实现 {@link #doExecute()}
     * 4. 写入推荐结果至上下文及流程服务
     * 5. 持久化对话历史记忆 {@link #persistMemory(String, String)}
     * 6. 标记状态为 FINISHED
     * 7. 统一捕获异常：区分主动中断 (STOPPED) 与系统异常 (ERROR)，记录耗时与日志
     */
    @Override
    public void execute() {
        long startTime = System.currentTimeMillis();
        context.setProperties("startTime", startTime);

        try {
            initialize();
            markFlowState(FlowStateEnum.RUNNING);
            String result = doExecute();

            context.setRecommendResult(result);
            if (flowService != null) {
                flowService.setResult(getFlowId(), result);
            }
            persistMemory(context.getStrategyTarget(), result);
            markFlowState(FlowStateEnum.FINISHED);

            long costMs = System.currentTimeMillis() - startTime;
            context.setProperties("costMs", costMs);
            log.info("[Flow: {}][FINISH] 工作流执行完成，耗时: {}ms, 最终产出字数: {}",
                    getFlowId(), costMs, result != null ? result.length() : 0);
        } catch (FlowStoppedException e) {
            long costMs = System.currentTimeMillis() - startTime;
            context.setProperties("costMs", costMs);
            context.setProperties("lastError", e);
            markFlowState(FlowStateEnum.STOPPED);
            log.info("[Flow: {}][STOPPED] 流程被主动终止，耗时: {}ms", getFlowId(), costMs);
        } catch (Exception e) {
            long costMs = System.currentTimeMillis() - startTime;
            context.setProperties("costMs", costMs);
            context.setProperties("lastError", e);
            markFlowState(FlowStateEnum.ERROR);
            log.error("[Flow: {}][ERROR] 工作流执行异常，耗时: {}ms: {}", getFlowId(), costMs, e.getMessage(), e);
        }
    }

    /**
     * 会话历史记忆持久化归档
     *
     * @param userMsg      用户输入目标或提示词
     * @param assistantMsg 智能体工作流最终产物
     */
    protected void persistMemory(String userMsg, String assistantMsg) {
        if (context != null && context.getMemoryContext() != null && StringUtils.isNotBlank(context.getSessionId())) {
            if (StringUtils.isNotBlank(userMsg)) {
                context.getMemoryContext().saveCache(ConversationCacheUnit.builder()
                        .sessionId(context.getSessionId())
                        .messageId(UUID.randomUUID().toString())
                        .role("user")
                        .content(userMsg)
                        .createTime(new Date())
                        .build());
            }
            if (StringUtils.isNotBlank(assistantMsg)) {
                context.getMemoryContext().saveCache(ConversationCacheUnit.builder()
                        .sessionId(context.getSessionId())
                        .messageId(UUID.randomUUID().toString())
                        .role("assistant")
                        .content(assistantMsg)
                        .createTime(new Date())
                        .build());
            }
        }
    }

    /**
     * 主动停止工作流
     * <p>
     * 设置 stopped 原子标记，置流态为 STOPPED，并级联通知并停止所有关联的智能体。
     */
    @Override
    public void stop() {
        this.stopped.set(true);
        markFlowState(FlowStateEnum.STOPPED);
        for (Agent agent : agentsMap.values()) {
            agent.stop();
        }
    }

    /**
     * 检查当前流程是否已被主动终止
     *
     * @return true 若已停止，否则 false
     */
    @Override
    public boolean isStopped() {
        return this.stopped.get();
    }

    /**
     * 检查当前执行步骤并步进递增
     * <p>
     * 校验流程是否已被中断，并检测递增后的当前步数是否仍在允许的最大步数范围内。
     *
     * @return true 若当前步数处于合法范围内，false 若已超过最大执行步数
     * @throws BizException 若流程已被主动终止
     */
    protected boolean checkStep() throws BizException {
        if (isStopped()) {
            throw BizErrorFactory.getInstance().strategyFlowStopped();
        }
        this.currentStep++;
        return this.currentStep <= this.maxStep;
    }

    /**
     * 同步流转流程状态
     * <p>
     * 具备状态机终态保护逻辑（STOPPED, FINISHED, ERROR 为不可逆终态），
     * 并在状态变更时自动同步至 FlowService 及触发所有注册的 {@link FlowStateChangeListener}。
     *
     * @param newState 目标流程状态
     */
    protected synchronized void markFlowState(FlowStateEnum newState) {
        if (this.state == FlowStateEnum.STOPPED && newState != FlowStateEnum.STOPPED) {
            log.warn("[Flow: {}] 流程已处于终态 STOPPED，忽略非法状态流转至: {}", getFlowId(), newState);
            return;
        }
        if (this.state == FlowStateEnum.FINISHED && newState != FlowStateEnum.FINISHED) {
            log.warn("[Flow: {}] 流程已处于终态 FINISHED，忽略非法状态流转至: {}", getFlowId(), newState);
            return;
        }
        if (this.state == FlowStateEnum.ERROR && newState != FlowStateEnum.ERROR) {
            log.warn("[Flow: {}] 流程已处于终态 ERROR，忽略非法状态流转至: {}", getFlowId(), newState);
            return;
        }
        FlowStateEnum oldState = this.state;
        this.state = newState;
        if (this.flowService != null) {
            this.flowService.markState(getFlowId(), newState);
        }
        log.info("[Flow: {}] 状态流转: {} -> {}", getFlowId(), oldState, newState);
        stateChangeListeners.forEach(l -> l.onStateChange(getFlowId(), oldState, newState));
    }

    /**
     * 执行单个智能体调度的通用执行步骤
     * <p>
     * 负责单个步骤的生命周期管控：
     * <ul>
     *   <li>非空与中断状态前置拦截</li>
     *   <li>触发 {@link com.shane.orchestragent.biz.listener.FlowAgentEventListener#onBefore} 事件</li>
     *   <li>调用 {@link Agent#execute(com.shane.orchestragent.biz.context.AgentContext)} 执行智能体核心逻辑</li>
     *   <li>异常捕获、主动打断识别与 {@code onError} 事件派发</li>
     *   <li>产出摘要打印与 {@code onAfter} 事件派发</li>
     *   <li>Fail-Fast 产出非空校验与类型反序列化解析（支持 String 与 JavaBean）</li>
     * </ul>
     *
     * @param agent      待执行的智能体
     * @param returnType 期望的返回值类型 Class（若为 null 或 String.class 则直接返回字符串）
     * @return 智能体产出结果对象（经过反序列化或原始字符串）
     * @throws BizException 若智能体为空、流程已停止、智能体执行异常或产出为空
     */
    protected Object executeStep(Agent agent, Class<?> returnType) throws BizException {
        if (agent == null) {
            throw new BizException("AGENT_NOT_FOUND", "执行步骤失败: Agent 为空");
        }
        long stepStart = System.currentTimeMillis();
        log.info("[Flow: {}][STEP_START] 第 {}/{} 步，执行智能体 [{}] (类型: {})",
                getFlowId(), currentStep, maxStep, agent.getName(), agent.getType());
        agentEventListeners.forEach(l -> l.onBefore(this, agent));

        AgentResult result;
        try {
            result = agent.execute(context);
            if (result != null) {
                agentEventListeners.forEach(l -> l.onAfter(this, agent, result));
            }
        } catch (FlowStoppedException e) {
            throw e;
        } catch (Exception e) {
            long stepCost = System.currentTimeMillis() - stepStart;
            log.error("[Flow: {}][STEP_ERROR] 智能体 [{}] 执行失败，耗时: {}ms，异常: {}",
                    getFlowId(), agent.getName(), stepCost, e.getMessage(), e);
            agentEventListeners.forEach(l -> l.onError(this, agent, e));
            if (e instanceof BizException bizEx) {
                throw bizEx;
            }
            throw new BizException("AGENT_EXECUTION_ERROR", "Agent 执行失败: " + e.getMessage(), e);
        }

        long stepCost = System.currentTimeMillis() - stepStart;
        if (result != null) {
            String outputPreview = result.getOutput();
            if (outputPreview != null && outputPreview.length() > 200) {
                outputPreview = outputPreview.substring(0, 200) + "...(共" + outputPreview.length() + "字)";
            }
            log.info("[Flow: {}][STEP_END] 智能体 [{}] 执行完成，耗时: {}ms，产出摘要: {}",
                    getFlowId(), agent.getName(), stepCost, outputPreview);
        }

        if (result == null || StringUtils.isBlank(result.getOutput())) {
            throw new BizException("AGENT_OUTPUT_EMPTY", "智能体 [" + agent.getName() + "] 产出为空");
        }
        if (returnType == null || returnType == String.class) {
            return result.getOutput();
        }
        return JsonUtils.parseObject(result.getOutput(), returnType);
    }

    /**
     * 执行具体策略编排逻辑的抽象方法
     * <p>
     * 由具体的策略工作流实现类（如 ConductorEvaluatorFlow 等）提供多智能体协同流转的具体编排实现。
     *
     * @return 流程最终产出的字符串结果
     * @throws BizException 若流程执行失败
     */
    protected abstract String doExecute() throws BizException;
}
