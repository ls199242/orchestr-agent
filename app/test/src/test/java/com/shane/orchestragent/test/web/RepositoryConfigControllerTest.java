package com.shane.orchestragent.test.web;

import com.shane.orchestragent.common.model.BaseResult;
import com.shane.orchestragent.repository.client.*;
import com.shane.orchestragent.repository.impl.*;
import com.shane.orchestragent.repository.model.*;
import com.shane.orchestragent.web.controller.RepositoryConfigController;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

/**
 * 仓储配置查看控制器测试
 *
 * @author Shane
 */
public class RepositoryConfigControllerTest {

    private RepositoryConfigController configController;

    private StrategyConfigRepositoryImpl strategyRepo;
    private AgentConfigRepositoryImpl agentRepo;
    private ToolConfigRepositoryImpl toolRepo;
    private ModelConfigRepositoryImpl modelRepo;
    private DictRepositoryImpl dictRepo;
    private PromptRepositoryImpl promptRepo;
    private PromptGroupRepositoryImpl promptGroupRepo;

    @BeforeEach
    public void setup() throws Exception {
        strategyRepo = new StrategyConfigRepositoryImpl();
        agentRepo = new AgentConfigRepositoryImpl();
        toolRepo = new ToolConfigRepositoryImpl();
        modelRepo = new ModelConfigRepositoryImpl();
        dictRepo = new DictRepositoryImpl();
        promptRepo = new PromptRepositoryImpl();
        promptGroupRepo = new PromptGroupRepositoryImpl();

        // 注入 Mock 数据客户端
        StrategyConfigDO strategy = StrategyConfigDO.builder().strategyId("strat_01").code("strat_01").name("测试策略").build();
        ReflectionTestUtils.setField(strategyRepo, "strategyConfigApiClient", (StrategyConfigApiClient) () -> List.of(strategy));

        AgentConfigDO agent = AgentConfigDO.builder().name("TEST_AGENT").model("gpt-4o").build();
        ReflectionTestUtils.setField(agentRepo, "agentConfigApiClient", (AgentConfigApiClient) () -> List.of(agent));

        ToolConfigDO tool = ToolConfigDO.builder().name("calc").code("calc").build();
        ReflectionTestUtils.setField(toolRepo, "toolConfigApiClient", (ToolConfigApiClient) () -> List.of(tool));

        ModelConfigDO model = ModelConfigDO.builder().code("gpt-4o").name("GPT-4o").build();
        ReflectionTestUtils.setField(modelRepo, "modelConfigApiClient", (ModelConfigApiClient) () -> List.of(model));

        DictDO dict = DictDO.builder().key("flow_max_step").value("15").build();
        ReflectionTestUtils.setField(dictRepo, "dictApiClient", (DictApiClient) () -> List.of(dict));

        PromptConfigDO prompt = PromptConfigDO.builder().name("TEST_PROMPT").code("test_prompt").prompt("Hi").build();
        ReflectionTestUtils.setField(promptRepo, "promptConfigApiClient", (PromptConfigApiClient) () -> List.of(prompt));

        PromptGroupConfigDO group = PromptGroupConfigDO.builder().code("test_group").flowType("REACT").build();
        ReflectionTestUtils.setField(promptGroupRepo, "promptGroupConfigApiClient", (PromptGroupConfigApiClient) () -> List.of(group));

        // 初始化
        strategyRepo.afterPropertiesSet();
        agentRepo.afterPropertiesSet();
        toolRepo.afterPropertiesSet();
        modelRepo.afterPropertiesSet();
        dictRepo.afterPropertiesSet();
        promptRepo.afterPropertiesSet();
        promptGroupRepo.afterPropertiesSet();

        configController = new RepositoryConfigController(
                strategyRepo, agentRepo, toolRepo, modelRepo, dictRepo, promptRepo, promptGroupRepo
        );
    }

    @Test
    @DisplayName("测试概览接口 /api/agent/config/overview")
    public void testOverview() {
        BaseResult<List<Map<String, Object>>> result = configController.getOverview();
        Assertions.assertTrue(result.isSuccess());
        Assertions.assertNotNull(result.getData());
        Assertions.assertEquals(7, result.getData().size());

        Map<String, Object> first = result.getData().get(0);
        Assertions.assertTrue(first.containsKey("name"));
        Assertions.assertTrue(first.containsKey("healthy"));
        Assertions.assertTrue(first.containsKey("itemCount"));
        Assertions.assertEquals(1, first.get("itemCount"));
    }

    @Test
    @DisplayName("测试全量数据查看 /api/agent/config/all")
    public void testGetAllConfigs() {
        BaseResult<Map<String, Object>> result = configController.getAllConfigs();
        Assertions.assertTrue(result.isSuccess());
        Map<String, Object> data = result.getData();
        Assertions.assertNotNull(data);
        Assertions.assertTrue(data.containsKey("strategies"));
        Assertions.assertTrue(data.containsKey("agents"));
        Assertions.assertTrue(data.containsKey("tools"));
        Assertions.assertTrue(data.containsKey("models"));
        Assertions.assertTrue(data.containsKey("dict"));
        Assertions.assertTrue(data.containsKey("prompts"));
        Assertions.assertTrue(data.containsKey("promptGroups"));
    }

    @Test
    @DisplayName("测试单个仓储查看 /api/agent/config/{repoName}")
    public void testGetRepoDetails() {
        BaseResult<Object> strategyResult = configController.getRepoDetails("strategy");
        Assertions.assertTrue(strategyResult.isSuccess());
        Assertions.assertNotNull(strategyResult.getData());

        BaseResult<Object> invalidResult = configController.getRepoDetails("invalid-repo-name");
        Assertions.assertFalse(invalidResult.isSuccess());
        Assertions.assertEquals("UNKNOWN_REPO", invalidResult.getCode());
    }

    @Test
    @DisplayName("测试手动触发热重载 /api/agent/config/reload")
    public void testTriggerReload() {
        BaseResult<Map<String, Object>> reloadAll = configController.triggerReload(null);
        Assertions.assertTrue(reloadAll.isSuccess());
        Assertions.assertTrue(reloadAll.getData().get("message").toString().contains("全部 7 个仓储"));

        BaseResult<Map<String, Object>> reloadSingle = configController.triggerReload("strategy");
        Assertions.assertTrue(reloadSingle.isSuccess());
        Assertions.assertTrue(reloadSingle.getData().get("message").toString().contains("[strategy]"));
    }
}
