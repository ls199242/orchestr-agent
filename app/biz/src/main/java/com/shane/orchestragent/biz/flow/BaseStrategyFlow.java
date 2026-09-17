package com.shane.orchestragent.biz.flow;

import com.shane.orchestragent.biz.agent.Agent;
import com.shane.orchestragent.biz.context.StrategyContext;
import com.shane.orchestragent.biz.flow.store.FlowStore;
import com.shane.orchestragent.biz.listener.FlowStateChangeListener;
import com.shane.orchestragent.biz.listener.FlowStreamListener;
import com.shane.orchestragent.biz.model.agent.AgentResult;
import com.shane.orchestragent.common.enums.FlowStateEnum;
import com.shane.orchestragent.common.exception.BizException;
import com.shane.orchestragent.common.utils.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 策略编排基类 (消除手动反射 autowireBean，规范状态流转)
 *
 * @author Shane
 */
public abstract class BaseStrategyFlow implements StrategyFlow {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    protected final StrategyContext context;
    protected final int maxStep;
    protected int currentStep = 0;

    protected FlowStateEnum state = FlowStateEnum.INITIAL;
    protected final AtomicBoolean stopped = new AtomicBoolean(false);

    protected final Map<String, Agent> agentsMap = new ConcurrentHashMap<>();
    protected final List<FlowStreamListener> streamListeners = new CopyOnWriteArrayList<>();
    protected final List<FlowStateChangeListener> stateChangeListeners = new CopyOnWriteArrayList<>();
    protected final List<com.shane.orchestragent.biz.listener.FlowAgentEventListener> agentEventListeners = new CopyOnWriteArrayList<>();
    protected com.shane.orchestragent.biz.service.FlowService flowService;

    public BaseStrategyFlow(StrategyContext context, int maxStep) {
        this.context = context;
        this.maxStep = maxStep > 0 ? maxStep : 20;
        FlowStore.add(getFlowId(), this);
    }

    @Override
    public String getFlowId() {
        return context.getFlowId();
    }

    @Override
    public StrategyContext getContext() {
        return context;
    }

    @Override
    public FlowStateEnum getState() {
        return state;
    }

    public void addAgent(Agent agent) {
        if (agent != null) {
            this.agentsMap.put(agent.getName(), agent);
            this.context.registerAgent(agent);
        }
    }

    public Map<String, Agent> getAgents() {
        return agentsMap;
    }

    public void addStreamListener(FlowStreamListener listener) {
        if (listener != null) {
            this.streamListeners.add(listener);
        }
    }

    public void addStateChangeListener(FlowStateChangeListener listener) {
        if (listener != null) {
            this.stateChangeListeners.add(listener);
        }
    }

    @Override
    public void addAgentEventListener(com.shane.orchestragent.biz.listener.FlowAgentEventListener listener) {
        if (listener != null) {
            this.agentEventListeners.add(listener);
        }
    }

    @Override
    public void start() {
        try {
            execute();
        } catch (Exception e) {
            log.error("[Flow: {}] 流程异步启动执行异常", getFlowId(), e);
        }
    }

    @Override
    public void initialize() throws BizException {
        markFlowState(FlowStateEnum.INITIAL);
        for (Agent agent : agentsMap.values()) {
            agent.start();
        }
    }

    public void setFlowService(com.shane.orchestragent.biz.service.FlowService flowService) {
        this.flowService = flowService;
    }

    @Override
    public String execute() throws BizException {
        try {
            initialize();
            markFlowState(FlowStateEnum.RUNNING);
            String result = doExecute();
            context.setRecommendResult(result);
            if (flowService != null) {
                flowService.setResult(getFlowId(), result);
            }
            markFlowState(FlowStateEnum.FINISHED);
            return result;
        } catch (BizException e) {
            markFlowState(FlowStateEnum.ERROR);
            log.error("[Flow: {}] 业务执行异常", getFlowId(), e);
            throw e;
        } catch (Exception e) {
            markFlowState(FlowStateEnum.ERROR);
            log.error("[Flow: {}] 系统未捕获异常", getFlowId(), e);
            throw new BizException(e);
        }
    }

    @Override
    public void stop() {
        this.stopped.set(true);
        markFlowState(FlowStateEnum.STOPPED);
        for (Agent agent : agentsMap.values()) {
            agent.stop();
        }
    }

    protected boolean checkStep() {
        if (stopped.get()) {
            return false;
        }
        this.currentStep++;
        return this.currentStep <= this.maxStep;
    }

    protected void markFlowState(FlowStateEnum newState) {
        FlowStateEnum oldState = this.state;
        this.state = newState;
        log.info("[Flow: {}] 状态流转: {} -> {}", getFlowId(), oldState, newState);
        stateChangeListeners.forEach(l -> l.onStateChange(getFlowId(), oldState, newState));
    }

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

        if (result == null) {
            return null;
        }
        if (returnType == null || returnType == String.class) {
            return result.getOutput();
        }
        return JsonUtils.parseObject(result.getOutput(), returnType);
    }

    protected abstract String doExecute() throws BizException;
}
