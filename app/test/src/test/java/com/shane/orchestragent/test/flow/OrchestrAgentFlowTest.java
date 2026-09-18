package com.shane.orchestragent.test.flow;

import com.shane.orchestragent.biz.agent.Agent;
import com.shane.orchestragent.biz.agent.flow.*;
import com.shane.orchestragent.biz.context.StrategyContext;
import com.shane.orchestragent.biz.context.impl.DefaultStrategyContext;
import com.shane.orchestragent.biz.flow.MultipleChatStrategyFlow;
import com.shane.orchestragent.biz.flow.ReActStrategyFlow;
import com.shane.orchestragent.biz.flow.StrategyFlowFactory;
import com.shane.orchestragent.biz.manager.AgentManager;
import com.shane.orchestragent.biz.manager.impl.AgentManagerImpl;
import com.shane.orchestragent.biz.model.agent.AgentResult;
import com.shane.orchestragent.biz.model.agent.LlmConfig;
import com.shane.orchestragent.biz.model.flow.FlowAgentMessage;
import com.shane.orchestragent.biz.model.request.RecommendRequestVO;
import com.shane.orchestragent.biz.model.response.RecommendResponseVO;
import com.shane.orchestragent.biz.recorder.impl.FlowExecutionRecorderImpl;
import com.shane.orchestragent.biz.service.FlowCacheService;
import com.shane.orchestragent.biz.service.FlowService;
import com.shane.orchestragent.biz.service.LlmService;
import com.shane.orchestragent.biz.service.impl.FlowCacheServiceImpl;
import com.shane.orchestragent.biz.service.impl.FlowServiceImpl;
import com.shane.orchestragent.biz.service.impl.LlmServiceImpl;
import com.shane.orchestragent.common.constant.PropertyKeys;
import com.shane.orchestragent.common.enums.AgentTypeEnum;
import com.shane.orchestragent.common.exception.BizException;
import com.shane.orchestragent.integration.llm.LlmClient;
import com.shane.orchestragent.test.support.MockLlmClient;
import com.shane.orchestragent.integration.llm.model.ChatMessageDTO;
import com.shane.orchestragent.prompt.render.TemplateRender;
import com.shane.orchestragent.prompt.service.PromptService;
import com.shane.orchestragent.prompt.service.impl.PromptServiceImpl;
import com.shane.orchestragent.repository.client.AgentConfigApiClient;
import com.shane.orchestragent.repository.impl.AgentConfigRepositoryImpl;
import com.shane.orchestragent.repository.impl.StrategyConfigRepositoryImpl;
import com.shane.orchestragent.repository.impl.ToolConfigRepositoryImpl;
import com.shane.orchestragent.repository.model.AgentConfigDO;
import com.shane.orchestragent.repository.model.StrategyConfigDO;
import com.shane.orchestragent.repository.model.ToolConfigDO;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * OrchestrAgent 全链路协同与自愈闭环核心测试用例
 * 覆盖：Agent 提示词纯配置驱动、Fail-Fast 严格阻断、伴生 RAG 与 Tool 调度
 *
 * @author Shane
 */
public class OrchestrAgentFlowTest {

    private LlmClient llmClient;
    private LlmService llmService;
    private PromptService promptService;
    private LlmConfig defaultConfig;
    private LlmConfig plannerConfig;
    private LlmConfig conductorConfig;
    private LlmConfig evaluatorConfig;
    private LlmConfig reporterConfig;
    private LlmConfig routerConfig;
    private AgentConfigRepositoryImpl agentRepo;

