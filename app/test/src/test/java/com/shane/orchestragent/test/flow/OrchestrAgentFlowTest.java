package com.shane.orchestragent.test.flow;

import com.shane.orchestragent.biz.agent.Agent;
import com.shane.orchestragent.biz.agent.flow.*;
import com.shane.orchestragent.biz.context.StrategyContext;
import com.shane.orchestragent.biz.context.impl.DefaultStrategyContext;
import com.shane.orchestragent.biz.flow.MultipleChatStrategyFlow;
import com.shane.orchestragent.biz.flow.ReActStrategyFlow;
import com.shane.orchestragent.biz.model.agent.LlmConfig;
import com.shane.orchestragent.common.enums.AgentTypeEnum;
import com.shane.orchestragent.biz.service.LlmService;
import com.shane.orchestragent.biz.service.impl.LlmServiceImpl;
import com.shane.orchestragent.common.constant.PropertyKeys;
import com.shane.orchestragent.common.exception.BizException;
import com.shane.orchestragent.integration.llm.LlmClient;
import com.shane.orchestragent.integration.llm.impl.OpenAiCompatibleLlmClient;
import com.shane.orchestragent.prompt.render.TemplateRender;
import com.shane.orchestragent.prompt.service.PromptService;
import com.shane.orchestragent.prompt.service.impl.PromptServiceImpl;
import com.shane.orchestragent.repository.model.StrategyWorkerDO;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.shane.orchestragent.biz.flow.StrategyFlowFactory;
import com.shane.orchestragent.biz.manager.AgentManager;
import com.shane.orchestragent.biz.manager.impl.AgentManagerImpl;
import com.shane.orchestragent.biz.model.request.RecommendRequestVO;
import com.shane.orchestragent.biz.model.response.RecommendResponseVO;
import com.shane.orchestragent.biz.recorder.impl.FlowExecutionRecorderImpl;
import com.shane.orchestragent.biz.service.FlowCacheService;
import com.shane.orchestragent.biz.service.FlowService;
import com.shane.orchestragent.biz.service.SseService;
import com.shane.orchestragent.biz.service.impl.FlowCacheServiceImpl;
import com.shane.orchestragent.biz.service.impl.FlowServiceImpl;
import com.shane.orchestragent.repository.StrategyConfigRepository;
import com.shane.orchestragent.repository.impl.StrategyConfigRepositoryImpl;
import com.shane.orchestragent.repository.model.StrategyConfigDO;

/**
 * OrchestrAgent 全链路协同与自愈闭环核心测试用例
 *
 * @author Shane
 */
public class OrchestrAgentFlowTest {

    private LlmClient llmClient;
    private LlmService llmService;
    private PromptService promptService;
    private LlmConfig defaultConfig;

    @BeforeEach
    public void setup() {
        this.llmClient = new OpenAiCompatibleLlmClient();
        this.llmService = new LlmServiceImpl(llmClient);
        this.promptService = new PromptServiceImpl(new TemplateRender());
        this.defaultConfig = LlmConfig.builder().model("gpt-4o").nodeWaitTime(5000L).build();
    }

