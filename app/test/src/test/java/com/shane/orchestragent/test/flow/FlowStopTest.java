package com.shane.orchestragent.test.flow;

import com.shane.orchestragent.biz.agent.Agent;
import com.shane.orchestragent.biz.agent.base.BaseLlmAgent;
import com.shane.orchestragent.biz.agent.flow.*;
import com.shane.orchestragent.biz.context.StrategyContext;
import com.shane.orchestragent.biz.context.impl.DefaultStrategyContext;
import com.shane.orchestragent.biz.flow.ReActStrategyFlow;
import com.shane.orchestragent.biz.flow.StrategyFlow;
import com.shane.orchestragent.biz.flow.StrategyFlowFactory;
import com.shane.orchestragent.biz.flow.store.FlowStore;
import com.shane.orchestragent.biz.manager.AgentManager;
import com.shane.orchestragent.biz.manager.impl.AgentManagerImpl;
import com.shane.orchestragent.biz.model.agent.AgentResult;
import com.shane.orchestragent.biz.model.agent.LlmConfig;
import com.shane.orchestragent.biz.model.vo.AgentInvokeRequestVO;
import com.shane.orchestragent.biz.model.vo.AgentInvokeResponseVO;
import com.shane.orchestragent.biz.recorder.impl.FlowExecutionRecorderImpl;
import com.shane.orchestragent.biz.service.FlowService;
import com.shane.orchestragent.biz.service.LlmService;
import com.shane.orchestragent.biz.service.impl.FlowServiceImpl;
import com.shane.orchestragent.biz.service.impl.LlmServiceImpl;
import com.shane.orchestragent.common.constant.PropertyKeys;
import com.shane.orchestragent.common.enums.AgentTypeEnum;
import com.shane.orchestragent.common.enums.FlowStateEnum;
import com.shane.orchestragent.common.exception.BizException;
import com.shane.orchestragent.integration.llm.LlmCallback;
import com.shane.orchestragent.integration.llm.LlmClient;
import com.shane.orchestragent.integration.llm.LlmSseDataListener;
import com.shane.orchestragent.integration.llm.model.ChatMessageDTO;
import com.shane.orchestragent.integration.llm.model.ChatRequestDTO;
import com.shane.orchestragent.integration.llm.model.ChatResponseVO;
import com.shane.orchestragent.prompt.render.TemplateRender;
import com.shane.orchestragent.prompt.service.PromptService;
import com.shane.orchestragent.prompt.service.impl.PromptServiceImpl;
import com.shane.orchestragent.repository.client.AgentConfigApiClient;
import com.shane.orchestragent.repository.client.StrategyConfigApiClient;
import com.shane.orchestragent.repository.impl.AgentConfigRepositoryImpl;
import com.shane.orchestragent.repository.impl.StrategyConfigRepositoryImpl;
import com.shane.orchestragent.repository.model.AgentConfigDO;
import com.shane.orchestragent.repository.model.StrategyConfigDO;
import com.shane.orchestragent.test.support.MockLlmClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 流程主动终止与智能体取消机制核心测试用例
 *
 * @author Shane
 */
public class FlowStopTest {

    private MockLlmClient mockLlmClient;
    private LlmService llmService;
    private PromptService promptService;
    private FlowService flowService;
    private FlowExecutionRecorderImpl recorder;
    private StrategyFlowFactory flowFactory;
    private StrategyConfigRepositoryImpl strategyRepo;
    private AgentConfigRepositoryImpl agentRepo;

