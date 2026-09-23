package com.shane.orchestragent.test.repository;

import com.shane.orchestragent.biz.flow.StrategyFlowFactory;
import com.shane.orchestragent.biz.manager.impl.AgentManagerImpl;
import com.shane.orchestragent.biz.model.vo.AgentChatRequestVO;
import com.shane.orchestragent.biz.model.vo.AgentInvokeRequestVO;
import com.shane.orchestragent.biz.recorder.impl.FlowExecutionRecorderImpl;
import com.shane.orchestragent.biz.service.impl.FlowServiceImpl;
import com.shane.orchestragent.biz.service.impl.LlmServiceImpl;
import com.shane.orchestragent.biz.service.impl.SseServiceImpl;
import com.shane.orchestragent.common.exception.BizErrorFactory;
import com.shane.orchestragent.common.exception.BizException;
import com.shane.orchestragent.integration.llm.impl.OpenAiCompatibleLlmClient;
import com.shane.orchestragent.prompt.render.TemplateRender;
import com.shane.orchestragent.prompt.service.impl.PromptServiceImpl;
import com.shane.orchestragent.repository.*;
import com.shane.orchestragent.repository.client.*;
import com.shane.orchestragent.repository.impl.*;
import com.shane.orchestragent.repository.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 仓储层完整链路集成测试
 * 覆盖 7 大核心 Repository 的查询契约、缓存管理、定时与手动 reload、回调通知以及无配置时严格抛异常中断流程机制
 *
 * @author Shane
 */
public class RepositoryIntegrationTest {

    /** 策略仓储实例 */
    private StrategyConfigRepositoryImpl strategyRepo;
    /** 智能体仓储实例 */
    private AgentConfigRepositoryImpl agentRepo;
    /** 工具仓储实例 */
    private ToolConfigRepositoryImpl toolRepo;
    /** 数据字典仓储实例 */
    private DictRepositoryImpl dictRepo;
    /** 大模型配置仓储实例 */
    private ModelConfigRepositoryImpl modelRepo;

    @BeforeEach
    public void setUp() throws Exception {
        strategyRepo = new StrategyConfigRepositoryImpl();
        strategyRepo.afterPropertiesSet();

        agentRepo = new AgentConfigRepositoryImpl();
        agentRepo.afterPropertiesSet();

        toolRepo = new ToolConfigRepositoryImpl();
        toolRepo.afterPropertiesSet();

        dictRepo = new DictRepositoryImpl();
        dictRepo.afterPropertiesSet();

        modelRepo = new ModelConfigRepositoryImpl();
        modelRepo.afterPropertiesSet();
    }

    @Test
    @DisplayName("1. 验证 StrategyConfigRepository: 无预设数据时返回空(严禁兜底)，远程拉取后准确查询")
    public void testStrategyConfigRepository() {
        assertNotNull(strategyRepo.name());
        assertEquals("strategyConfigRepository", strategyRepo.name());

        // 验证无数据时不兜底伪造策略，直接返回 null
        assertNull(strategyRepo.getByCode("default_react_strategy"), "无远程数据时严禁默认兜底生成策略");
        assertNull(strategyRepo.getByCode("react_default_strategy"));
        assertTrue(strategyRepo.getAll().isEmpty(), "初始策略快照应为空列表");

        // 模拟远程下发策略并 reload
        StrategyConfigDO custom = StrategyConfigDO.builder()
                .strategyId("custom_test_01")
                .code("custom_code_01")
                .name("自定义测试策略")
                .flowTopologyType("REACT")
                .maxStep(15)
                .workerCodes(List.of("search_worker"))
                .build();

        StrategyConfigDO chatStrategy = StrategyConfigDO.builder()
                .strategyId("chat_strategy_01")
                .code("multi_chat_strategy")
                .name("多轮对话策略")
                .flowTopologyType("MULTIPLE_CHAT")
                .stream(true)
                .build();

        StrategyConfigApiClient mockStrategyClient = () -> List.of(custom, chatStrategy);
        ReflectionTestUtils.setField(strategyRepo, "strategyConfigApiClient", mockStrategyClient);
        strategyRepo.reload();

        assertEquals(custom, strategyRepo.getByCode("custom_code_01"));
        assertEquals(List.of("search_worker"), strategyRepo.getByCode("custom_code_01").getWorkerCodes());
        assertEquals("MULTIPLE_CHAT", strategyRepo.getByCode("multi_chat_strategy").getFlowTopologyType());
        assertTrue(strategyRepo.getByCode("multi_chat_strategy").getStream());
        assertEquals(2, strategyRepo.getAll().size());
        assertTrue(strategyRepo.getMap().containsKey("custom_code_01"));
    }

