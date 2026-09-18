package com.shane.orchestragent.test.web;

import com.shane.orchestragent.biz.flow.StrategyFlowFactory;
import com.shane.orchestragent.biz.manager.AgentManager;
import com.shane.orchestragent.biz.manager.impl.AgentManagerImpl;
import com.shane.orchestragent.biz.recorder.impl.FlowExecutionRecorderImpl;
import com.shane.orchestragent.biz.service.FlowCacheService;
import com.shane.orchestragent.biz.service.FlowService;
import com.shane.orchestragent.biz.service.LlmService;
import com.shane.orchestragent.biz.service.SseService;
import com.shane.orchestragent.biz.service.impl.FlowCacheServiceImpl;
import com.shane.orchestragent.biz.service.impl.FlowServiceImpl;
import com.shane.orchestragent.biz.service.impl.LlmServiceImpl;
import com.shane.orchestragent.biz.service.impl.SseServiceImpl;
import com.shane.orchestragent.biz.sse.SseEmitterUTF8;
import com.shane.orchestragent.common.enums.FlowStateEnum;
import com.shane.orchestragent.common.exception.BizException;
import com.shane.orchestragent.common.model.BaseResult;
import com.shane.orchestragent.test.support.MockLlmClient;
import com.shane.orchestragent.memory.context.impl.CaffeineMemoryContext;
import com.shane.orchestragent.prompt.render.TemplateRender;
import com.shane.orchestragent.prompt.service.PromptService;
import com.shane.orchestragent.prompt.service.impl.PromptServiceImpl;
import com.shane.orchestragent.repository.StrategyConfigRepository;
import com.shane.orchestragent.repository.impl.StrategyConfigRepositoryImpl;
import com.shane.orchestragent.repository.model.StrategyConfigDO;
import com.shane.orchestragent.web.controller.OrchestrAgentController;
import com.shane.orchestragent.web.converter.AgentApiMapping;
import com.shane.orchestragent.web.dto.AgentChatRequestDTO;
import com.shane.orchestragent.web.dto.AgentInvokeRequestDTO;
import com.shane.orchestragent.web.dto.AgentInvokeResponseDTO;
import com.shane.orchestragent.repository.client.AgentConfigApiClient;
import com.shane.orchestragent.repository.impl.AgentConfigRepositoryImpl;
import com.shane.orchestragent.repository.model.AgentConfigDO;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

/**
 * OrchestrAgentController 核心方法单元测试
 * 针对 Controller 的 invoke (异步)、chat (SSE 流式)、testInvoke (同步) 三个端点进行全面测试验证
 *
 * @author Shane
 */
public class OrchestrAgentControllerTest {

    private OrchestrAgentController controller;
    private FlowService flowService;