    @BeforeEach
    public void setup() throws Exception {
        this.llmClient = new MockLlmClient();
        this.llmService = new LlmServiceImpl(llmClient);
        this.promptService = new PromptServiceImpl(new TemplateRender());

        this.plannerConfig = LlmConfig.builder()
                .model("gpt-4o")
                .nodeWaitTime(5000L)
                .systemPrompt("# PlannerAgent\n请分析目标，输出规划步骤: {\"steps\": [{\"step\": 1, \"agent\": \"search_worker\", \"description\": \"查询\"}]}")
                .userPrompt("目标: ${strategy_target}")
                .build();

        this.conductorConfig = LlmConfig.builder()
                .model("gpt-4o")
                .nodeWaitTime(5000L)
                .systemPrompt("# ConductorAgent\n调度下一步。输出: {\"next\": \"FINISH\", \"request\": {}}")
                .userPrompt("默认调度指令")
                .build();

        this.evaluatorConfig = LlmConfig.builder()
                .model("gpt-4o")
                .nodeWaitTime(5000L)
                .systemPrompt("# EvaluatorAgent\n验收输出: {\"pass\": true, \"score\": 100, \"critique\": \"\", \"suggestedRemedy\": \"\"}")
                .userPrompt("【目标】: ${strategy_target}\n【执行结果汇总】: ${allAgentOutputs}")
                .build();

        this.reporterConfig = LlmConfig.builder()
                .model("gpt-4o")
                .nodeWaitTime(5000L)
                .systemPrompt("# ReporterAgent\n汇总结果")
                .userPrompt("请根据执行总结输出最终回复:\n${flowExecutionSummary}")
                .build();

        this.routerConfig = LlmConfig.builder()
                .model("gpt-4o")
                .nodeWaitTime(5000L)
                .systemPrompt("# RouterAgent\n分析意图: {\"handoffToPlanner\": true, \"reply\": \"\"}")
                .userPrompt("用户诉求: ${strategy_target}")
                .build();

        this.defaultConfig = LlmConfig.builder()
                .model("gpt-4o")
                .nodeWaitTime(5000L)
                .systemPrompt("# Default System Prompt")
                .userPrompt("用户请求: ${strategy_target}")
                .build();

        this.agentRepo = new AgentConfigRepositoryImpl();
        List<AgentConfigDO> systemAgents = List.of(
                AgentConfigDO.builder().name("PLANNER").code("PLANNER").model("gpt-4o").systemPrompt(plannerConfig.getSystemPrompt()).userPrompt(plannerConfig.getUserPrompt()).build(),
                AgentConfigDO.builder().name("CONDUCTOR").code("CONDUCTOR").model("gpt-4o").systemPrompt(conductorConfig.getSystemPrompt()).userPrompt(conductorConfig.getUserPrompt()).build(),
                AgentConfigDO.builder().name("EVALUATOR").code("EVALUATOR").model("gpt-4o").systemPrompt(evaluatorConfig.getSystemPrompt()).userPrompt(evaluatorConfig.getUserPrompt()).build(),
                AgentConfigDO.builder().name("REPORTER").code("REPORTER").model("gpt-4o").systemPrompt(reporterConfig.getSystemPrompt()).userPrompt(reporterConfig.getUserPrompt()).build(),
                AgentConfigDO.builder().name("ROUTER").code("ROUTER").model("gpt-4o").systemPrompt(routerConfig.getSystemPrompt()).userPrompt(routerConfig.getUserPrompt()).build(),
                AgentConfigDO.builder().name("CHAT_SUMMARY").code("CHAT_SUMMARY").model("gpt-4o").systemPrompt("# ChatSummaryAgent\n摘要压缩").build(),
                AgentConfigDO.builder().name("search_worker").code("search_worker").agentType("WORKER").model("gpt-4o").systemPrompt("检索航班信息").build(),
                AgentConfigDO.builder().name("expert_ticket").code("expert_ticket").agentType("WORKER").model("gpt-4o").systemPrompt("负责客规审查").datasets(List.of("kb_flight_rules")).build()
        );
        org.springframework.test.util.ReflectionTestUtils.setField(agentRepo, "agentConfigApiClient", (AgentConfigApiClient) () -> systemAgents);
        agentRepo.afterPropertiesSet();
    }

