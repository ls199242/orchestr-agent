package com.shane.orchestragent.test.flow;

import com.shane.orchestragent.biz.agent.Agent;
import com.shane.orchestragent.biz.agent.base.BaseAgent;
import com.shane.orchestragent.biz.agent.flow.*;
import com.shane.orchestragent.biz.context.StrategyContext;
import com.shane.orchestragent.biz.context.impl.DefaultStrategyContext;
import com.shane.orchestragent.biz.flow.ReActStrategyFlow;
import com.shane.orchestragent.biz.flow.SessionOrchestrationFlow;
import com.shane.orchestragent.biz.flow.StrategyFlowFactory;
import com.shane.orchestragent.biz.model.flow.FlowDagStep;
import com.shane.orchestragent.biz.model.flow.PlanResult;
import com.shane.orchestragent.memory.session.SessionPendingState;
import com.shane.orchestragent.memory.session.SessionStateStore;
import com.shane.orchestragent.biz.model.agent.AgentResult;
import com.shane.orchestragent.biz.model.agent.LlmConfig;
import com.shane.orchestragent.biz.model.flow.FlowAgentMessage;
import com.shane.orchestragent.biz.recorder.impl.FlowExecutionRecorderImpl;
import com.shane.orchestragent.biz.service.FlowService;
import com.shane.orchestragent.biz.service.LlmService;
import com.shane.orchestragent.biz.service.impl.FlowServiceImpl;
import com.shane.orchestragent.biz.service.impl.LlmServiceImpl;
import com.shane.orchestragent.common.constant.PropertyKeys;
import com.shane.orchestragent.common.enums.AgentTypeEnum;
import com.shane.orchestragent.common.enums.FlowStateEnum;
import com.shane.orchestragent.common.exception.BizException;
import com.shane.orchestragent.integration.llm.LlmClient;
import com.shane.orchestragent.test.support.MockLlmClient;
import com.shane.orchestragent.integration.llm.model.ChatMessageDTO;
import com.shane.orchestragent.prompt.render.TemplateRender;
import com.shane.orchestragent.prompt.service.PromptService;
import com.shane.orchestragent.prompt.service.impl.PromptServiceImpl;
import com.shane.orchestragent.repository.client.AgentConfigApiClient;
import com.shane.orchestragent.repository.client.StrategyConfigApiClient;
import com.shane.orchestragent.repository.impl.AgentConfigRepositoryImpl;
import com.shane.orchestragent.repository.impl.StrategyConfigRepositoryImpl;
import com.shane.orchestragent.repository.impl.ToolConfigRepositoryImpl;
import com.shane.orchestragent.repository.model.AgentConfigDO;

