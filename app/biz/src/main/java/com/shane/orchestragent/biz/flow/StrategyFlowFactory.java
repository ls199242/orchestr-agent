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
import com.shane.orchestragent.common.exception.BizErrorFactory;
import com.shane.orchestragent.common.exception.BizException;
import com.shane.orchestragent.prompt.service.PromptService;
import com.shane.orchestragent.repository.AgentConfigRepository;
import com.shane.orchestragent.repository.DictRepository;
import com.shane.orchestragent.repository.ToolConfigRepository;
import com.shane.orchestragent.repository.model.AgentConfigDO;
import com.shane.orchestragent.repository.model.StrategyConfigDO;
import com.shane.orchestragent.repository.model.ToolConfigDO;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

/**
 * 策略流工程工厂 (标准 IoC 组装，支持构造期提示词快照隔离与 Fail-Fast 校验)
 * 职责：
 * 1. 构造期从 AgentConfigRepository 动态解析系统 Agent 模型与提示词超参（Fail-Fast 校验）；
 * 2. 从 AgentConfigRepository 动态解析系统 Agent 模型超参与提示词；
 * 3. 动态加载并装配 ToolAgent 工具节点；
 * 4. 动态装配专精 WorkerAgent 及其伴生 RagAgent (WorkerName#RAG)。
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
    private ToolConfigRepository toolConfigRepository;

    public StrategyFlowFactory(LlmService llmService, PromptService promptService) {
        this(llmService, promptService, null, null, null, null, null);
    }

    public StrategyFlowFactory(LlmService llmService,
                               PromptService promptService,
                               FlowService flowService,
                               FlowExecutionRecorder flowExecutionRecorder) {
        this(llmService, promptService, flowService, flowExecutionRecorder, null, null, null);
    }

    public StrategyFlowFactory(LlmService llmService,
                               PromptService promptService,
                               FlowService flowService,
                               FlowExecutionRecorder flowExecutionRecorder,
                               DictRepository dictRepository) {
        this(llmService, promptService, flowService, flowExecutionRecorder, dictRepository, null, null);
    }

    @Autowired
    public StrategyFlowFactory(LlmService llmService,
                               PromptService promptService,
                               @Autowired(required = false) FlowService flowService,
                               @Autowired(required = false) FlowExecutionRecorder flowExecutionRecorder,
                               @Autowired(required = false) DictRepository dictRepository,
                               @Autowired(required = false) AgentConfigRepository agentConfigRepository,
                               @Autowired(required = false) ToolConfigRepository toolConfigRepository) {
        this.llmService = llmService;
        this.promptService = promptService;
        this.flowService = flowService;
        this.flowExecutionRecorder = flowExecutionRecorder;
        this.dictRepository = dictRepository;
        this.agentConfigRepository = agentConfigRepository;
        this.toolConfigRepository = toolConfigRepository;

        if (dictRepository != null) {
            int expireMins = dictRepository.getValue(DictRepository.Keys.KEY_FLOW_INSTANCE_EXPIRE_MINUTES, DictRepository.Defaults.DEFAULT_FLOW_INSTANCE_EXPIRE_MINUTES);
            int maxCap = dictRepository.getValue(DictRepository.Keys.KEY_FLOW_INSTANCE_MAX_CAPACITY, DictRepository.Defaults.DEFAULT_FLOW_INSTANCE_MAX_CAPACITY);
            com.shane.orchestragent.biz.flow.store.FlowStore.configure(expireMins, maxCap);
        }
    }

    public void setAgentConfigRepository(AgentConfigRepository agentConfigRepository) {
        this.agentConfigRepository = agentConfigRepository;
    }

    public void setToolConfigRepository(ToolConfigRepository toolConfigRepository) {
        this.toolConfigRepository = toolConfigRepository;
    }

    /**
     * 根据上下文策略配置自动创建并装配 StrategyFlow
     *
     * @param context 运行上下文
     * @return 装配完毕的流程实例
     */
    public StrategyFlow create(StrategyContext context) throws BizException {
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
    public StrategyFlow createReActFlow(StrategyConfigDO strategyConfig, StrategyContext context) throws BizException {
        Map<AgentTypeEnum, Agent> systemAgents = buildSystemAgents(strategyConfig);
        int defaultMaxStep = dictRepository != null ? dictRepository.getValue(DictRepository.Keys.KEY_FLOW_MAX_STEP, 20) : 20;
        int maxStep = (strategyConfig != null && strategyConfig.getMaxStep() != null) ? strategyConfig.getMaxStep() : defaultMaxStep;
        int maxEvalRetry = dictRepository != null ? dictRepository.getValue(DictRepository.Keys.KEY_FLOW_EVAL_MAX_RETRY, 2) : 2;
        log.info("[FLOW_FACTORY][BUILD] 创建纯 ReAct 策略流程: flowId={}, maxStep={} (dict保底={}), maxEvalRetry={}, workersCount={}, toolsCount={}",
                context.getFlowId(), maxStep, defaultMaxStep, maxEvalRetry,
                getWorkerCount(strategyConfig),
                (strategyConfig != null && strategyConfig.getToolCodes() != null) ? strategyConfig.getToolCodes().size() : 0);

        ReActStrategyFlow flow = new ReActStrategyFlow(context, systemAgents, maxStep, maxEvalRetry);
        registerTools(flow, strategyConfig);
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
    public StrategyFlow createMultipleChatFlow(StrategyConfigDO strategyConfig, StrategyContext context) throws BizException {
        Map<AgentTypeEnum, Agent> systemAgents = buildSystemAgents(strategyConfig);
        int defaultMaxStep = dictRepository != null ? dictRepository.getValue(DictRepository.Keys.KEY_FLOW_MAX_STEP, 20) : 20;
        int maxStep = (strategyConfig != null && strategyConfig.getMaxStep() != null) ? strategyConfig.getMaxStep() : defaultMaxStep;
        log.info("[FLOW_FACTORY][BUILD] 创建 MultipleChat 策略流程: flowId={}, maxStep={} (dict保底={}), workersCount={}, toolsCount={}",
                context.getFlowId(), maxStep, defaultMaxStep,
                getWorkerCount(strategyConfig),
                (strategyConfig != null && strategyConfig.getToolCodes() != null) ? strategyConfig.getToolCodes().size() : 0);

        MultipleChatStrategyFlow flow = new MultipleChatStrategyFlow(context, systemAgents, maxStep);
        registerTools(flow, strategyConfig);
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

    private Map<AgentTypeEnum, Agent> buildSystemAgents(StrategyConfigDO strategyConfig) throws BizException {
        Map<AgentTypeEnum, Agent> map = new HashMap<>();

        map.put(AgentTypeEnum.ROUTER, new RouterAgent(resolveSystemAgentLlmConfig(AgentTypeEnum.ROUTER, 0.1), llmService, promptService));
        map.put(AgentTypeEnum.PLANNER, new PlannerAgent(resolveSystemAgentLlmConfig(AgentTypeEnum.PLANNER, 0.2), llmService, promptService));
        map.put(AgentTypeEnum.CONDUCTOR, new ConductorAgent(resolveSystemAgentLlmConfig(AgentTypeEnum.CONDUCTOR, 0.2), llmService, promptService));
        map.put(AgentTypeEnum.EVALUATOR, new EvaluatorAgent(resolveSystemAgentLlmConfig(AgentTypeEnum.EVALUATOR, 0.1), llmService, promptService));
        map.put(AgentTypeEnum.REPORTER, new ReporterAgent(resolveSystemAgentLlmConfig(AgentTypeEnum.REPORTER, 0.3), llmService, promptService));
        return map;
    }

    private LlmConfig resolveSystemAgentLlmConfig(AgentTypeEnum type, double fallbackTemp) throws BizException {
        String model = null;
        Double temp = null;
        Integer maxTokens = null;
        Long timeout = null;
        String systemPrompt = null;
        String userPrompt = null;

        if (agentConfigRepository != null) {
            AgentConfigDO agentDO = agentConfigRepository.getByName(type.name());
            if (agentDO != null) {
                model = agentDO.getModel();
                temp = agentDO.getTemperature();
                maxTokens = agentDO.getMaxTokens();
                timeout = agentDO.getNodeWaitTime();
                systemPrompt = agentDO.getSystemPrompt();
                userPrompt = agentDO.getUserPrompt();
            }
        }

        // 若配置库未就绪，使用字典配置
        if (StringUtils.isBlank(model) && dictRepository != null) {
            model = dictRepository.getValue(DictRepository.Keys.KEY_DEFAULT_LLM_MODEL, null);
        }
        if (StringUtils.isBlank(model)) {
            throw new BizException("LLM_MODEL_NOT_CONFIGURED", "系统智能体 [" + type.name() + "] 未配置大模型名称 (model)");
        }
        if (temp == null && dictRepository != null) {
            temp = dictRepository.getValue(DictRepository.Keys.KEY_DEFAULT_SYSTEM_AGENT_TEMP, fallbackTemp);
        }
        if (temp == null) {
            temp = fallbackTemp;
        }

        return LlmConfig.builder()
                .model(model)
                .temperature(temp)
                .maxTokens(maxTokens)
                .nodeWaitTime(timeout != null ? timeout : 30000L)
                .systemPrompt(systemPrompt)
                .userPrompt(userPrompt)
                .build();
    }

    private void registerTools(BaseStrategyFlow flow, StrategyConfigDO strategyConfig) throws BizException {
        if (strategyConfig == null || CollectionUtils.isEmpty(strategyConfig.getToolCodes())) {
            return;
        }
        for (String toolCode : strategyConfig.getToolCodes()) {
            ToolConfigDO toolConfig = null;
            if (toolConfigRepository != null) {
                toolConfig = toolConfigRepository.findToolById(toolCode);
                if (toolConfig == null) {
                    toolConfig = toolConfigRepository.findByCode(toolCode);
                }
            }
            if (toolConfig == null) {
                throw BizErrorFactory.getInstance().toolNotFound(toolCode);
            }
            ToolAgent toolAgent = new ToolAgent(toolConfig);
            flow.addAgent(toolAgent);
            log.info("[FLOW_FACTORY] 成功挂载策略工具节点: [{}] ({})", toolAgent.getName(), toolAgent.getDescription());
        }
    }

    private int getWorkerCount(StrategyConfigDO strategyConfig) {
        if (strategyConfig == null || strategyConfig.getWorkerCodes() == null) {
            return 0;
        }
        return strategyConfig.getWorkerCodes().size();
    }

    private void registerWorkers(BaseStrategyFlow flow, StrategyConfigDO strategyConfig) throws BizException {
        if (strategyConfig == null || CollectionUtils.isEmpty(strategyConfig.getWorkerCodes())) {
            return;
        }

        for (String workerCode : strategyConfig.getWorkerCodes()) {
            AgentConfigDO agentConfig = null;
            if (agentConfigRepository != null) {
                agentConfig = agentConfigRepository.getByCode(workerCode);
                if (agentConfig == null) {
                    agentConfig = agentConfigRepository.getByName(workerCode);
                }
            }
            if (agentConfig == null) {
                throw BizErrorFactory.getInstance().agentNotFound(workerCode);
            }

            LlmConfig workerLlmConfig = resolveWorkerLlmConfig(agentConfig);
            WorkerAgent worker = new WorkerAgent(agentConfig, workerLlmConfig, llmService, promptService);
            flow.addAgent(worker);
            log.info("[FLOW_FACTORY] 成功根据 workerCode [{}] 注册专精 Worker 节点: [{}] (模型: {})",
                    workerCode, worker.getName(), workerLlmConfig.getModel());

            // 伴生 RAG 机制：若 Worker 关联了知识库，自动生成对应的伴生 RAG 节点
            if (CollectionUtils.isNotEmpty(agentConfig.getDatasets())) {
                String ragName = RagAgent.buildRagName(agentConfig.getName());
                RagAgent companionRag = new RagAgent(ragName, agentConfig.getDatasets());
                flow.addAgent(companionRag);
                log.info("[FLOW_FACTORY] 为专精 Worker [{}] 注册伴生 RAG 节点 [{}] (关联知识库: {})",
                        agentConfig.getName(), ragName, agentConfig.getDatasets());
            }
        }
    }

    private LlmConfig resolveWorkerLlmConfig(AgentConfigDO agentDO) throws BizException {
        String model = agentDO != null && StringUtils.isNotBlank(agentDO.getModel()) ? agentDO.getModel() : null;
        Double temp = agentDO != null ? agentDO.getTemperature() : null;
        Integer maxTokens = agentDO != null ? agentDO.getMaxTokens() : null;
        Long waitTime = agentDO != null ? agentDO.getNodeWaitTime() : null;
        String systemPrompt = agentDO != null ? agentDO.getSystemPrompt() : null;
        String userPrompt = agentDO != null ? agentDO.getUserPrompt() : null;

        if (StringUtils.isBlank(model) && dictRepository != null) {
            model = dictRepository.getValue(DictRepository.Keys.KEY_DEFAULT_LLM_MODEL, null);
        }
        if (StringUtils.isBlank(model)) {
            throw new BizException("LLM_MODEL_NOT_CONFIGURED", "业务智能体 [" + (agentDO != null ? agentDO.getName() : "unknown") + "] 未配置大模型名称 (model)");
        }
        if (temp == null && dictRepository != null) {
            temp = dictRepository.getValue(DictRepository.Keys.KEY_DEFAULT_WORKER_AGENT_TEMP, 0.3);
        }

        return LlmConfig.builder()
                .model(model)
                .temperature(temp != null ? temp : 0.3)
                .maxTokens(maxTokens)
                .nodeWaitTime(waitTime != null ? waitTime : 30000L)
                .systemPrompt(systemPrompt)
                .userPrompt(userPrompt)
                .build();
    }
}