    @Test
    @DisplayName("测试正常全流程: Planner -> Conductor -> Worker -> Evaluator(通过) -> Reporter")
    public void testFullFlowSuccess() throws BizException {
        StrategyContext context = new DefaultStrategyContext();
        context.setProperties(PropertyKeys.KEY_STRATEGY_TARGET, "查询北京到上海的最优航班并给出建议");

        Map<AgentTypeEnum, Agent> systemAgents = new HashMap<>();
        systemAgents.put(AgentTypeEnum.PLANNER, new PlannerAgent(plannerConfig, llmService, promptService));
        systemAgents.put(AgentTypeEnum.CONDUCTOR, new ConductorAgent(conductorConfig, llmService, promptService));
        systemAgents.put(AgentTypeEnum.EVALUATOR, new EvaluatorAgent(evaluatorConfig, llmService, promptService));
        systemAgents.put(AgentTypeEnum.REPORTER, new ReporterAgent(reporterConfig, llmService, promptService));

        ReActStrategyFlow flow = new ReActStrategyFlow(context, systemAgents, 10);

        AgentConfigDO workerDO = AgentConfigDO.builder()
                .name("search_worker")
                .description("航班检索专精工作节点")
                .systemPrompt("负责根据出发地与目的地检索航班实时信息")
                .model("gpt-4o")
                .build();
        WorkerAgent worker = new WorkerAgent(workerDO, defaultConfig, llmService, promptService);
        flow.addAgent(worker);

        String result = flow.execute();

        Assertions.assertNotNull(result);
        Assertions.assertTrue(result.contains("OrchestrAgent") || result.contains("完成"));
        Assertions.assertNotNull(context.getPlanResult());
        Assertions.assertNotNull(context.getLastEvaluatorResult());
        Assertions.assertTrue(context.getLastEvaluatorResult().isPass());
        Assertions.assertEquals(100, context.getLastEvaluatorResult().getScore());
    }

    @Test
    @DisplayName("测试 Evaluator 自愈反馈机制: 验收不合格时打回重试，超过上限优雅降级")
    public void testEvaluatorCritiqueAndDegradation() throws BizException {
        StrategyContext context = new DefaultStrategyContext();
        context.setProperties(PropertyKeys.KEY_STRATEGY_TARGET, "严格核验行李额和改签政策");

        EvaluatorAgent strictEvaluator = new EvaluatorAgent(evaluatorConfig, llmService, promptService) {
            @Override
            public com.shane.orchestragent.biz.model.agent.AgentResult execute(StrategyContext ctx) {
                return new com.shane.orchestragent.biz.model.agent.LlmAgentResult(
                        "{\"pass\": false, \"score\": 60, \"critique\": \"缺少免费行李额详细说明\", \"suggestedRemedy\": \"补充查询行李额\"}",
                        null, null
                );
            }
        };

        Map<AgentTypeEnum, Agent> systemAgents = new HashMap<>();
        systemAgents.put(AgentTypeEnum.PLANNER, new PlannerAgent(plannerConfig, llmService, promptService));
        systemAgents.put(AgentTypeEnum.CONDUCTOR, new ConductorAgent(conductorConfig, llmService, promptService));
        systemAgents.put(AgentTypeEnum.EVALUATOR, strictEvaluator);
        systemAgents.put(AgentTypeEnum.REPORTER, new ReporterAgent(reporterConfig, llmService, promptService));

        ReActStrategyFlow flow = new ReActStrategyFlow(context, systemAgents, 10);

        AgentConfigDO workerDO = AgentConfigDO.builder()
                .name("search_worker")
                .description("航班检索")
                .systemPrompt("检索航班")
                .model("gpt-4o")
                .build();
        flow.addAgent(new WorkerAgent(workerDO, defaultConfig, llmService, promptService));

        String result = flow.execute();

        Assertions.assertNotNull(result);
        Assertions.assertEquals(2, context.getEvaluationRetryCount());
        Assertions.assertNotNull(context.getLastEvaluatorResult());
        Assertions.assertFalse(context.getLastEvaluatorResult().isPass());
        Assertions.assertEquals("缺少免费行李额详细说明", context.getLastEvaluatorResult().getCritique());
    }