    @Test
    @DisplayName("2. 验证 AgentConfigRepository: 无数据时返回空，远程拉取后各智能体正常检索")
    public void testAgentConfigRepository() {
        assertEquals("agentConfigRepository", agentRepo.name());

        // 验证初始状态下无兜底数据
        assertNull(agentRepo.getByCode("ROUTER"), "无外部配置时严禁自动填充默认智能体");
        assertNull(agentRepo.getByCode("PLANNER"));
        assertTrue(agentRepo.getAll().isEmpty());

        // 模拟远程下发标准智能体
        AgentConfigDO router = AgentConfigDO.builder().name("ROUTER").code("router_code").model("gpt-4o").build();
        AgentConfigDO planner = AgentConfigDO.builder().name("PLANNER").code("planner_code").model("gpt-4o").build();
        AgentConfigDO conductor = AgentConfigDO.builder().name("CONDUCTOR").code("conductor_code").model("gpt-4o").build();
        AgentConfigDO worker = AgentConfigDO.builder().name("search_worker").code("search_worker_code").model("gpt-4o").build();

        AgentConfigApiClient mockAgentClient = () -> List.of(router, planner, conductor, worker);
        ReflectionTestUtils.setField(agentRepo, "agentConfigApiClient", mockAgentClient);
        agentRepo.reload();

        // 验证检索（按 code）
        assertEquals(router, agentRepo.getByCode("router_code"));
        assertEquals(planner, agentRepo.getByCode("planner_code"));
        assertEquals(conductor, agentRepo.getByCode("conductor_code"));
        assertEquals(worker, agentRepo.getByCode("search_worker_code"));
        assertEquals(4, agentRepo.getAll().size());
        assertTrue(agentRepo.getMap().containsKey("router_code"));

        // 验证未注册的智能体返回 null
        assertNull(agentRepo.getByCode("NON_EXISTENT_CODE"));
    }

    @Test
    @DisplayName("3. 验证 ToolConfigRepository: 工具查询与 Schema 检索(无数据不兜底)")
    public void testToolConfigRepository() {
        assertEquals("toolConfigRepository", toolRepo.name());

        // 验证无数据返回 null
        assertNull(toolRepo.getByCode("flight_search"), "无数据时严禁生成默认工具");
        assertNull(toolRepo.getByCode("calc_tool"));

        // 模拟远程拉取工具配置
        ToolConfigDO newTool = ToolConfigDO.builder()
                .name("calculator")
                .code("calc_tool")
                .title("计算器工具")
                .endpoint("http://127.0.0.1:8080/calc")
                .requestJsonSchema("{\"type\":\"object\"}")
                .build();

        ToolConfigApiClient mockToolClient = () -> List.of(newTool);
        ReflectionTestUtils.setField(toolRepo, "toolConfigApiClient", mockToolClient);
        toolRepo.reload();

        assertEquals(newTool, toolRepo.getByCode("calc_tool"));
        assertNotNull(toolRepo.getByCode("calc_tool").getRequestJsonSchema());
        assertEquals(1, toolRepo.getAll().size());
        assertTrue(toolRepo.getMap().containsKey("calc_tool"));
    }

