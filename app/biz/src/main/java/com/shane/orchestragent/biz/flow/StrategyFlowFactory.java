package com.shane.orchestragent.biz.flow;

import com.shane.orchestragent.biz.agent.Agent;
import com.shane.orchestragent.biz.agent.flow.*;
import com.shane.orchestragent.biz.context.StrategyContext;
import com.shane.orchestragent.biz.model.agent.LlmConfig;
import com.shane.orchestragent.biz.model.enums.FlowTopologyTypeEnum;
import com.shane.orchestragent.biz.recorder.FlowExecutionRecorder;
import com.shane.orchestragent.biz.service.FlowService;
import com.shane.orchestragent.biz.service.LlmService;
import com.shane.orchestragent.common.enums.AgentTypeEnum;
import com.shane.orchestragent.prompt.service.PromptService;
import com.shane.orchestragent.repository.AgentConfigRepository;
import com.shane.orchestragent.repository.DictRepository;
import com.shane.orchestragent.repository.model.AgentConfigDO;
import com.shane.orchestragent.repository.model.StrategyConfigDO;
import com.shane.orchestragent.repository.model.StrategyWorkerDO;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

/**
 * 策略流工程工厂 (标准 IoC 组装，支持根据拓扑类型动态构建流程)
 * 支持从 DictRepository 与 AgentConfigRepository 动态获取模型参数与自愈重试上限
 *
 * @author Shane
 */
@Component
@Slf4j
public class StrategyFlowFactory {

    private final LlmService llmService;
    private final PromptService promptService;

    private final DictRepository dictRepository;
    private final FlowService flowService;
    private final FlowExecutionRecorder flowExecutionRecorder;
    private AgentConfigRepository agentConfigRepository;

    public StrategyFlowFactory(LlmService llmService, PromptService promptService) {
        this(llmService, promptService, null, null, null, null);
    }

    public StrategyFlowFactory(LlmService llmService,
                               PromptService promptService,
                               FlowService flowService,
                               FlowExecutionRecorder flowExecutionRecorder) {
        this(llmService, promptService, flowService, flowExecutionRecorder, null, null);
    }

    public StrategyFlowFactory(LlmService llmService,
                               PromptService promptService,
                               FlowService flowService,
                               FlowExecutionRecorder flowExecutionRecorder,
                               DictRepository dictRepository) {
        this(llmService, promptService, flowService, flowExecutionRecorder, dictRepository, null);
    }

    @Autowired
    public StrategyFlowFactory(LlmService llmService,
                               PromptService promptService,
                               @Autowired(required = false) FlowService flowService,
                               @Autowired(required = false) FlowExecutionRecorder flowExecutionRecorder,
                               @Autowired(required = false) DictRepository dictRepository,
                               @Autowired(required = false) AgentConfigRepository agentConfigRepository) {
        this.llmService = llmService;
        this.promptService = promptService;
        this.flowService = flowService;
        this.flowExecutionRecorder = flowExecutionRecorder;
        this.dictRepository = dictRepository;
        this.agentConfigRepository = agentConfigRepository;

        if (dictRepository != null) {
            int expireMins = dictRepository.getValue(DictRepository.Keys.KEY_FLOW_INSTANCE_EXPIRE_MINUTES, DictRepository.Defaults.DEFAULT_FLOW_INSTANCE_EXPIRE_MINUTES);
            int maxCap = dictRepository.getValue(DictRepository.Keys.KEY_FLOW_INSTANCE_MAX_CAPACITY, DictRepository.Defaults.DEFAULT_FLOW_INSTANCE_MAX_CAPACITY);
            com.shane.orchestragent.biz.flow.store.FlowStore.configure(expireMins, maxCap);
        }
    }

    public void setAgentConfigRepository(AgentConfigRepository agentConfigRepository) {
        this.agentConfigRepository = agentConfigRepository;
    }

    /**
     * 根据上下文策略配置自动创建并装配 StrategyFlow
     *
     * @param context 运行上下文
     * @return 装配完毕的流程实例
     */
    public StrategyFlow create(StrategyContext context) {
        StrategyConfigDO config = context.getConfig();
        String topologyType = config != null ? config.getFlowTopologyType() : null;
        FlowTopologyTypeEnum topology = FlowTopologyTypeEnum.of(topologyType);

        if (topology == FlowTopologyTypeEnum.MULTIPLE_CHAT) {
            return createMultipleChatFlow(config, context);
        } else {
            return createReActFlow(config, context);
        }
    }