    @BeforeEach
    public void setup() {
        MockLlmClient llmClient = new MockLlmClient();
        LlmService llmService = new LlmServiceImpl(llmClient);

        AgentConfigRepositoryImpl agentRepo = new AgentConfigRepositoryImpl();
        List<AgentConfigDO> systemAgents = List.of(
                AgentConfigDO.builder()
                        .name("PLANNER")
                        .model("Deepseek")
                        .systemPrompt("# PlannerAgent\n请分析目标，输出规划步骤: {\"steps\": [{\"step\": 1, \"agent\": \"search_worker\", \"description\": \"查询\"}]}")
                        .userPrompt("目标: ${strategy_target}")
                        .build(),
                AgentConfigDO.builder()
                        .name("CONDUCTOR")
                        .model("Deepseek")
                        .systemPrompt("# ConductorAgent\n调度下一步。输出: {\"next\": \"FINISH\", \"request\": {}}")
                        .userPrompt("默认调度指令")
                        .build(),
                AgentConfigDO.builder()
                        .name("EVALUATOR")
                        .model("Deepseek")
                        .systemPrompt("# EvaluatorAgent\n验收输出: {\"pass\": true, \"score\": 100, \"critique\": \"\", \"suggestedRemedy\": \"\"}")
                        .userPrompt("【目标】: ${strategy_target}\n【执行结果汇总】: ${allAgentOutputs}")
                        .build(),
                AgentConfigDO.builder()
                        .name("REPORTER")
                        .model("Deepseek")
                        .systemPrompt("# ReporterAgent\n汇总结果")
                        .userPrompt("请根据执行总结输出最终回复:\n${flowExecutionSummary}")
                        .build(),
                AgentConfigDO.builder()
                        .name("ROUTER")
                        .model("Deepseek")
                        .systemPrompt("# RouterAgent\n分析意图: {\"handoffToPlanner\": true, \"reply\": \"\"}")
                        .userPrompt("用户诉求: ${strategy_target}")
                        .build(),
                AgentConfigDO.builder()
                        .name("CHAT_SUMMARY")
                        .model("Deepseek")
                        .systemPrompt("# ChatSummaryAgent\n摘要压缩")
                        .build(),
                AgentConfigDO.builder()
                        .name("search_worker")
                        .code("search_worker")
                        .agentType("WORKER")
                        .model("Deepseek")
                        .systemPrompt("负责检索航班实时信息")
                        .build()
        );
        org.springframework.test.util.ReflectionTestUtils.setField(agentRepo, "agentConfigApiClient", (AgentConfigApiClient) () -> systemAgents);
        try {
            agentRepo.afterPropertiesSet();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        PromptService promptService = new PromptServiceImpl(new TemplateRender());

        flowService = new FlowServiceImpl();
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
        try {
            strategyRepo.afterPropertiesSet();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        AgentManagerImpl agentManagerImpl = new AgentManagerImpl(flowFactory, strategyRepo, flowService, flowCacheService);
        CaffeineMemoryContext memoryContext = new CaffeineMemoryContext();
        SseService sseService = new SseServiceImpl();
        agentManagerImpl.setMemoryContext(memoryContext);
        agentManagerImpl.setSseService(sseService);

        AgentApiMapping apiMapping = AgentApiMapping.INSTANCE;
        this.controller = new OrchestrAgentController(apiMapping, agentManagerImpl);
    }

    @Test
    @DisplayName("测试 invoke 异步调用: 直接触发 Plan+ReAct 流程并在后台异步运行，立即返回 flowId")
    public void testInvokeAsyncSuccess() throws Exception {
        AgentInvokeRequestDTO request = AgentInvokeRequestDTO.builder()
                .strategyId("biz_travel")
                .message("预订北京到上海的最优航班")
                .sessionId("test-session-invoke-1")
                .userId("user-001")
                .build();

        BaseResult<AgentInvokeResponseDTO> result = controller.invoke(request);
        Assertions.assertNotNull(result);
        Assertions.assertTrue(result.isSuccess());

        AgentInvokeResponseDTO response = result.getData();
        Assertions.assertNotNull(response);
        Assertions.assertNotNull(response.getFlowId());
        Assertions.assertEquals("test-session-invoke-1", response.getSessionId());

        String flowId = response.getFlowId();

        // 轮询等待后台虚拟线程完成流程
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < 3000) {
            FlowStateEnum state = flowService.getState(flowId);
            if (state == FlowStateEnum.FINISHED || state == FlowStateEnum.ERROR) {
                break;
            }
            Thread.sleep(50);
        }

        Assertions.assertEquals(FlowStateEnum.FINISHED, flowService.getState(flowId));
        String flowResult = flowService.getResult(flowId);
        Assertions.assertNotNull(flowResult);
    }

    @Test
    @DisplayName("测试 chat 流式会话: 调用 Router 进行意图识别分流，并返回 SseEmitterUTF8")
    public void testChatSseStreamSuccess() throws BizException {
        AgentChatRequestDTO request = AgentChatRequestDTO.builder()
                .strategyId("biz_travel")
                .message("请问有什么差旅推荐？")
                .sessionId("test-session-chat-1")
                .userId("user-002")
                .build();

        SseEmitterUTF8 emitter = controller.chat(request);
        Assertions.assertNotNull(emitter);
    }

    @Test
    @DisplayName("测试 testInvoke 同步测试接口: 同步阻塞触发 ReAct 循环，返回完整产出与质检评估")
    public void testTestInvokeSyncSuccess() throws BizException {
        AgentInvokeRequestDTO request = AgentInvokeRequestDTO.builder()
                .strategyId("biz_travel")
                .message("预订北京到上海的最优航班")
                .sessionId("test-session-testInvoke-1")
                .userId("user-003")
                .build();

        BaseResult<AgentInvokeResponseDTO> result = controller.testInvoke(request);
        Assertions.assertNotNull(result);
        Assertions.assertTrue(result.isSuccess());

        AgentInvokeResponseDTO response = result.getData();
        Assertions.assertNotNull(response);
        Assertions.assertEquals("test-session-testInvoke-1", response.getSessionId());
        Assertions.assertNotNull(response.getFlowId());
        Assertions.assertNotNull(response.getReply());
        Assertions.assertTrue(response.getReply().contains("OrchestrAgent") || response.getReply().contains("完成"));
        Assertions.assertEquals("FINISHED", response.getState());
        Assertions.assertNotNull(response.getEvaluation());
        Assertions.assertTrue(response.getEvaluation().isPass());
        Assertions.assertNotNull(response.getCostMs());
        Assertions.assertTrue(response.getCostMs() >= 0);
    }

    @Test
    @DisplayName("测试 testInvoke 业务参数兼容: 支持从 bizData.query 提取用户意图并同步执行")
    public void testTestInvokeWithBizDataQuery() throws BizException {
        AgentInvokeRequestDTO request = AgentInvokeRequestDTO.builder()
                .strategyId("biz_travel")
                .bizData(Map.of("query", "查询明天上午飞往深圳的航班"))
                .sessionId("test-session-testInvoke-2")
                .build();

        BaseResult<AgentInvokeResponseDTO> result = controller.testInvoke(request);
        Assertions.assertNotNull(result);
        Assertions.assertTrue(result.isSuccess());
        Assertions.assertNotNull(result.getData().getReply());
    }

    @Test
    @DisplayName("测试入参边界保护: 空参数调用 invoke / chat / testInvoke 抛出 BizException")
    public void testInputValidation() {
        BizException ex1 = Assertions.assertThrows(BizException.class, () -> controller.invoke(null));
        Assertions.assertEquals("REQUEST_NULL", ex1.getErrorCode());

        BizException ex2 = Assertions.assertThrows(BizException.class, () -> controller.chat(null));
        Assertions.assertEquals("REQUEST_NULL", ex2.getErrorCode());

        BizException ex3 = Assertions.assertThrows(BizException.class, () -> controller.testInvoke(null));
        Assertions.assertEquals("REQUEST_NULL", ex3.getErrorCode());
    }
}