    @Test
    @DisplayName("4. 验证 DictRepository: 仓储无配置时由调用方指定兜底默认值，拉取配置后优先使用配置值")
    public void testDictRepository() {
        assertEquals("dictRepository", dictRepo.name());
        assertTrue(dictRepo.getAll().isEmpty(), "初始字典快照应为空");

        // 仓储中无对应 key 时，按参数传入的调用方 fallback 返回
        int fallbackStep = dictRepo.getValue(DictRepository.Keys.KEY_FLOW_MAX_STEP, 10);
        assertEquals(10, fallbackStep);

        long fallbackTimeout = dictRepo.getValue(DictRepository.Keys.KEY_LLM_MAX_WAIT_TIME, 5000L);
        assertEquals(5000L, fallbackTimeout);

        boolean fallbackFlag = dictRepo.getValue(DictRepository.Keys.KEY_LOG_FLAGS, true);
        assertTrue(fallbackFlag);

        double fallbackTemp = dictRepo.getValue(DictRepository.Keys.KEY_DEFAULT_SYSTEM_AGENT_TEMP, 0.7);
        assertEquals(0.7, fallbackTemp, 0.001);

        String fallbackModel = dictRepo.getValue(DictRepository.Keys.KEY_DEFAULT_LLM_MODEL, "fallback-model");
        assertEquals("fallback-model", fallbackModel);

        // 模拟远程客户端返回字典配置并 reload
        DictApiClient mockDictClient = () -> List.of(
                DictDO.builder().key(DictRepository.Keys.KEY_FLOW_MAX_STEP).value("20").build(),
                DictDO.builder().key(DictRepository.Keys.KEY_LLM_MAX_WAIT_TIME).value("30000").build(),
                DictDO.builder().key(DictRepository.Keys.KEY_LOG_FLAGS).value("false").build(),
                DictDO.builder().key(DictRepository.Keys.KEY_DEFAULT_SYSTEM_AGENT_TEMP).value("0.5").build(),
                DictDO.builder().key(DictRepository.Keys.KEY_DEFAULT_LLM_MODEL).value("Deepseek-R1").build()
        );
        ReflectionTestUtils.setField(dictRepo, "dictApiClient", mockDictClient);
        dictRepo.reload();

        // 重新读取已同步的配置
        assertEquals(20, dictRepo.getValue(DictRepository.Keys.KEY_FLOW_MAX_STEP, 10));
        assertEquals(30000L, dictRepo.getValue(DictRepository.Keys.KEY_LLM_MAX_WAIT_TIME, 5000L));
        assertFalse(dictRepo.getValue(DictRepository.Keys.KEY_LOG_FLAGS, true));
        assertEquals(0.5, dictRepo.getValue(DictRepository.Keys.KEY_DEFAULT_SYSTEM_AGENT_TEMP, 0.7), 0.001);
        assertEquals("Deepseek-R1", dictRepo.getValue(DictRepository.Keys.KEY_DEFAULT_LLM_MODEL, "fallback-model"));
    }

    @Test
    @DisplayName("5. 验证 AgentConfigRepository: systemPrompt 与 userPrompt 纯配置驱动且无数据不兜底")
    public void testAgentConfigPrompts() {
        assertEquals("agentConfigRepository", agentRepo.name());

        // 验证初始状态无数据返回 null (严格不兜底)
        assertNull(agentRepo.getByCode("PLANNER_PROMPT_TEST"));

        // 模拟远程下发包含 systemPrompt 和 userPrompt 的智能体配置
        AgentConfigApiClient mockAgentClient = () -> List.of(
                AgentConfigDO.builder()
                        .name("PLANNER_PROMPT_TEST")
                        .code("PLANNER_PROMPT_TEST")
                        .model("gpt-4o")
                        .systemPrompt("你是专业的任务规划专家")
                        .userPrompt("请针对以下目标进行拆解: ${strategy_target}")
                        .build(),
                AgentConfigDO.builder()
                        .name("WORKER_PROMPT_TEST")
                        .code("WORKER_PROMPT_TEST")
                        .model("gpt-4o")
                        .systemPrompt("你是专业的工作执行节点")
                        .build()
        );
        ReflectionTestUtils.setField(agentRepo, "agentConfigApiClient", mockAgentClient);
        agentRepo.reload();

        AgentConfigDO planner = agentRepo.getByCode("PLANNER_PROMPT_TEST");
        assertNotNull(planner);
        assertEquals("你是专业的任务规划专家", planner.getSystemPrompt());
        assertEquals("请针对以下目标进行拆解: ${strategy_target}", planner.getUserPrompt());

        AgentConfigDO worker = agentRepo.getByCode("WORKER_PROMPT_TEST");
        assertNotNull(worker);
        assertEquals("你是专业的工作执行节点", worker.getSystemPrompt());
        assertNull(worker.getUserPrompt());
    }