    /**
     * 创建纯 ReAct 策略流程 (直接从 Planner 规划启动循环协同)
     *
     * @param strategyConfig 策略配置实体
     * @param context 策略运行上下文
     * @return ReAct 策略流程实例
     */
    public StrategyFlow createReActFlow(StrategyConfigDO strategyConfig, StrategyContext context) {
        Map<AgentTypeEnum, Agent> systemAgents = buildSystemAgents(strategyConfig);
        int defaultMaxStep = dictRepository != null ? dictRepository.getValue(DictRepository.Keys.KEY_FLOW_MAX_STEP, 20) : 20;
        int maxStep = (strategyConfig != null && strategyConfig.getMaxStep() != null) ? strategyConfig.getMaxStep() : defaultMaxStep;
        int maxEvalRetry = dictRepository != null ? dictRepository.getValue(DictRepository.Keys.KEY_FLOW_EVAL_MAX_RETRY, 2) : 2;
        log.info("[FLOW_FACTORY][BUILD] 创建纯 ReAct 策略流程: flowId={}, maxStep={} (dict保底={}), maxEvalRetry={}, workersCount={}",
                context.getFlowId(), maxStep, defaultMaxStep, maxEvalRetry, (strategyConfig != null && strategyConfig.getWorkers() != null) ? strategyConfig.getWorkers().size() : 0);

        ReActStrategyFlow flow = new ReActStrategyFlow(context, systemAgents, maxStep, maxEvalRetry);
        registerWorkers(flow, strategyConfig);
        decorateFlow(flow);
        return flow;
    }

    /**
     * 创建多轮会话策略流程 (包含 Router 智能体，首轮进行意图识别与分流决策)
     *
     * @param strategyConfig 策略配置实体
     * @param context 策略运行上下文
     * @return 多轮会话流程实例
     */
    public StrategyFlow createMultipleChatFlow(StrategyConfigDO strategyConfig, StrategyContext context) {
        Map<AgentTypeEnum, Agent> systemAgents = buildSystemAgents(strategyConfig);
        int defaultMaxStep = dictRepository != null ? dictRepository.getValue(DictRepository.Keys.KEY_FLOW_MAX_STEP, 20) : 20;
        int maxStep = (strategyConfig != null && strategyConfig.getMaxStep() != null) ? strategyConfig.getMaxStep() : defaultMaxStep;
        log.info("[FLOW_FACTORY][BUILD] 创建 MultipleChat 策略流程: flowId={}, maxStep={} (dict保底={}), workersCount={}",
                context.getFlowId(), maxStep, defaultMaxStep, (strategyConfig != null && strategyConfig.getWorkers() != null) ? strategyConfig.getWorkers().size() : 0);

        MultipleChatStrategyFlow flow = new MultipleChatStrategyFlow(context, systemAgents, maxStep);
        registerWorkers(flow, strategyConfig);
        decorateFlow(flow);
        return flow;
    }

    /**
     * 为流程实例装配生命周期监听器与执行记录器
     *
     * @param flow 待装配流程
     */
    private void decorateFlow(StrategyFlow flow) {
        if (flow instanceof BaseStrategyFlow baseFlow) {
            if (flowService != null) {
                baseFlow.setFlowService(flowService);
            }
            if (flowExecutionRecorder != null) {
                baseFlow.addStateChangeListener(flowExecutionRecorder);
                baseFlow.addAgentEventListener(flowExecutionRecorder);
            }
        }
    }

