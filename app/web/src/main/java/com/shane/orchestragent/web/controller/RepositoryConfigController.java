package com.shane.orchestragent.web.controller;

import com.shane.orchestragent.common.annotation.Api;
import com.shane.orchestragent.common.enums.LogModuleEnum;
import com.shane.orchestragent.common.model.BaseResult;
import com.shane.orchestragent.repository.*;
import com.shane.orchestragent.repository.impl.AbstractConfigRepository;
import org.springframework.web.bind.annotation.*;

import java.text.SimpleDateFormat;
import java.util.*;

/**
 * 仓储配置查看与运维控制器
 * 提供 5 大核心仓储当前内存快照查看、运行健康状态巡检及手动热重载触发能力
 *
 * @author Shane
 */
@RestController
@RequestMapping("/api/agent/config")
@Api(logModule = LogModuleEnum.API_CONFIG, desc = "仓储配置管理")
public class RepositoryConfigController {

    private final StrategyConfigRepository strategyRepo;
    private final AgentConfigRepository agentRepo;
    private final ToolConfigRepository toolRepo;
    private final ModelConfigRepository modelRepo;
    private final DictRepository dictRepo;

    public RepositoryConfigController(
            StrategyConfigRepository strategyRepo,
            AgentConfigRepository agentRepo,
            ToolConfigRepository toolRepo,
            ModelConfigRepository modelRepo,
            DictRepository dictRepo) {
        this.strategyRepo = strategyRepo;
        this.agentRepo = agentRepo;
        this.toolRepo = toolRepo;
        this.modelRepo = modelRepo;
        this.dictRepo = dictRepo;
    }

    /**
     * 1. 获取所有仓储的概览信息（健康状态、条数、刷新时间）
     */
    @GetMapping("/overview")
    public BaseResult<List<Map<String, Object>>> getOverview() {
        List<Map<String, Object>> list = new ArrayList<>();
        list.add(buildRepoOverview(strategyRepo));
        list.add(buildRepoOverview(agentRepo));
        list.add(buildRepoOverview(toolRepo));
        list.add(buildRepoOverview(modelRepo));
        list.add(buildRepoOverview(dictRepo));
        return BaseResult.ok(list);
    }

    /**
     * 2. 获取所有仓储当前内存中的全量数据
     */
    @GetMapping("/all")
    public BaseResult<Map<String, Object>> getAllConfigs() {
        Map<String, Object> all = new LinkedHashMap<>();
        all.put("strategies", strategyRepo.getAll());
        all.put("agents", agentRepo.getAll());
        all.put("tools", toolRepo.getAll());
        all.put("models", modelRepo.getAll());
        all.put("dict", dictRepo.getDictMap());
        return BaseResult.ok(all);
    }

    /**
     * 3. 获取指定仓储的详情数据
     *
     * @param repoName 仓储标识 (strategy, agent, tool, model, dict)
     */
    @GetMapping("/{repoName}")
    public BaseResult<Object> getRepoDetails(@PathVariable("repoName") String repoName) {
        return switch (repoName.toLowerCase()) {
            case "strategy", "strategyconfigrepository" -> BaseResult.ok(strategyRepo.getAll());
            case "agent", "agentconfigrepository" -> BaseResult.ok(agentRepo.getAll());
            case "tool", "toolconfigrepository" -> BaseResult.ok(toolRepo.getAll());
            case "model", "modelconfigrepository" -> BaseResult.ok(modelRepo.getAll());
            case "dict", "dictrepository" -> BaseResult.ok(Map.of(
                    "dictMap", dictRepo.getDictMap(),
                    "items", dictRepo.getAll()
            ));
            default -> BaseResult.fail("UNKNOWN_REPO", "未知的仓储名称: " + repoName);
        };
    }

    /**
     * 4. 手动触发仓储刷新重载
     *
     * @param repoName 可选，指定仓储名称；若为空则刷新全部 5 个仓储
     */
    @PostMapping("/reload")
    public BaseResult<Map<String, Object>> triggerReload(@RequestParam(value = "repoName", required = false) String repoName) {
        Map<String, Object> resultMap = new LinkedHashMap<>();
        if (repoName == null || repoName.isBlank() || "all".equalsIgnoreCase(repoName)) {
            strategyRepo.reload();
            agentRepo.reload();
            toolRepo.reload();
            modelRepo.reload();
            dictRepo.reload();
            resultMap.put("message", "全部 5 个核心仓储热重载完成");
        } else {
            switch (repoName.toLowerCase()) {
                case "strategy" -> strategyRepo.reload();
                case "agent" -> agentRepo.reload();
                case "tool" -> toolRepo.reload();
                case "model" -> modelRepo.reload();
                case "dict" -> dictRepo.reload();
                default -> {
                    return BaseResult.fail("UNKNOWN_REPO", "未知的仓储名称: " + repoName);
                }
            }
            resultMap.put("message", "仓储 [" + repoName + "] 热重载完成");
        }

        resultMap.put("overview", getOverview().getData());
        return BaseResult.ok(resultMap);
    }

    private Map<String, Object> buildRepoOverview(Repository<?> repo) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", repo.name());
        map.put("itemCount", repo.getAll() != null ? repo.getAll().size() : 0);

        if (repo instanceof AbstractConfigRepository<?> absRepo) {
            map.put("healthy", absRepo.isHealthy());
            long lastTs = absRepo.getLastRefreshTimestamp();
            map.put("lastRefreshTimestamp", lastTs);
            if (lastTs > 0) {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                map.put("lastRefreshTime", sdf.format(new Date(lastTs)));
            } else {
                map.put("lastRefreshTime", "未曾成功刷新");
            }
            map.put("refreshIntervalSeconds", absRepo.getRefreshIntervalSeconds());
        }
        return map;
    }
}