    @Test
    @DisplayName("测试 RouterAgent 意图识别与直接回复 (不触发后续流程)")
    public void testRouterDirectReply() throws BizException {
        StrategyContext context = new DefaultStrategyContext();
        context.setProperties(PropertyKeys.KEY_STRATEGY_TARGET, "你好呀");

        RouterAgent directRouter = new RouterAgent(routerConfig, llmService, promptService) {
            @Override
            public com.shane.orchestragent.biz.model.agent.AgentResult execute(StrategyContext ctx) {
                return new com.shane.orchestragent.biz.model.agent.LlmAgentResult(
                        "{\"handoffToPlanner\": false, \"reply\": \"您好！我是 OrchestrAgent，很高兴为您服务。\"}",
                        null, null
                );
            }
        };

        Map<AgentTypeEnum, Agent> systemAgents = new HashMap<>();
        systemAgents.put(AgentTypeEnum.ROUTER, directRouter);
        systemAgents.put(AgentTypeEnum.PLANNER, new PlannerAgent(plannerConfig, llmService, promptService));
        systemAgents.put(AgentTypeEnum.CONDUCTOR, new ConductorAgent(conductorConfig, llmService, promptService));
        systemAgents.put(AgentTypeEnum.EVALUATOR, new EvaluatorAgent(evaluatorConfig, llmService, promptService));
        systemAgents.put(AgentTypeEnum.REPORTER, new ReporterAgent(reporterConfig, llmService, promptService));

        MultipleChatStrategyFlow flow = new MultipleChatStrategyFlow(context, systemAgents, 10);
        String reply = flow.execute();

        Assertions.assertEquals("您好！我是 OrchestrAgent，很高兴为您服务。", reply);
        Assertions.assertNull(context.getPlanResult());
    }

    @Test
    @DisplayName("测试 MultipleChatStrategyFlow: RouterAgent 返回直接答复但内容为空时 Fail-Fast 阻断")
    public void testMultipleChatRouterEmptyReplyFailFast() {
        StrategyConfigDO config = StrategyConfigDO.builder()
                .strategyId("chat_strategy")
                .name("客服多轮问答策略")
                .maxStep(10)
                .flowTopologyType("MULTIPLE_CHAT")
                .build();
        StrategyContext context = new DefaultStrategyContext(config, "test_flow_router_empty", "test_session", null);

        RouterAgent blankReplyRouter = new RouterAgent(routerConfig, llmService, promptService) {
            @Override
            public AgentResult execute(StrategyContext ctx) {
                return new AgentResult("{\"handoffToPlanner\": false, \"reply\": \"\"}", null);
            }
        };

        Map<AgentTypeEnum, Agent> systemAgents = new HashMap<>();
        systemAgents.put(AgentTypeEnum.ROUTER, blankReplyRouter);
        systemAgents.put(AgentTypeEnum.PLANNER, new PlannerAgent(plannerConfig, llmService, promptService));
        systemAgents.put(AgentTypeEnum.CONDUCTOR, new ConductorAgent(conductorConfig, llmService, promptService));
        systemAgents.put(AgentTypeEnum.EVALUATOR, new EvaluatorAgent(evaluatorConfig, llmService, promptService));
        systemAgents.put(AgentTypeEnum.REPORTER, new ReporterAgent(reporterConfig, llmService, promptService));

        MultipleChatStrategyFlow flow = new MultipleChatStrategyFlow(context, systemAgents, 10);
        BizException ex = Assertions.assertThrows(BizException.class, flow::execute);
        Assertions.assertEquals("ROUTER_REPLY_EMPTY", ex.getErrorCode());
    }

    @Test
    @DisplayName("测试 ToolAgent: 严禁假兜底，null配置或空名称时严格抛出异常")
    public void testToolAgentFailFast() {
        Assertions.assertThrows(NullPointerException.class, () -> new ToolAgent(null));
        com.shane.orchestragent.repository.model.ToolConfigDO emptyTool = new com.shane.orchestragent.repository.model.ToolConfigDO();
        Assertions.assertThrows(IllegalArgumentException.class, () -> new ToolAgent(emptyTool));
    }