    @BeforeEach
    public void setup() throws Exception {
        this.mockLlmClient = new MockLlmClient();
        this.llmService = new LlmServiceImpl(mockLlmClient);
        this.promptService = new PromptServiceImpl(new TemplateRender());
        this.flowService = new FlowServiceImpl();
        this.recorder = new FlowExecutionRecorderImpl(flowService);
        this.flowFactory = new StrategyFlowFactory(llmService, promptService, flowService, recorder);

        this.agentRepo = new AgentConfigRepositoryImpl();
        List<AgentConfigDO> systemAgents = List.of(
                AgentConfigDO.builder().name("PLANNER").code("PLANNER").model("gpt-4o")
                        .systemPrompt("# Planner\n{\"steps\": [{\"step\": 1, \"agent\": \"search_worker\", \"description\": \"查询\"}]}")
                        .userPrompt("目标: ${strategy_target}").build(),
                AgentConfigDO.builder().name("CONDUCTOR").code("CONDUCTOR").model("gpt-4o")
                        .systemPrompt("# Conductor\n{\"next\": \"FINISH\", \"request\": {}}")
                        .userPrompt("调度").build(),
                AgentConfigDO.builder().name("EVALUATOR").code("EVALUATOR").model("gpt-4o")
                        .systemPrompt("# Evaluator\n{\"pass\": true, \"score\": 100, \"critique\": \"\", \"suggestedRemedy\": \"\"}")
                        .userPrompt("目标: ${strategy_target}").build(),
                AgentConfigDO.builder().name("REPORTER").code("REPORTER").model("gpt-4o")
                        .systemPrompt("# Reporter\n汇总输出")
                        .userPrompt("产出: ${flowExecutionSummary}").build(),
                AgentConfigDO.builder().name("ROUTER").code("ROUTER").model("gpt-4o")
                        .systemPrompt("# Router\n{\"handoffToPlanner\": true, \"reply\": \"\"}")
                        .userPrompt("目标: ${strategy_target}").build(),
                AgentConfigDO.builder().name("search_worker").code("search_worker").agentType("WORKER").model("gpt-4o")
                        .systemPrompt("搜索结果").build()
        );
        ReflectionTestUtils.setField(agentRepo, "agentConfigApiClient", (AgentConfigApiClient) () -> systemAgents);
        agentRepo.afterPropertiesSet();
        flowFactory.setAgentConfigRepository(agentRepo);

        this.strategyRepo = new StrategyConfigRepositoryImpl();
        StrategyConfigDO configDO = StrategyConfigDO.builder()
                .strategyId("test_stop_strat")
                .code("test_stop_strat")
                .name("测试终止策略")
                .maxStep(10)
                .flowTopologyType("REACT")
                .workerCodes(List.of("search_worker"))
                .build();
        ReflectionTestUtils.setField(strategyRepo, "strategyConfigApiClient", (StrategyConfigApiClient) () -> List.of(configDO));
        strategyRepo.afterPropertiesSet();
    }

    @Test
    @DisplayName("测试直接调用 strategyFlow.stop() 确保流程终态为 STOPPED 且不抛出步数超限异常")
    public void testDirectFlowStop() {
        StrategyContext context = new DefaultStrategyContext();
        context.setProperties(PropertyKeys.KEY_STRATEGY_TARGET, "测试主动停止");

        LlmConfig defaultLlmConfig = LlmConfig.builder()
                .model("test-model")
                .temperature(0.7)
                .maxTokens(1000)
                .nodeWaitTime(5000L)
                .systemPrompt("你是一个规划器")
                .userPrompt("目标: {{strategy_target}}")
                .build();

        Map<AgentTypeEnum, Agent> systemAgents = new HashMap<>();
        systemAgents.put(AgentTypeEnum.PLANNER, new PlannerAgent(defaultLlmConfig, llmService, promptService));
        systemAgents.put(AgentTypeEnum.CONDUCTOR, new ConductorAgent(defaultLlmConfig, llmService, promptService));
        systemAgents.put(AgentTypeEnum.EVALUATOR, new EvaluatorAgent(defaultLlmConfig, llmService, promptService));
        systemAgents.put(AgentTypeEnum.REPORTER, new ReporterAgent(defaultLlmConfig, llmService, promptService));

        ReActStrategyFlow flow = new ReActStrategyFlow(context, systemAgents, 10);
        flow.setFlowService(flowService);

        // 立即调用 stop
        flow.stop();

        assertTrue(flow.isStopped());
        assertEquals(FlowStateEnum.STOPPED, flow.getState());
        assertEquals(FlowStateEnum.STOPPED, flowService.getState(flow.getFlowId()));

        // 执行流程，验证状态依然为 STOPPED，不被覆盖为 ERROR 或 FINISHED
        assertDoesNotThrow(flow::execute);
        assertNull(context.getRecommendResult());

        assertEquals(FlowStateEnum.STOPPED, flow.getState());
        assertEquals(FlowStateEnum.STOPPED, flowService.getState(flow.getFlowId()));
    }

    @Test
    @DisplayName("测试 AgentManager.stopFlow(flowId) 联动 FlowStore 终止工作流")
    public void testAgentManagerStopFlow() throws Exception {
        AgentManager agentManager = new AgentManagerImpl(flowFactory, strategyRepo, flowService);

        AgentInvokeRequestVO request = AgentInvokeRequestVO.builder()
                .strategyCode("test_stop_strat")
                .message("测试终止请求")
                .build();

        AgentInvokeResponseVO response = agentManager.invoke(request);
        assertNotNull(response);
        String flowId = response.getFlowId();
        assertNotNull(flowId);

        StrategyFlow flowInstance = FlowStore.get(flowId);
        assertNotNull(flowInstance);

        // 主动终止
        boolean stopSuccess = agentManager.stopFlow(flowId);
        assertTrue(stopSuccess);
        assertTrue(flowInstance.isStopped());
        assertEquals(FlowStateEnum.STOPPED, flowInstance.getState());
        assertEquals(FlowStateEnum.STOPPED, flowService.getState(flowId));
    }