import java.util.Set;
import com.shane.orchestragent.repository.model.StrategyConfigDO;
import com.shane.orchestragent.repository.model.StrategyParamDO;
import com.shane.orchestragent.repository.model.ToolConfigDO;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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
    private LlmConfig supervisorConfig;
    private LlmConfig evaluatorConfig;
    private LlmConfig reporterConfig;
    private LlmConfig coordinatorConfig;
    private AgentConfigRepositoryImpl agentRepo;
    private StrategyConfigRepositoryImpl strategyRepo;
    private SessionStateStore sessionStateStore;
    private StrategyFlowFactory flowFactory;

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

        this.supervisorConfig = LlmConfig.builder()
                .model("gpt-4o")
                .nodeWaitTime(5000L)
                .systemPrompt("# SupervisorAgent\n调度下一步。输出: {\"next\": \"FINISH\", \"request\": {}}")
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

        this.coordinatorConfig = LlmConfig.builder()
                .model("gpt-4o")
                .nodeWaitTime(5000L)
                .systemPrompt("# CoordinatorAgent\n意图决策: {\"action\": \"DIRECT_REPLY|COLLECT|EXECUTE\", \"reply\": \"\", \"strategyCode\": \"\", \"params\": {}}")
                .userPrompt("【用户最新消息】：${strategy_target}")
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
                AgentConfigDO.builder().name("SUPERVISOR").code("SUPERVISOR").model("gpt-4o").systemPrompt(supervisorConfig.getSystemPrompt()).userPrompt(supervisorConfig.getUserPrompt()).build(),
                AgentConfigDO.builder().name("EVALUATOR").code("EVALUATOR").model("gpt-4o").systemPrompt(evaluatorConfig.getSystemPrompt()).userPrompt(evaluatorConfig.getUserPrompt()).build(),
                AgentConfigDO.builder().name("REPORTER").code("REPORTER").model("gpt-4o").systemPrompt(reporterConfig.getSystemPrompt()).userPrompt(reporterConfig.getUserPrompt()).build(),
                AgentConfigDO.builder().name("COORDINATOR").code("COORDINATOR").model("gpt-4o").systemPrompt(coordinatorConfig.getSystemPrompt()).userPrompt(coordinatorConfig.getUserPrompt()).build(),
                AgentConfigDO.builder().name("CHAT_SUMMARY").code("CHAT_SUMMARY").model("gpt-4o").systemPrompt("# ChatSummaryAgent\n摘要压缩").build(),
                AgentConfigDO.builder().name("search_worker").code("search_worker").agentType("EXPERT").model("gpt-4o").systemPrompt("检索航班信息").build(),
                AgentConfigDO.builder().name("expert_ticket").code("expert_ticket").agentType("EXPERT").model("gpt-4o").systemPrompt("负责客规审查").datasets(List.of("kb_flight_rules")).build()
        );
        org.springframework.test.util.ReflectionTestUtils.setField(agentRepo, "agentConfigApiClient", (AgentConfigApiClient) () -> systemAgents);
        agentRepo.afterPropertiesSet();

        // 带参数定义的策略目录与编排流工厂 (会话编排层意图选策略 + 参数收集测试依赖)
        this.strategyRepo = new StrategyConfigRepositoryImpl();
        StrategyConfigDO flightStrategy = StrategyConfigDO.builder()
                .strategyId("flight_strategy")
                .code("flight_strategy")
                .name("航班落地服务策略")
                .description("航班落地场景的接机/酒店/门票推荐")
                .maxStep(10)
                .flowTopologyType("REACT")
                .expertCodes(List.of("search_worker"))
                .params(List.of(
                        StrategyParamDO.builder().name("flightNo").type("string").description("航班号，如 CA1234").required(true).build(),
                        StrategyParamDO.builder().name("arrivalCity").type("string").description("落地城市").required(true).build(),
                        StrategyParamDO.builder().name("userId").type("string").description("旅客会员ID（系统注入）").required(true).build()
                ))
                .build();
        org.springframework.test.util.ReflectionTestUtils.setField(strategyRepo, "strategyConfigApiClient", (StrategyConfigApiClient) () -> List.of(flightStrategy));
        strategyRepo.afterPropertiesSet();

        this.sessionStateStore = new SessionStateStore();
        this.flowFactory = new StrategyFlowFactory(llmService, promptService);
        flowFactory.setAgentConfigRepository(agentRepo);
        flowFactory.setStrategyConfigRepository(strategyRepo);
        flowFactory.setSessionStateStore(sessionStateStore);
    }

    @Test
    @DisplayName("测试正常全流程: Planner -> Supervisor -> Expert -> Evaluator(通过) -> Reporter")
    public void testFullFlowSuccess() throws BizException {
        StrategyContext context = new DefaultStrategyContext();
        context.setProperties(PropertyKeys.KEY_STRATEGY_TARGET, "查询北京到上海的最优航班并给出建议");

        Map<AgentTypeEnum, Agent> systemAgents = new HashMap<>();
        systemAgents.put(AgentTypeEnum.PLANNER, new PlannerAgent(plannerConfig, llmService, promptService));
        systemAgents.put(AgentTypeEnum.SUPERVISOR, new SupervisorAgent());
        systemAgents.put(AgentTypeEnum.EVALUATOR, new EvaluatorAgent(evaluatorConfig, llmService, promptService));
        systemAgents.put(AgentTypeEnum.REPORTER, new ReporterAgent(reporterConfig, llmService, promptService));

        ReActStrategyFlow flow = new ReActStrategyFlow(context, systemAgents, 10);

        AgentConfigDO expertDO = AgentConfigDO.builder()
                .name("search_worker")
                .description("航班检索专精工作节点")
                .systemPrompt("负责根据出发地与目的地检索航班实时信息")
                .model("gpt-4o")
                .build();
        ExpertAgent expert = new ExpertAgent(expertDO, defaultConfig, llmService, promptService);
        flow.addAgent(expert);

        flow.execute();
        String result = context.getRecommendResult();

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
        systemAgents.put(AgentTypeEnum.SUPERVISOR, new SupervisorAgent());
        systemAgents.put(AgentTypeEnum.EVALUATOR, strictEvaluator);
        systemAgents.put(AgentTypeEnum.REPORTER, new ReporterAgent(reporterConfig, llmService, promptService));

        ReActStrategyFlow flow = new ReActStrategyFlow(context, systemAgents, 10);

        AgentConfigDO expertDO = AgentConfigDO.builder()
                .name("search_worker")
                .description("航班检索")
                .systemPrompt("检索航班")
                .model("gpt-4o")
                .build();
        flow.addAgent(new ExpertAgent(expertDO, defaultConfig, llmService, promptService));

        flow.execute();
        String result = context.getRecommendResult();

        Assertions.assertNotNull(result);
        Assertions.assertEquals(2, context.getEvaluationRetryCount());
        Assertions.assertNotNull(context.getLastEvaluatorResult());
        Assertions.assertFalse(context.getLastEvaluatorResult().isPass());
        Assertions.assertEquals("缺少免费行李额详细说明", context.getLastEvaluatorResult().getCritique());
    }

    @Test
    @DisplayName("测试会话编排流: 意图识别判定闲聊直答 (不触发任何策略)")
    public void testCoordinatorDirectReply() throws BizException {
        StrategyContext context = new DefaultStrategyContext();
        context.setProperties(PropertyKeys.KEY_STRATEGY_TARGET, "你好呀");

        CoordinatorAgent directCoordinator = new CoordinatorAgent(coordinatorConfig, llmService, promptService) {
            @Override
            public com.shane.orchestragent.biz.model.agent.AgentResult execute(StrategyContext ctx) {
                return new com.shane.orchestragent.biz.model.agent.LlmAgentResult(
                        "{\"action\": \"DIRECT_REPLY\", \"reply\": \"您好！我是 OrchestrAgent，很高兴为您服务。\"}",
                        null, null
                );
            }
        };

        SessionOrchestrationFlow flow = new SessionOrchestrationFlow(
                context, directCoordinator, strategyRepo, sessionStateStore, flowFactory, 10);
        flow.execute();
        String reply = context.getRecommendResult();

        Assertions.assertEquals("您好！我是 OrchestrAgent，很高兴为您服务。", reply);
        Assertions.assertNull(context.getPlanResult());
        Assertions.assertEquals(FlowStateEnum.FINISHED, flow.getState());
    }

    @Test
    @DisplayName("测试会话编排流: 意图识别直答但 reply 为空时 Fail-Fast 阻断")
    public void testMultipleChatCoordinatorEmptyReplyFailFast() {
        StrategyContext context = new DefaultStrategyContext(null, "test_flow_coordinator_empty", "test_session_empty", null);
        context.setProperties(PropertyKeys.KEY_STRATEGY_TARGET, "你好");

        CoordinatorAgent blankReplyCoordinator = new CoordinatorAgent(coordinatorConfig, llmService, promptService) {
            @Override
            public AgentResult execute(StrategyContext ctx) {
                return new AgentResult("{\"action\": \"DIRECT_REPLY\", \"reply\": \"\"}", null);
            }
        };

        SessionOrchestrationFlow flow = new SessionOrchestrationFlow(
                context, blankReplyCoordinator, strategyRepo, sessionStateStore, flowFactory, 10);
        flow.execute();
        Assertions.assertEquals(FlowStateEnum.ERROR, flow.getState());
        Throwable lastError = (Throwable) context.getProperty("lastError");
        Assertions.assertTrue(lastError instanceof BizException);
        Assertions.assertEquals("COORDINATOR_REPLY_EMPTY", ((BizException) lastError).getErrorCode());
    }

    @Test
    @DisplayName("测试会话编排流: 缺参追问落盘待定状态并跨轮累积参数")
    public void testSessionCollectParamsAcrossRounds() throws BizException {
        // 第一轮: 命中策略但缺少 arrivalCity，COLLECT 追问并落盘待定状态
        StrategyContext round1 = new DefaultStrategyContext(null, "test_flow_collect_1", "test_session_collect", null);
        round1.setProperties(PropertyKeys.KEY_STRATEGY_TARGET, "CA1234 落地了帮我安排接机");
        round1.setUserId("U001");

        CoordinatorAgent collectCoordinator = new CoordinatorAgent(coordinatorConfig, llmService, promptService) {
            @Override
            public AgentResult execute(StrategyContext ctx) {
                return new AgentResult("{\"action\": \"COLLECT\", \"reply\": \"请问您落地哪座城市？\", "
                        + "\"strategyCode\": \"flight_strategy\", \"params\": {\"flightNo\": \"CA1234\"}}", null);
            }
        };

        SessionOrchestrationFlow flow1 = new SessionOrchestrationFlow(
                round1, collectCoordinator, strategyRepo, sessionStateStore, flowFactory, 10);
        flow1.execute();

        Assertions.assertEquals("请问您落地哪座城市？", round1.getRecommendResult());
        Assertions.assertNull(round1.getPlanResult());
        SessionPendingState pending = sessionStateStore.get("test_session_collect");
        Assertions.assertNotNull(pending);
        Assertions.assertEquals("flight_strategy", pending.getStrategyCode());
        Assertions.assertEquals("CA1234", pending.getCollectedParams().get("flightNo"));

        // 第二轮: 补充 arrivalCity 后必填齐备，EXECUTE 触发策略流
        StrategyContext round2 = new DefaultStrategyContext(null, "test_flow_collect_2", "test_session_collect", null);
        round2.setProperties(PropertyKeys.KEY_STRATEGY_TARGET, "上海虹桥");
        round2.setUserId("U001");

        CoordinatorAgent executeCoordinator = new CoordinatorAgent(coordinatorConfig, llmService, promptService) {
            @Override
            public AgentResult execute(StrategyContext ctx) {
                return new AgentResult("{\"action\": \"EXECUTE\", \"reply\": \"\", "
                        + "\"strategyCode\": \"flight_strategy\", \"params\": {\"arrivalCity\": \"上海\"}}", null);
            }
        };

        SessionOrchestrationFlow flow2 = new SessionOrchestrationFlow(
                round2, executeCoordinator, strategyRepo, sessionStateStore, flowFactory, 10);
        flow2.execute();

        // 跨轮累积参数已合并 (flightNo + arrivalCity + userId 系统注入) 且策略流真实执行
        Assertions.assertNotNull(round2.getRecommendResult());
        Assertions.assertNull(sessionStateStore.get("test_session_collect"));
        Assertions.assertEquals(FlowStateEnum.FINISHED, flow2.getState());

        // 委托策略流以同一 flowId 注册于 FlowStore: 验证其独立上下文中的规划结果与平铺入参
        com.shane.orchestragent.biz.flow.StrategyFlow delegate = com.shane.orchestragent.biz.flow.store.FlowStore.get("test_flow_collect_2");
        Assertions.assertNotNull(delegate);
        Assertions.assertNotNull(delegate.getContext().getPlanResult());
        Assertions.assertEquals("CA1234", delegate.getContext().getProperty("flightNo"));
        Assertions.assertEquals("上海", delegate.getContext().getProperty("arrivalCity"));
        Assertions.assertEquals("U001", delegate.getContext().getProperty("userId"));
    }

    @Test
    @DisplayName("测试会话编排流: LLM 误判 EXECUTE 但必填参数缺失时强制降级为追问 (严禁假通过)")
    public void testSessionExecuteWithMissingParamsDowngrade() throws BizException {
        StrategyContext context = new DefaultStrategyContext(null, "test_flow_downgrade", "test_session_downgrade", null);
        context.setProperties(PropertyKeys.KEY_STRATEGY_TARGET, "帮我安排接机");
        context.setUserId("U001");

        CoordinatorAgent prematureExecuteCoordinator = new CoordinatorAgent(coordinatorConfig, llmService, promptService) {
            @Override
            public AgentResult execute(StrategyContext ctx) {
                return new AgentResult("{\"action\": \"EXECUTE\", \"reply\": \"\", "
                        + "\"strategyCode\": \"flight_strategy\", \"params\": {}}", null);
            }
        };

        SessionOrchestrationFlow flow = new SessionOrchestrationFlow(
                context, prematureExecuteCoordinator, strategyRepo, sessionStateStore, flowFactory, 10);
        flow.execute();

        // 完整性终审拦截: 未触发策略流，落盘待定状态并返回确定性追问话术
        Assertions.assertNull(context.getPlanResult());
        String reply = context.getRecommendResult();
        Assertions.assertNotNull(reply);
        Assertions.assertTrue(reply.contains("flightNo") && reply.contains("arrivalCity"));
        SessionPendingState pending = sessionStateStore.get("test_session_downgrade");
        Assertions.assertNotNull(pending);
        Assertions.assertEquals("flight_strategy", pending.getStrategyCode());
    }

    @Test
    @DisplayName("测试会话编排流: 意图识别编造目录外策略编码时 Fail-Fast 阻断")
    public void testSessionUnknownStrategyFailFast() {
        StrategyContext context = new DefaultStrategyContext(null, "test_flow_unknown", "test_session_unknown", null);
        context.setProperties(PropertyKeys.KEY_STRATEGY_TARGET, "帮我退票");

        CoordinatorAgent hallucinatedCoordinator = new CoordinatorAgent(coordinatorConfig, llmService, promptService) {
            @Override
            public AgentResult execute(StrategyContext ctx) {
                return new AgentResult("{\"action\": \"EXECUTE\", \"reply\": \"\", "
                        + "\"strategyCode\": \"not_exist_strategy\", \"params\": {}}", null);
            }
        };

        SessionOrchestrationFlow flow = new SessionOrchestrationFlow(
                context, hallucinatedCoordinator, strategyRepo, sessionStateStore, flowFactory, 10);
        flow.execute();

        Assertions.assertEquals(FlowStateEnum.ERROR, flow.getState());
        Throwable lastError = (Throwable) context.getProperty("lastError");
        Assertions.assertTrue(lastError instanceof BizException);
        Assertions.assertEquals("COORDINATOR_STRATEGY_UNKNOWN", ((BizException) lastError).getErrorCode());
    }

    @Test
    @DisplayName("测试会话编排流: 系统级必填参数 userId 上下文缺失时 Fail-Fast (严禁向用户追问)")
    public void testSessionSystemParamMissingFailFast() {
        StrategyContext context = new DefaultStrategyContext(null, "test_flow_sysparam", "test_session_sysparam", null);
        context.setProperties(PropertyKeys.KEY_STRATEGY_TARGET, "CA1234 落地上海帮我安排接机");

        CoordinatorAgent executeCoordinator = new CoordinatorAgent(coordinatorConfig, llmService, promptService) {
            @Override
            public AgentResult execute(StrategyContext ctx) {
                return new AgentResult("{\"action\": \"EXECUTE\", \"reply\": \"\", "
                        + "\"strategyCode\": \"flight_strategy\", \"params\": {\"flightNo\": \"CA1234\", \"arrivalCity\": \"上海\"}}", null);
            }
        };

        SessionOrchestrationFlow flow = new SessionOrchestrationFlow(
                context, executeCoordinator, strategyRepo, sessionStateStore, flowFactory, 10);
        flow.execute();

        // userId 声明必填但请求上下文未提供: Fail-Fast 而非向终端用户追问
        Assertions.assertEquals(FlowStateEnum.ERROR, flow.getState());
        Throwable lastError = (Throwable) context.getProperty("lastError");
        Assertions.assertTrue(lastError instanceof BizException);
        Assertions.assertEquals("STRATEGY_PARAM_MISSING", ((BizException) lastError).getErrorCode());
    }

    @Test
    @DisplayName("测试 ToolAgent: 严禁假兜底，null配置、空名称或缺失端点时严格抛出异常")
    public void testToolAgentFailFast() {
        Assertions.assertThrows(NullPointerException.class, () -> new ToolAgent(null));
        com.shane.orchestragent.repository.model.ToolConfigDO emptyTool = new com.shane.orchestragent.repository.model.ToolConfigDO();
        Assertions.assertThrows(IllegalArgumentException.class, () -> new ToolAgent(emptyTool));

        com.shane.orchestragent.repository.model.ToolConfigDO noEndpointTool = com.shane.orchestragent.repository.model.ToolConfigDO.builder()
                .name("no_endpoint_tool")
                .code("no_endpoint_tool")
                .description("无端点工具")
                .build();
        ToolAgent toolAgent = new ToolAgent(noEndpointTool);
        Assertions.assertThrows(BizException.class, () -> toolAgent.execute(new DefaultStrategyContext()));
    }

    @Test
    @DisplayName("测试纯代码 SupervisorAgent 之 DAG 校验、并发调度与局部重跑")
    public void testSupervisorAgentDagExecutionAndValidation() throws BizException {
        SupervisorAgent supervisor = new SupervisorAgent();
        StrategyContext context = new DefaultStrategyContext();
        context.setProperties("orderId", "A1024");
        context.setProperties(PropertyKeys.KEY_STRATEGY_TARGET, "退货审批");

        // 注册两个测试子节点
        Agent s1Agent = new BaseAgent<StrategyContext>("s1_worker", "步骤1工作节点", AgentTypeEnum.EXPERT) {
            @Override
            protected AgentResult doExecute(StrategyContext ctx) throws BizException {
                return new AgentResult("{\"eligible\": true, \"evidence\": \"符合条件\"}", null);
            }
        };
        Agent s2Agent = new BaseAgent<StrategyContext>("s2_worker", "步骤2工作节点", AgentTypeEnum.EXPERT) {
            @Override
            protected AgentResult doExecute(StrategyContext ctx) throws BizException {
                Map<String, Object> req = ctx.getNextAgentRequest();
                Assertions.assertNotNull(req);
                return new AgentResult("{\"approved\": true}", null);
            }
        };
        context.registerAgent(s1Agent);
        context.registerAgent(s2Agent);

        // 1. 测试有环检测 (Kahn 算法 Fail-Fast)
        FlowDagStep cycle1 = FlowDagStep.builder().id("c1").agent("s1_worker").dependsOn(List.of("c2")).build();
        FlowDagStep cycle2 = FlowDagStep.builder().id("c2").agent("s2_worker").dependsOn(List.of("c1")).build();
        PlanResult cyclePlan = PlanResult.builder().steps(List.of(cycle1, cycle2)).build();
        Assertions.assertThrows(BizException.class, () -> supervisor.run(cyclePlan, context));

        // 2. 测试正常 DAG 并发调度与参数机械求值 ($global.orderId, $s1.output)
        FlowDagStep step1 = FlowDagStep.builder()
                .id("s1")
                .agent("s1_worker")
                .goal("查询是否符合退货")
                .inputs(Map.of("orderId", "$global.orderId"))
                .dependsOn(List.of())
                .outputKeys(Set.of("eligible"))
                .build();
        FlowDagStep step2 = FlowDagStep.builder()
                .id("s2")
                .agent("s2_worker")
                .goal("审批决策")
                .inputs(Map.of("s1Result", "$s1.output", "eligible", "$s1.output.eligible"))
                .dependsOn(List.of("s1"))
                .outputKeys(Set.of("approved"))
                .build();
        PlanResult normalPlan = PlanResult.builder().steps(List.of(step1, step2)).build();

        com.shane.orchestragent.biz.flow.dag.DagExecutionState state = supervisor.run(normalPlan, context);
        Assertions.assertTrue(state.isAllSucceeded());
        Assertions.assertEquals(1, state.getAttempts().get("s1"));
        Assertions.assertEquals(1, state.getAttempts().get("s2"));

        // 3. 测试 Evaluator 点名局部重跑 (传递下游闭包计算: s2 作废重跑，s1 尝试次数仍为 1 原样复用)
        Map<String, String> feedback = Map.of("s2", "缺少审批备注，请补充");
        state = supervisor.rerun(normalPlan, state, feedback, context);
        Assertions.assertTrue(state.isAllSucceeded());
        Assertions.assertEquals(1, state.getAttempts().get("s1")); // s1 未被打回，尝试次数不变
        Assertions.assertEquals(2, state.getAttempts().get("s2")); // s2 被点名重跑，尝试次数增加到 2
    }

    @Test
    @DisplayName("测试 ToolAgent 与 RagAgent 执行与历史归档")
    @SuppressWarnings("unchecked")
    public void testToolAgentAndRagAgentExecution() throws Exception {
        StrategyContext context = new DefaultStrategyContext();
        context.setProperties(PropertyKeys.KEY_STRATEGY_TARGET, "检索差旅退改标准");
        context.setNextAgentRequest(Map.of("city", "Beijing"));

        // 1. ToolAgent (使用纯净 Mockito 测试桩隔离外部网络)
        HttpClient mockHttpClient = Mockito.mock(HttpClient.class);
        HttpResponse<String> mockResponse = (HttpResponse<String>) Mockito.mock(HttpResponse.class);
        Mockito.when(mockResponse.statusCode()).thenReturn(200);
        Mockito.when(mockResponse.body()).thenReturn("{\"status\": \"SUCCESS\", \"tool\": \"test_tool\", \"city\": \"Beijing\"}");
        Mockito.when(mockHttpClient.send(Mockito.any(HttpRequest.class), Mockito.any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);

        ToolConfigDO toolConfig = ToolConfigDO.builder()
                .name("test_tool")
                .code("test_tool")
                .description("测试外部工具")
                .endpoint("http://127.0.0.1:8000/mock/tools/weather")
                .build();
        ToolAgent toolAgent = new ToolAgent(toolConfig, mockHttpClient);
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
    @DisplayName("测试 ToolAgent 请求序列化与强制 HTTP_1_1 版本防 422")
    @SuppressWarnings("unchecked")
    public void testToolAgentRequestSerializationAndHttpVersion() throws Exception {
        HttpClient mockHttpClient = Mockito.mock(HttpClient.class);
        HttpResponse<String> mockResponse = (HttpResponse<String>) Mockito.mock(HttpResponse.class);
        Mockito.when(mockResponse.statusCode()).thenReturn(200);
        Mockito.when(mockResponse.body()).thenReturn("{\"status\":\"OK\"}");

        org.mockito.ArgumentCaptor<HttpRequest> captor = org.mockito.ArgumentCaptor.forClass(HttpRequest.class);
        Mockito.when(mockHttpClient.send(captor.capture(), Mockito.any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);

        ToolConfigDO toolConfig = ToolConfigDO.builder()
                .name("query_user_profile_tool")
                .code("query_user_profile_tool")
                .description("旅客偏好查询")
                .endpoint("http://127.0.0.1:8000/mock/tools/user-profile")
                .build();
        ToolAgent toolAgent = new ToolAgent(toolConfig, mockHttpClient);

        // 1. null 请求，兜底为 "{}" 且 HTTP 版本为 HTTP_1_1
        StrategyContext context = new DefaultStrategyContext();
        context.setNextAgentRequest(null);
        toolAgent.execute(context);
        HttpRequest req1 = captor.getValue();
        Assertions.assertEquals(HttpClient.Version.HTTP_1_1, req1.version().orElse(null));

        // 2. 正常 Map 请求
        context.setNextAgentRequest(Map.of("userId", "U8801"));
        toolAgent.execute(context);
        HttpRequest req2 = captor.getValue();
        Assertions.assertEquals(HttpClient.Version.HTTP_1_1, req2.version().orElse(null));

        // 3. 字符串格式 JSON 请求
        context.setNextAgentRequest(null);
        context.setProperties("agentRequest", "{\"userId\":\"U8801\"}");
        toolAgent.execute(context);
        HttpRequest req3 = captor.getValue();
        Assertions.assertEquals(HttpClient.Version.HTTP_1_1, req3.version().orElse(null));
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

        // 3. ExpertAgent 缺少有效 prompt / systemPrompt 抛出 AGENT_CONFIG_ERROR
        AgentConfigDO emptyPromptExpertDO = AgentConfigDO.builder().name("empty_worker").build();
        LlmConfig emptyLlmConfig = LlmConfig.builder().model("gpt-4o").build();
        ExpertAgent expertNoPrompt = new ExpertAgent(emptyPromptExpertDO, emptyLlmConfig, llmService, promptService);
        BizException ex3 = Assertions.assertThrows(BizException.class, () -> expertNoPrompt.execute(context));
        Assertions.assertEquals("AGENT_CONFIG_ERROR", ex3.getErrorCode());
        Assertions.assertTrue(ex3.getMessage().contains("empty_worker") && ex3.getMessage().contains("systemPrompt"));
    }

    @Test
    @DisplayName("测试 StrategyFlowFactory 挂载 ToolAgent 与伴生 RagAgent 全协同链路")
    public void testFactoryMountsToolsAndCompanionRag() throws Exception {
        ToolConfigRepositoryImpl toolRepo = new ToolConfigRepositoryImpl();
        ToolConfigDO weatherTool = ToolConfigDO.builder()
                .name("weather_tool")
                .code("weather_tool")
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
                .code("companion_rag_strat")
                .flowTopologyType("REACT")
                .maxStep(10)
                .toolCodes(List.of("weather_tool"))
                .expertCodes(List.of("expert_ticket"))
                .build();

        DefaultStrategyContext context = new DefaultStrategyContext(config, "test-flow-001", "session-001", null);
        ReActStrategyFlow flow = (ReActStrategyFlow) factory.create(context);

        // 验证系统已成功注册 ToolAgent 与伴生 RAG (expert_ticket#RAG)
        Assertions.assertTrue(flow.getAgents().containsKey("weather_tool"));
        Assertions.assertTrue(flow.getAgents().containsKey("expert_ticket"));
        Assertions.assertTrue(flow.getAgents().containsKey("expert_ticket#RAG"));
    }

    @Test
    @DisplayName("测试纯 expertCodes 引用挂载 Expert 与伴生 RAG")
    public void testPureExpertCodesAndCompanionRag() throws Exception {
        ToolConfigRepositoryImpl toolRepo = new ToolConfigRepositoryImpl();
        List<ToolConfigDO> tools = List.of(
                ToolConfigDO.builder()
                        .name("weather_tool")
                        .code("weather_tool")
                        .title("实时天气")
                        .build()
        );
        org.springframework.test.util.ReflectionTestUtils.setField(toolRepo, "toolConfigApiClient", (com.shane.orchestragent.repository.client.ToolConfigApiClient) () -> tools);
        toolRepo.afterPropertiesSet();

        StrategyFlowFactory factory = new StrategyFlowFactory(llmService, promptService);
        factory.setToolConfigRepository(toolRepo);
        factory.setAgentConfigRepository(agentRepo);

        StrategyConfigDO config = StrategyConfigDO.builder()
                .strategyId("pure_expert_codes_strat")
                .code("pure_expert_codes_strat")
                .flowTopologyType("REACT")
                .maxStep(10)
                .toolCodes(List.of("weather_tool"))
                .expertCodes(List.of("expert_ticket"))
                .build();

        DefaultStrategyContext context = new DefaultStrategyContext(config, "test-flow-pure-001", "session-pure-001", null);
        ReActStrategyFlow flow = (ReActStrategyFlow) factory.create(context);

        // 验证纯 expertCodes 模式下成功通过 AgentConfigRepository 解析并注册 ExpertAgent 与伴生 RAG
        Assertions.assertTrue(flow.getAgents().containsKey("weather_tool"));
        Assertions.assertTrue(flow.getAgents().containsKey("expert_ticket"));
        Assertions.assertTrue(flow.getAgents().containsKey("expert_ticket#RAG"));
    }
}