    @Test
    @DisplayName("测试正常全流程: Planner -> Conductor -> Worker -> Evaluator(通过) -> Reporter")
    public void testFullFlowSuccess() throws BizException {
        StrategyContext context = new DefaultStrategyContext();
        context.setProperties(PropertyKeys.KEY_STRATEGY_TARGET, "查询北京到上海的最优航班并给出建议");

        Map<AgentTypeEnum, Agent> systemAgents = new HashMap<>();
        systemAgents.put(AgentTypeEnum.PLANNER, new PlannerAgent(defaultConfig, llmService, promptService));
        systemAgents.put(AgentTypeEnum.CONDUCTOR, new ConductorAgent(defaultConfig, llmService, promptService));
        systemAgents.put(AgentTypeEnum.EVALUATOR, new EvaluatorAgent(defaultConfig, llmService, promptService));
        systemAgents.put(AgentTypeEnum.REPORTER, new ReporterAgent(defaultConfig, llmService, promptService));

        ReActStrategyFlow flow = new ReActStrategyFlow(context, systemAgents, 10);

        StrategyWorkerDO workerDO = StrategyWorkerDO.builder()
                .name("search_worker")
                .description("航班检索专精工作节点")
                .prompt("负责根据出发地与目的地检索航班实时信息")
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

        // 构造一个在 Evaluator 阶段判定不合格的特定 Mock Agent
        EvaluatorAgent strictEvaluator = new EvaluatorAgent(defaultConfig, llmService, promptService) {
            @Override
            public com.shane.orchestragent.biz.model.agent.AgentResult execute(StrategyContext ctx) {
                return new com.shane.orchestragent.biz.model.agent.LlmAgentResult(
                        "{\"pass\": false, \"score\": 60, \"critique\": \"缺少免费行李额详细说明\", \"suggestedRemedy\": \"补充查询行李额\"}",
                        null, null
                );
            }
        };

        Map<AgentTypeEnum, Agent> systemAgents = new HashMap<>();
        systemAgents.put(AgentTypeEnum.PLANNER, new PlannerAgent(defaultConfig, llmService, promptService));
        systemAgents.put(AgentTypeEnum.CONDUCTOR, new ConductorAgent(defaultConfig, llmService, promptService));
        systemAgents.put(AgentTypeEnum.EVALUATOR, strictEvaluator);
        systemAgents.put(AgentTypeEnum.REPORTER, new ReporterAgent(defaultConfig, llmService, promptService));

        ReActStrategyFlow flow = new ReActStrategyFlow(context, systemAgents, 10);

        StrategyWorkerDO workerDO = StrategyWorkerDO.builder()
                .name("search_worker")
                .description("航班检索")
                .build();
        flow.addAgent(new WorkerAgent(workerDO, defaultConfig, llmService, promptService));

        String result = flow.execute();

        Assertions.assertNotNull(result);
        // 验证重试次数达到了上限 2 次自愈尝试
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

        RouterAgent directRouter = new RouterAgent(defaultConfig, llmService, promptService) {
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
        systemAgents.put(AgentTypeEnum.PLANNER, new PlannerAgent(defaultConfig, llmService, promptService));
        systemAgents.put(AgentTypeEnum.CONDUCTOR, new ConductorAgent(defaultConfig, llmService, promptService));
        systemAgents.put(AgentTypeEnum.EVALUATOR, new EvaluatorAgent(defaultConfig, llmService, promptService));
        systemAgents.put(AgentTypeEnum.REPORTER, new ReporterAgent(defaultConfig, llmService, promptService));

        MultipleChatStrategyFlow flow = new MultipleChatStrategyFlow(context, systemAgents, 10);
        String reply = flow.execute();

        Assertions.assertEquals("您好！我是 OrchestrAgent，很高兴为您服务。", reply);
        // 验证没有进入 Planner
        Assertions.assertNull(context.getPlanResult());
    }

    @Test
    @DisplayName("测试 AgentManager 整体生命周期调度与结果缓存")
    public void testAgentManagerProcessAndCache() throws Exception {
        FlowService flowService = new FlowServiceImpl();
        FlowCacheService flowCacheService = new FlowCacheServiceImpl();
        FlowExecutionRecorderImpl recorder = new FlowExecutionRecorderImpl(flowService);

        StrategyFlowFactory flowFactory = new StrategyFlowFactory(llmService, promptService, flowService, recorder);
        StrategyConfigRepositoryImpl strategyRepo = new StrategyConfigRepositoryImpl();
        StrategyConfigDO configDO = StrategyConfigDO.builder()
                .strategyId("biz_travel")
                .name("商务差旅推荐策略")
                .maxStep(10)
                .flowTopologyType("REACT")
                .workers(List.of(StrategyWorkerDO.builder()
                        .name("search_worker")
                        .description("航班检索")
                        .build()))
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

        // 轮询等待虚拟线程完成流程执行 (不超过 3 秒)
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

        // 再次请求同一个 flowId，验证直接命中结果缓存
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
    @DisplayName("测试 ConductorAgent 多轮提示词模板动态渲染")
    public void testConductorAgentMultiRoundMessageRendering() throws BizException {
        ConductorAgent conductor = new ConductorAgent(defaultConfig, llmService, promptService);
        Map<String, Object> properties = new HashMap<>();
        properties.put("test_key", "test_val");

        // 1. Planner 产物渲染
        String plannerPrompt = conductor.renderConductorPlannerUserPrompt("{\"steps\": []}", properties);
        Assertions.assertTrue(plannerPrompt.contains("【任务规划步骤】"));
        Assertions.assertTrue(plannerPrompt.contains("{\"steps\": []}"));

        // 2. Worker 产物渲染
        String workerPrompt = conductor.renderConductorWorkerUserPrompt("search_worker", "已查询到3个航班", properties);
        Assertions.assertTrue(workerPrompt.contains("search_worker"));
        Assertions.assertTrue(workerPrompt.contains("已查询到3个航班"));

        // 3. Tool 产物渲染
        String toolPrompt = conductor.renderConductorToolUserPrompt("weather_tool", "晴天 25度", properties);
        Assertions.assertTrue(toolPrompt.contains("weather_tool"));
        Assertions.assertTrue(toolPrompt.contains("晴天 25度"));

        // 4. Evaluator 产物渲染
        String evalPrompt = conductor.renderConductorEvaluatorUserPrompt("{\"pass\": false, \"critique\": \"需要核实价格\"}", properties);
        Assertions.assertTrue(evalPrompt.contains("整改要求"));
        Assertions.assertTrue(evalPrompt.contains("需要核实价格"));
    }

    @Test
    @DisplayName("测试 ToolAgent 与 RagAgent 执行与历史归档")
    public void testToolAgentAndRagAgentExecution() throws BizException {
        StrategyContext context = new DefaultStrategyContext();
        context.setProperties(PropertyKeys.KEY_STRATEGY_TARGET, "检索差旅退改标准");
        context.setNextAgentRequest(Map.of("city", "Beijing"));

        // 1. ToolAgent
        com.shane.orchestragent.repository.model.ToolConfigDO toolConfig = com.shane.orchestragent.repository.model.ToolConfigDO.builder()
                .name("test_tool")
                .description("测试外部工具")
                .build();
        ToolAgent toolAgent = new ToolAgent(toolConfig);
        com.shane.orchestragent.biz.model.agent.AgentResult toolResult = toolAgent.execute(context);
        Assertions.assertNotNull(toolResult);
        Assertions.assertTrue(toolResult.getOutput().contains("test_tool"));
        Assertions.assertTrue(toolResult.getOutput().contains("Beijing"));

        // 2. RagAgent
        RagAgent ragAgent = new RagAgent("test_rag", "测试知识检索");
        com.shane.orchestragent.biz.model.agent.AgentResult ragResult = ragAgent.execute(context);
        Assertions.assertNotNull(ragResult);
        Assertions.assertTrue(ragResult.getOutput().contains("知识库匹配内容"));
        Assertions.assertTrue(ragResult.getOutput().contains("检索差旅退改标准"));

        // 3. 验证历史记录正确追加
        Assertions.assertEquals(2, context.getChatMessages().size());
        Assertions.assertEquals(AgentTypeEnum.TOOL, context.getChatMessages().get(0).getAgentType());
        Assertions.assertEquals(AgentTypeEnum.RAG, context.getChatMessages().get(1).getAgentType());
    }

    @Test
    @DisplayName("测试 DefaultChatStreamResponseBuilder 流式增量聚合与构建")
    public void testDefaultChatStreamResponseBuilder() throws Exception {
        com.shane.orchestragent.biz.stream.impl.DefaultChatStreamResponseBuilder builder =
                new com.shane.orchestragent.biz.stream.impl.DefaultChatStreamResponseBuilder();

        builder.append("{\"id\":\"1\",\"choices\":[{\"delta\":{\"content\":\"Hello \",\"reasoning_content\":\"Think 1 \"}}]}");
        builder.append("{\"id\":\"1\",\"choices\":[{\"delta\":{\"content\":\"World!\",\"reasoning_content\":\"Think 2\"}}]}");

        com.shane.orchestragent.integration.llm.model.ChatResponseVO response = builder.build();
        Assertions.assertNotNull(response);
        Assertions.assertEquals("Hello World!", response.getFirstMessage().getContent());
        Assertions.assertEquals("Think 1 Think 2", response.getFirstMessage().getReasoningContent());
    }

    @Test
    @DisplayName("测试 Context 分层设计: AgentContext (traceId, isStream, properties) 与 StrategyContext (flowId, sessionId, userId)")
    public void testAgentContextAndStrategyContextHierarchy() throws BizException {
        // 1. 测试独立轻量级 DefaultAgentContext
        com.shane.orchestragent.biz.context.impl.DefaultAgentContext baseContext =
                new com.shane.orchestragent.biz.context.impl.DefaultAgentContext("trace-12345", true, Map.of("action", "ping"));
        Assertions.assertEquals("trace-12345", baseContext.getTraceId());
        Assertions.assertTrue(baseContext.isStream());
        Assertions.assertNotNull(baseContext.getAgentRequest());
        baseContext.addProperty("k1", "v1");
        Assertions.assertEquals("v1", baseContext.getProperty("k1"));

        // 单独让 ToolAgent 在基础 AgentContext 下运行
        com.shane.orchestragent.repository.model.ToolConfigDO toolConfig = com.shane.orchestragent.repository.model.ToolConfigDO.builder()
                .name("standalone_tool")
                .description("单节点独立工具")
                .build();
        ToolAgent standaloneTool = new ToolAgent(toolConfig);
        com.shane.orchestragent.biz.model.agent.AgentResult standaloneResult = standaloneTool.execute(baseContext);
        Assertions.assertNotNull(standaloneResult);
        Assertions.assertTrue(standaloneResult.getOutput().contains("standalone_tool"));
        Assertions.assertTrue(standaloneResult.getOutput().contains("ping"));

        // 2. 测试策略上下文 DefaultStrategyContext 继承与扩展
        com.shane.orchestragent.biz.model.request.RecommendRequestVO requestVO =
                com.shane.orchestragent.biz.model.request.RecommendRequestVO.builder()
                        .flowId("flow-999")
                        .sessionId("session-888")
                        .traceId("trace-777")
                        .stream(false)
                        .build();

        DefaultStrategyContext strategyContext = new DefaultStrategyContext(null, "flow-999", "session-888", requestVO);
        strategyContext.setUserId("user-007");
        strategyContext.setNextAgentRequest(Map.of("step", "execute_task"));

        // 验证基础 AgentContext 属性
        Assertions.assertEquals("trace-777", strategyContext.getTraceId());
        Assertions.assertFalse(strategyContext.isStream());
        Assertions.assertEquals(Map.of("step", "execute_task"), strategyContext.getAgentRequest());

        // 验证 StrategyContext 专属属性
        Assertions.assertEquals("flow-999", strategyContext.getFlowId());
        Assertions.assertEquals("session-888", strategyContext.getSessionId());
        Assertions.assertEquals("user-007", strategyContext.getUserId());

        // 验证 Agent 接口 registerExtension
        Agent<StrategyContext> ragAgent = new RagAgent("rag", "desc");
        ragAgent.registerExtension((agent, agentResult, agentCtx) -> agentResult);
        Assertions.assertEquals(AgentTypeEnum.RAG, ragAgent.getType());
    }
}