    @Test
    @DisplayName("测试 BaseLlmAgent 在途异步请求主动打断 (In-flight Cancellation)")
    public void testLlmAgentInFlightStop() throws Exception {
        CountDownLatch callStarted = new CountDownLatch(1);
        CountDownLatch cancelLatch = new CountDownLatch(1);

        // 自定义可取消的异步 LlmClient
        LlmClient hangingClient = new LlmClient() {
            @Override
            public CompletableFuture<ChatResponseVO> asyncCall(ChatRequestDTO request, LlmCallback callback) throws BizException {
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public CompletableFuture<Void> asyncCall(String requestJson, LlmSseDataListener listener) throws BizException {
                CompletableFuture<Void> future = new CompletableFuture<>();
                future.whenComplete((r, ex) -> {
                    if (future.isCancelled()) {
                        cancelLatch.countDown();
                    }
                });
                callStarted.countDown();
                return future;
            }

            @Override
            public ChatResponseVO chat(ChatRequestDTO request) throws BizException {
                return null;
            }
        };

        LlmService hangingLlmService = new LlmServiceImpl(hangingClient);

        LlmConfig config = LlmConfig.builder()
                .model("test-model")
                .nodeWaitTime(5000L)
                .systemPrompt("sys")
                .userPrompt("usr")
                .build();

        BaseLlmAgent<StrategyContext> testAgent = new BaseLlmAgent<>("TEST_AGENT", "测试智能体", AgentTypeEnum.WORKER, config, hangingLlmService, promptService) {
            @Override
            protected com.shane.orchestragent.integration.llm.model.SystemChatMessageDTO buildSystemPrompt(StrategyContext context) {
                return new com.shane.orchestragent.integration.llm.model.SystemChatMessageDTO("system");
            }

            @Override
            protected List<ChatMessageDTO> buildMessages(StrategyContext context) {
                return Collections.emptyList();
            }
        };

        StrategyContext context = new DefaultStrategyContext();
        AtomicReference<AgentResult> resultRef = new AtomicReference<>();

        Thread workerThread = new Thread(() -> {
            try {
                AgentResult result = testAgent.execute(context);
                resultRef.set(result);
            } catch (Exception e) {
                // 不应抛出异常
            }
        });
        workerThread.start();

        // 等待 LLM 请求发起
        assertTrue(callStarted.await(2, TimeUnit.SECONDS));

        // 主动终止智能体
        testAgent.stop();

        // 验证底层 future 收到 cancel
        assertTrue(cancelLatch.await(2, TimeUnit.SECONDS));
        assertTrue(testAgent.isStopped());

        workerThread.join(2000);
        assertFalse(workerThread.isAlive());
        assertNull(resultRef.get());
    }

    @Test
    @DisplayName("测试流程执行中途主动停止通过 FlowStoppedException 向上击穿阻断且终态保持 STOPPED")
    public void testMidFlightFlowStopWithException() {
        StrategyContext context = new DefaultStrategyContext();
        context.setProperties(PropertyKeys.KEY_STRATEGY_TARGET, "测试中途异常阻断");

        LlmConfig plannerConfig = LlmConfig.builder()
                .model("test-model")
                .temperature(0.7)
                .maxTokens(1000)
                .nodeWaitTime(5000L)
                .systemPrompt("你是一个任务规划专家 (PlannerAgent)")
                .userPrompt("目标: {{strategy_target}}")
                .build();

        LlmConfig defaultLlmConfig = LlmConfig.builder()
                .model("test-model")
                .temperature(0.7)
                .maxTokens(1000)
                .nodeWaitTime(5000L)
                .systemPrompt("调度与指挥专家 (ConductorAgent)")
                .userPrompt("目标: {{strategy_target}}")
                .build();

        Map<AgentTypeEnum, Agent> systemAgents = new HashMap<>();
        systemAgents.put(AgentTypeEnum.PLANNER, new PlannerAgent(plannerConfig, llmService, promptService));
        systemAgents.put(AgentTypeEnum.EVALUATOR, new EvaluatorAgent(defaultLlmConfig, llmService, promptService));
        systemAgents.put(AgentTypeEnum.REPORTER, new ReporterAgent(defaultLlmConfig, llmService, promptService));

        AtomicReference<ReActStrategyFlow> flowRef = new AtomicReference<>();

        // 自定义 Conductor: 在执行指挥调度决策时模拟外部调用 stop
        ConductorAgent customConductor = new ConductorAgent(defaultLlmConfig, llmService, promptService) {
            @Override
            public AgentResult execute(StrategyContext ctx) throws BizException {
                if (flowRef.get() != null) {
                    flowRef.get().stop();
                }
                return super.execute(ctx);
            }
        };
        systemAgents.put(AgentTypeEnum.CONDUCTOR, customConductor);

        ReActStrategyFlow flow = new ReActStrategyFlow(context, systemAgents, 10);
        flow.setFlowService(flowService);
        flowRef.set(flow);

        assertDoesNotThrow(flow::execute);
        assertNull(context.getRecommendResult());

        assertTrue(flow.isStopped());
        assertEquals(FlowStateEnum.STOPPED, flow.getState());
        assertEquals(FlowStateEnum.STOPPED, flowService.getState(flow.getFlowId()));
    }
}