    @Test
    @DisplayName("6. 验证 ModelConfigRepository: 无数据时不兜底，未知模型不回退默认模型返回 null")
    public void testModelConfigRepository() {
        assertEquals("modelConfigRepository", modelRepo.name());

        // 验证无数据返回 null
        assertNull(modelRepo.getDefaultModel());
        assertNull(modelRepo.getByCode("gpt-4o"));

        // 模拟远程加载模型配置
        ModelConfigApiClient mockModelClient = () -> List.of(
                ModelConfigDO.builder().code("gpt-4o").name("GPT-4o").endpoint("https://api.openai.com/v1").build(),
                ModelConfigDO.builder().code("deepseek-chat").name("DeepSeek").endpoint("https://api.deepseek.com").build()
        );
        ReflectionTestUtils.setField(modelRepo, "modelConfigApiClient", mockModelClient);
        modelRepo.reload();

        // 验证查询
        assertNotNull(modelRepo.getDefaultModel());
        assertEquals("gpt-4o", modelRepo.getDefaultModel().getCode());
        assertNotNull(modelRepo.getByCode("deepseek-chat"));
        assertEquals("deepseek-chat", modelRepo.getByCode("deepseek-chat").getCode());

        // 验证未知模型不回退默认模型，严格返回 null 避免隐式兜底
        assertNull(modelRepo.getByCode("unknown-custom-model"));
    }

    @Test
    @DisplayName("7. 验证 ApiClient 模拟远程拉取与 RepositoryReloadCallback 变更通知机制")
    public void testApiClientReloadAndCallback() {
        // 创建 Mock ApiClient，模拟远程服务端返回全新配置列表
        StrategyConfigApiClient mockClient = new StrategyConfigApiClient() {
            @Override
            public List<StrategyConfigDO> list() {
                StrategyConfigDO remoteStrategy = StrategyConfigDO.builder()
                        .strategyId("remote_strategy_999")
                        .code("remote_code_999")
                        .name("远程动态下发策略")
                        .flowTopologyType("REACT")
                        .maxStep(35)
                        .build();
                return Collections.singletonList(remoteStrategy);
            }
        };

        // 注入 Mock Client
        ReflectionTestUtils.setField(strategyRepo, "strategyConfigApiClient", mockClient);

        // 注册回调监听器
        AtomicBoolean callbackFired = new AtomicBoolean(false);
        RepositoryReloadCallback callback = repoName -> {
            if ("strategyConfigRepository".equals(repoName)) {
                callbackFired.set(true);
            }
        };
        strategyRepo.addCallback(callback);

        // 手动执行 reload
        strategyRepo.reload();

        // 验证回调已被触发
        assertTrue(callbackFired.get(), "reload 后必须触发注册的回调监听器");

        // 验证本地快照已替换为远程配置
        StrategyConfigDO reloaded = strategyRepo.getByCode("remote_code_999");
        assertNotNull(reloaded, "应当能够通过 code 获取到远程下发的全新策略");
        assertEquals(35, reloaded.getMaxStep());
        assertEquals("remote_code_999", reloaded.getCode());

        // 测试移除回调
        strategyRepo.removeCallback(callback);
    }