    @Test
    @DisplayName("测试 AgentManager 整体生命周期调度与结果缓存")
    public void testAgentManagerProcessAndCache() throws Exception {
        FlowService flowService = new FlowServiceImpl();
        FlowCacheService flowCacheService = new FlowCacheServiceImpl();
        FlowExecutionRecorderImpl recorder = new FlowExecutionRecorderImpl(flowService);

        StrategyFlowFactory flowFactory = new StrategyFlowFactory(llmService, promptService, flowService, recorder);
        flowFactory.setAgentConfigRepository(agentRepo);

        StrategyConfigRepositoryImpl strategyRepo = new StrategyConfigRepositoryImpl();
        StrategyConfigDO configDO = StrategyConfigDO.builder()
                .strategyId("biz_travel")
                .name("商务差旅推荐策略")
                .maxStep(10)
                .flowTopologyType("REACT")
                .workerCodes(List.of("search_worker"))
                .build();
        org.springframework.test.util.ReflectionTestUtils.setField(strategyRepo, "strategyConfigApiClient", (com.shane.orchestragent.repository.client.StrategyConfigApiClient) () -> List.of(configDO));
        strategyRepo.afterPropertiesSet();

        AgentManager agentManager = new AgentManagerImpl(flowFactory, strategyRepo, flowService, flowCacheService);

        Map<String, String> bizData = new HashMap<>();
        bizData.put("query", "预订上海到深圳的机票");

        RecommendRequestVO request = RecommendRequestVO.builder()
                .code("biz_travel")
                .bizData(bizData)
                .build();

        RecommendResponseVO response = agentManager.process(request);
        Assertions.assertNotNull(response);
        Assertions.assertNotNull(response.getFlowId());
        String flowId = response.getFlowId();

        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < 3000) {
            com.shane.orchestragent.common.enums.FlowStateEnum state = flowService.getState(flowId);
            if (state == com.shane.orchestragent.common.enums.FlowStateEnum.FINISHED ||
                    state == com.shane.orchestragent.common.enums.FlowStateEnum.ERROR) {
                break;
            }
            Thread.sleep(50);
        }

        Assertions.assertEquals(com.shane.orchestragent.common.enums.FlowStateEnum.FINISHED, flowService.getState(flowId));
        String result = flowService.getResult(flowId);
        Assertions.assertNotNull(result);
        Assertions.assertTrue(result.contains("OrchestrAgent") || result.contains("完成"));