    private Map<AgentTypeEnum, Agent> buildSystemAgents(StrategyConfigDO strategyConfig) {
        Map<AgentTypeEnum, Agent> map = new HashMap<>();
        String defaultModel = dictRepository != null ? dictRepository.getValue(DictRepository.Keys.KEY_DEFAULT_LLM_MODEL, "gpt-4o") : "gpt-4o";

        map.put(AgentTypeEnum.ROUTER, new RouterAgent(resolveSystemAgentLlmConfig(AgentTypeEnum.ROUTER, defaultModel, 0.1), llmService, promptService));
        map.put(AgentTypeEnum.PLANNER, new PlannerAgent(resolveSystemAgentLlmConfig(AgentTypeEnum.PLANNER, defaultModel, 0.2), llmService, promptService));
        map.put(AgentTypeEnum.CONDUCTOR, new ConductorAgent(resolveSystemAgentLlmConfig(AgentTypeEnum.CONDUCTOR, defaultModel, 0.2), llmService, promptService));
        map.put(AgentTypeEnum.EVALUATOR, new EvaluatorAgent(resolveSystemAgentLlmConfig(AgentTypeEnum.EVALUATOR, defaultModel, 0.1), llmService, promptService));
        map.put(AgentTypeEnum.REPORTER, new ReporterAgent(resolveSystemAgentLlmConfig(AgentTypeEnum.REPORTER, defaultModel, 0.3), llmService, promptService));
        return map;
    }

    private LlmConfig resolveSystemAgentLlmConfig(AgentTypeEnum type, String defaultModel, double fallbackTemp) {
        if (agentConfigRepository != null) {
            AgentConfigDO agentDO = agentConfigRepository.getByName(type.name());
            if (agentDO != null) {
                String model = StringUtils.isNotBlank(agentDO.getModel()) ? agentDO.getModel() : defaultModel;
                Double temp = agentDO.getTemperature() != null ? agentDO.getTemperature() : fallbackTemp;
                Integer maxTokens = agentDO.getMaxTokens();
                return LlmConfig.builder().model(model).temperature(temp).maxTokens(maxTokens).build();
            }
        }
        double defaultSysTemp = dictRepository != null ? dictRepository.getValue(DictRepository.Keys.KEY_DEFAULT_SYSTEM_AGENT_TEMP, fallbackTemp) : fallbackTemp;
        return LlmConfig.builder().model(defaultModel).temperature(defaultSysTemp).build();
    }

    private void registerWorkers(BaseStrategyFlow flow, StrategyConfigDO strategyConfig) {
        if (strategyConfig != null && strategyConfig.getWorkers() != null) {
            String defaultModel = dictRepository != null ? dictRepository.getValue(DictRepository.Keys.KEY_DEFAULT_LLM_MODEL, "gpt-4o") : "gpt-4o";
            double defaultWorkerTemp = dictRepository != null ? dictRepository.getValue(DictRepository.Keys.KEY_DEFAULT_WORKER_AGENT_TEMP, 0.3) : 0.3;

            for (StrategyWorkerDO workerDO : strategyConfig.getWorkers()) {
                LlmConfig workerLlmConfig = resolveWorkerLlmConfig(workerDO, defaultModel, defaultWorkerTemp);
                WorkerAgent worker = new WorkerAgent(workerDO, workerLlmConfig, llmService, promptService);
                flow.addAgent(worker);
            }
        }
        // 默认补充 RAG 智能体，便于上下文知识检索注入
        flow.addAgent(new RagAgent());
    }

    private LlmConfig resolveWorkerLlmConfig(StrategyWorkerDO workerDO, String defaultModel, double defaultTemp) {
        String model = workerDO != null && StringUtils.isNotBlank(workerDO.getModel()) ? workerDO.getModel() : null;
        Double temp = workerDO != null ? workerDO.getTemperature() : null;
        Integer maxTokens = workerDO != null ? workerDO.getMaxTokens() : null;

        if (agentConfigRepository != null && workerDO != null && (model == null || temp == null)) {
            AgentConfigDO agentDO = agentConfigRepository.getByName(workerDO.getName());
            if (agentDO != null) {
                if (model == null && StringUtils.isNotBlank(agentDO.getModel())) {
                    model = agentDO.getModel();
                }
                if (temp == null && agentDO.getTemperature() != null) {
                    temp = agentDO.getTemperature();
                }
                if (maxTokens == null && agentDO.getMaxTokens() != null) {
                    maxTokens = agentDO.getMaxTokens();
                }
            }
        }

        return LlmConfig.builder()
                .model(StringUtils.isNotBlank(model) ? model : defaultModel)
                .temperature(temp != null ? temp : defaultTemp)
                .maxTokens(maxTokens)
                .build();
    }
}