    @Test
    @DisplayName("8. 验证核心业务中断: 策略未获取到时严格抛出 BizException 中断流程")
    public void testStrategyNotFoundThrowsBizException() {
        // 构建核心执行链路
        OpenAiCompatibleLlmClient llmClient = new OpenAiCompatibleLlmClient();
        FlowServiceImpl flowService = new FlowServiceImpl();
        FlowExecutionRecorderImpl recorder = new FlowExecutionRecorderImpl(flowService);
        StrategyFlowFactory flowFactory = new StrategyFlowFactory(
                new LlmServiceImpl(llmClient),
                new PromptServiceImpl(new TemplateRender()),
                flowService,
                recorder
        );

        AgentManagerImpl agentManager = new AgentManagerImpl(flowFactory, strategyRepo, flowService);
        agentManager.setSseService(new SseServiceImpl());

        // 1. 验证 invoke (异步调用) 在策略不存在时直接抛出异常中断
        AgentInvokeRequestVO invokeReq = AgentInvokeRequestVO.builder()
                .strategyCode("non_existent_strategy_id")
                .message("测试查询")
                .sessionId("session-test-01")
                .build();
        BizException invokeEx = assertThrows(BizException.class, () -> agentManager.invoke(invokeReq));
        assertEquals(BizErrorFactory.STRATEGY_NOT_FOUND, invokeEx.getErrorCode());
        assertTrue(invokeEx.getMessage().contains("non_existent_strategy_id"));

        // 2. 验证 chat (流式调用) 在策略不存在时直接抛出异常中断
        AgentChatRequestVO chatReq = AgentChatRequestVO.builder()
                .strategyCode("non_existent_chat_strategy")
                .message("你好")
                .sessionId("session-test-02")
                .build();
        BizException chatEx = assertThrows(BizException.class, () -> agentManager.chat(chatReq));
        assertEquals(BizErrorFactory.STRATEGY_NOT_FOUND, chatEx.getErrorCode());
        assertTrue(chatEx.getMessage().contains("non_existent_chat_strategy"));
    }

    @Test
    @DisplayName("9. 验证 DeepSeek 模型配置解析与实际模型名称映射")
    public void testDeepseekModelConfigResolution() throws Exception {
        ModelConfigApiClient mockClient = () -> List.of(
                ModelConfigDO.builder()
                        .code("Deepseek")
                        .name("Deepseek")
                        .modelName("deepseek-flash")
                        .endpoint("https://api.deepseek.com")
                        .apiKey("sk-a4077ed8bd294ebf86bb8e6a75658f81")
                        .build()
        );
        ReflectionTestUtils.setField(modelRepo, "modelConfigApiClient", mockClient);
        modelRepo.reload();

        // 通过编码 "Deepseek" 获取
        ModelConfigDO resolved = modelRepo.getByCode("Deepseek");
        assertNotNull(resolved);
        assertEquals("Deepseek", resolved.getName());
        assertEquals("deepseek-flash", resolved.getActualModelName());

        // 验证默认模型也是该模型
        ModelConfigDO defaultModel = modelRepo.getDefaultModel();
        assertNotNull(defaultModel);
        assertEquals("Deepseek", defaultModel.getName());
        assertEquals("deepseek-flash", defaultModel.getActualModelName());

        // 验证大小写不敏感编码查询
        assertNotNull(modelRepo.getByCode("deepseek"));

        // 验证 OpenAiCompatibleLlmClient 端到端加载并调用 Deepseek
        OpenAiCompatibleLlmClient client = new OpenAiCompatibleLlmClient();
        client.setModelConfigRepository(modelRepo);
        com.shane.orchestragent.integration.llm.model.ChatResponseVO resp = client.chat(
                com.shane.orchestragent.integration.llm.model.ChatRequestDTO.builder()
                        .model("Deepseek")
                        .messages(java.util.List.of(com.shane.orchestragent.integration.llm.model.ChatMessageDTO.builder().role("user").content("1+1=? 请只输出数字结果").build()))
                        .build()
        );
        assertNotNull(resp);
        assertNotNull(resp.getFirstMessage());
        assertNotNull(resp.getFirstMessage().getContent());
        assertTrue(resp.getFirstMessage().getContent().contains("2"));
    }
}