        RecommendRequestVO request2 = RecommendRequestVO.builder()
                .flowId(flowId)
                .code("biz_travel")
                .bizData(bizData)
                .build();
        RecommendResponseVO response2 = agentManager.process(request2);
        Assertions.assertNotNull(response2);
        Assertions.assertEquals(com.shane.orchestragent.common.enums.FlowStateEnum.FINISHED, response2.getState());
        Assertions.assertEquals(result, response2.getResult());
    }

    @Test
    @DisplayName("测试 ConductorAgent 多轮消息组装与系统提示词动态渲染")
    public void testConductorAgentMultiRoundMessageRendering() throws BizException {
        ConductorAgent conductor = new ConductorAgent(conductorConfig, llmService, promptService);
        StrategyContext context = new DefaultStrategyContext();
        context.setProperties(PropertyKeys.KEY_STRATEGY_TARGET, "北京到上海机票");

        // 1. 系统消息渲染
        ChatMessageDTO systemMsg = conductor.formatSystemMessage(context);
        Assertions.assertNotNull(systemMsg);
        Assertions.assertTrue(systemMsg.getContent().contains("# ConductorAgent"));

        // 2. 模拟多轮中间协同产物追加到 context
        context.getChatMessages().add(FlowAgentMessage.builder()
                .agentType(AgentTypeEnum.PLANNER)
                .agentName("PLANNER")
                .output("{\"steps\": [{\"step\": 1, \"agent\": \"search_worker\"}]}")
                .build());
        context.getChatMessages().add(FlowAgentMessage.builder()
                .agentType(AgentTypeEnum.WORKER)
                .agentName("search_worker")
                .output("已查询到3个推荐航班")
                .build());
        context.getChatMessages().add(FlowAgentMessage.builder()
                .agentType(AgentTypeEnum.TOOL)
                .agentName("weather_tool")
                .output("上海晴天 25度")
                .build());
        context.getChatMessages().add(FlowAgentMessage.builder()
                .agentType(AgentTypeEnum.EVALUATOR)
                .agentName("EVALUATOR")
                .output("缺少行李额说明")
                .build());

        // 3. 完整多轮消息格式化
        List<ChatMessageDTO> formatted = conductor.formatMessages(context);
        Assertions.assertEquals(5, formatted.size()); // 1 system + 4 round messages
        Assertions.assertTrue(formatted.get(1).getContent().contains("【任务规划步骤】"));
        Assertions.assertTrue(formatted.get(2).getContent().contains("【工作节点 search_worker 执行结果】"));
        Assertions.assertTrue(formatted.get(3).getContent().contains("【工具 weather_tool 调用结果】"));
        Assertions.assertTrue(formatted.get(4).getContent().contains("【整改要求】: 缺少行李额说明"));
    }

    @Test
    @DisplayName("测试 ToolAgent 与 RagAgent 执行与历史归档")
    public void testToolAgentAndRagAgentExecution() throws BizException {
        StrategyContext context = new DefaultStrategyContext();
        context.setProperties(PropertyKeys.KEY_STRATEGY_TARGET, "检索差旅退改标准");
        context.setNextAgentRequest(Map.of("city", "Beijing"));

        // 1. ToolAgent
        ToolConfigDO toolConfig = ToolConfigDO.builder()
                .name("test_tool")
                .description("测试外部工具")
                .build();
        ToolAgent toolAgent = new ToolAgent(toolConfig);
        com.shane.orchestragent.biz.model.agent.AgentResult toolResult = toolAgent.execute(context);
        Assertions.assertNotNull(toolResult);
        Assertions.assertTrue(toolResult.getOutput().contains("test_tool"));
        Assertions.assertTrue(toolResult.getOutput().contains("Beijing"));

        // 2. RagAgent (带关联知识库)
        RagAgent ragAgent = new RagAgent("test_rag", List.of("kb_refund_01"));
        com.shane.orchestragent.biz.model.agent.AgentResult ragResult = ragAgent.execute(context);
        Assertions.assertNotNull(ragResult);
        Assertions.assertTrue(ragResult.getOutput().contains("知识库匹配内容"));
        Assertions.assertTrue(ragResult.getOutput().contains("kb_refund_01"));

        // 3. 验证历史记录正确追加
        Assertions.assertEquals(2, context.getChatMessages().size());
        Assertions.assertEquals(AgentTypeEnum.TOOL, context.getChatMessages().get(0).getAgentType());
        Assertions.assertEquals(AgentTypeEnum.RAG, context.getChatMessages().get(1).getAgentType());
    }

    @Test
    @DisplayName("测试智能体提示词缺失时严格 Fail-Fast 阻断执行（无代码保底字符串）")
    public void testAgentFailFastWhenMissingPrompt() {
        StrategyContext context = new DefaultStrategyContext();
        context.setProperties(PropertyKeys.KEY_STRATEGY_TARGET, "测试目标");

        // 1. PlannerAgent 缺少 systemPrompt 抛出 AGENT_CONFIG_ERROR
        LlmConfig noSystemPromptConfig = LlmConfig.builder().model("gpt-4o").userPrompt("目标: ${strategy_target}").build();
        PlannerAgent plannerNoSystem = new PlannerAgent(noSystemPromptConfig, llmService, promptService);
        BizException ex1 = Assertions.assertThrows(BizException.class, () -> plannerNoSystem.execute(context));
        Assertions.assertEquals("AGENT_CONFIG_ERROR", ex1.getErrorCode());
        Assertions.assertTrue(ex1.getMessage().contains("PLANNER") && ex1.getMessage().contains("systemPrompt"));

        // 2. PlannerAgent 缺少 userPrompt 抛出 AGENT_CONFIG_ERROR
        LlmConfig noUserPromptConfig = LlmConfig.builder().model("gpt-4o").systemPrompt("# Planner System").build();
        PlannerAgent plannerNoUser = new PlannerAgent(noUserPromptConfig, llmService, promptService);
        BizException ex2 = Assertions.assertThrows(BizException.class, () -> plannerNoUser.execute(context));
        Assertions.assertEquals("AGENT_CONFIG_ERROR", ex2.getErrorCode());
        Assertions.assertTrue(ex2.getMessage().contains("PLANNER") && ex2.getMessage().contains("userPrompt"));

        // 3. WorkerAgent 缺少有效 prompt / systemPrompt 抛出 AGENT_CONFIG_ERROR
        AgentConfigDO emptyPromptWorkerDO = AgentConfigDO.builder().name("empty_worker").build();
        LlmConfig emptyLlmConfig = LlmConfig.builder().model("gpt-4o").build();
        WorkerAgent workerNoPrompt = new WorkerAgent(emptyPromptWorkerDO, emptyLlmConfig, llmService, promptService);
        BizException ex3 = Assertions.assertThrows(BizException.class, () -> workerNoPrompt.execute(context));
        Assertions.assertEquals("AGENT_CONFIG_ERROR", ex3.getErrorCode());
        Assertions.assertTrue(ex3.getMessage().contains("empty_worker") && ex3.getMessage().contains("systemPrompt"));
    }

    @Test
    @DisplayName("测试 StrategyFlowFactory 挂载 ToolAgent 与伴生 RagAgent 全协同链路")
    public void testFactoryMountsToolsAndCompanionRag() throws Exception {
        ToolConfigRepositoryImpl toolRepo = new ToolConfigRepositoryImpl();
        ToolConfigDO weatherTool = ToolConfigDO.builder()
                .name("weather_tool")
                .title("天气查询工具")
                .description("查询指定城市天气")
                .build();
        org.springframework.test.util.ReflectionTestUtils.setField(toolRepo, "toolConfigApiClient", (com.shane.orchestragent.repository.client.ToolConfigApiClient) () -> List.of(weatherTool));
        toolRepo.afterPropertiesSet();

        StrategyFlowFactory factory = new StrategyFlowFactory(llmService, promptService);
        factory.setToolConfigRepository(toolRepo);
        factory.setAgentConfigRepository(agentRepo);

        StrategyConfigDO config = StrategyConfigDO.builder()
                .strategyId("companion_rag_strat")
                .flowTopologyType("REACT")
                .maxStep(10)
                .toolCodes(List.of("weather_tool"))
                .workerCodes(List.of("expert_ticket"))
                .build();

        DefaultStrategyContext context = new DefaultStrategyContext(config, "test-flow-001", "session-001", null);
        ReActStrategyFlow flow = (ReActStrategyFlow) factory.create(context);

        // 验证系统已成功注册 ToolAgent 与伴生 RAG (expert_ticket#RAG)
        Assertions.assertTrue(flow.getAgents().containsKey("weather_tool"));
        Assertions.assertTrue(flow.getAgents().containsKey("expert_ticket"));
        Assertions.assertTrue(flow.getAgents().containsKey("expert_ticket#RAG"));
    }

    @Test
    @DisplayName("测试纯 workerCodes 引用挂载 Worker 与伴生 RAG")
    public void testPureWorkerCodesAndCompanionRag() throws Exception {
        ToolConfigRepositoryImpl toolRepo = new ToolConfigRepositoryImpl();
        List<ToolConfigDO> tools = List.of(
                ToolConfigDO.builder()
                        .name("weather_tool")
                        .title("实时天气")
                        .build()
        );
        org.springframework.test.util.ReflectionTestUtils.setField(toolRepo, "toolConfigApiClient", (com.shane.orchestragent.repository.client.ToolConfigApiClient) () -> tools);
        toolRepo.afterPropertiesSet();

        StrategyFlowFactory factory = new StrategyFlowFactory(llmService, promptService);
        factory.setToolConfigRepository(toolRepo);
        factory.setAgentConfigRepository(agentRepo);

        StrategyConfigDO config = StrategyConfigDO.builder()
                .strategyId("pure_worker_codes_strat")
                .flowTopologyType("REACT")
                .maxStep(10)
                .toolCodes(List.of("weather_tool"))
                .workerCodes(List.of("expert_ticket"))
                .build();

        DefaultStrategyContext context = new DefaultStrategyContext(config, "test-flow-pure-001", "session-pure-001", null);
        ReActStrategyFlow flow = (ReActStrategyFlow) factory.create(context);

        // 验证纯 workerCodes 模式下成功通过 AgentConfigRepository 解析并注册 WorkerAgent 与伴生 RAG
        Assertions.assertTrue(flow.getAgents().containsKey("weather_tool"));
        Assertions.assertTrue(flow.getAgents().containsKey("expert_ticket"));
        Assertions.assertTrue(flow.getAgents().containsKey("expert_ticket#RAG"));
    }
}
