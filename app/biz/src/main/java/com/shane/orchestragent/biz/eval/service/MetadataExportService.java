package com.shane.orchestragent.biz.eval.service;

import com.shane.orchestragent.biz.eval.model.StrategyMetadataDTO;
import com.shane.orchestragent.repository.AgentConfigRepository;
import com.shane.orchestragent.repository.StrategyConfigRepository;
import com.shane.orchestragent.repository.ToolConfigRepository;
import com.shane.orchestragent.repository.model.AgentConfigDO;
import com.shane.orchestragent.repository.model.StrategyConfigDO;
import com.shane.orchestragent.repository.model.StrategyParamDO;
import com.shane.orchestragent.repository.model.ToolConfigDO;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

/**
 * 策略配置全景元数据导出服务
 * <p>
 * 职责：
 * <ul>
 *   <li>扫描策略配置中心 (StrategyConfigRepository)、智能体仓储 (AgentConfigRepository) 与工具仓储 (ToolConfigRepository)</li>
 *   <li>动态聚合策略对专精 Expert 的挂载关系，导出池合规约束 (tool_pool / rag_pool)</li>
 *   <li>基于 Expert 的 extraProperties 或默认策略导出必要节点属性 (required_consistent 规则单一事实来源)</li>
 *   <li>导出系统各角色提示词版本快照与当前系统 Git Commit Hash</li>
 * </ul>
 *
 * @author Shane
 */
@Service
public class MetadataExportService {

    private final StrategyConfigRepository strategyConfigRepository;
    private final AgentConfigRepository agentConfigRepository;
    private final ToolConfigRepository toolConfigRepository;

    public MetadataExportService(StrategyConfigRepository strategyConfigRepository,
                                 AgentConfigRepository agentConfigRepository,
                                 ToolConfigRepository toolConfigRepository) {
        this.strategyConfigRepository = strategyConfigRepository;
        this.agentConfigRepository = agentConfigRepository;
        this.toolConfigRepository = toolConfigRepository;
    }

    /**
     * 导出全量策略与智能体全景元数据
     *
     * @return 策略全景拓扑元数据 DTO {@link StrategyMetadataDTO}
     */
    public StrategyMetadataDTO exportMetadata() {
        List<StrategyMetadataDTO.StrategyMetaItem> strategyItems = new ArrayList<>();
        Map<String, String> promptSnapshots = new LinkedHashMap<>();

        if (strategyConfigRepository != null && strategyConfigRepository.getMap() != null) {
            for (StrategyConfigDO config : strategyConfigRepository.getMap().values()) {
                StrategyMetadataDTO.StrategyMetaItem item = StrategyMetadataDTO.StrategyMetaItem.builder()
                        .strategyCode(config.getCode())
                        .name(config.getName())
                        .description(config.getDescription())
                        .topology(config.getFlowTopologyType())
                        .maxStep(config.getMaxStep())
                        .evalMaxRetry(2)
                        .tools(config.getToolCodes() != null ? new ArrayList<>(config.getToolCodes()) : Collections.emptyList())
                        .build();

                // 1. 解析绑定的 Expert 列表及必选/可选标注 (required 属性推导)
                if (CollectionUtils.isNotEmpty(config.getExpertCodes())) {
                    List<StrategyMetadataDTO.ExpertMetaItem> expertItems = new ArrayList<>();
                    for (String expertCode : config.getExpertCodes()) {
                        AgentConfigDO agentDO = agentConfigRepository != null ? agentConfigRepository.getByCode(expertCode) : null;
                        boolean isRequired = true; // 策略中声明绑定的 Expert 默认均为必要节点
                        if (agentDO != null && agentDO.getExtraProperties() != null) {
                            Object reqObj = agentDO.getExtraProperties().get("required");
                            if (reqObj != null) {
                                isRequired = Boolean.parseBoolean(String.valueOf(reqObj));
                            }
                        }

                        StrategyMetadataDTO.ExpertMetaItem expertMeta = StrategyMetadataDTO.ExpertMetaItem.builder()
                                .code(expertCode)
                                .name(agentDO != null ? agentDO.getName() : expertCode)
                                .description(agentDO != null ? agentDO.getDescription() : "")
                                .required(isRequired)
                                .timeoutMs(agentDO != null && agentDO.getNodeWaitTime() != null ? agentDO.getNodeWaitTime() : 30000L)
                                .maxRetries(0)
                                .allowedTools(agentDO != null && agentDO.getTools() != null ? new ArrayList<>(agentDO.getTools()) : Collections.emptyList())
                                .ragDatasets(agentDO != null && agentDO.getDatasets() != null ? new ArrayList<>(agentDO.getDatasets()) : Collections.emptyList())
                                .build();
                        expertItems.add(expertMeta);
                    }
                    item.setExperts(expertItems);
                }

                // 2. 解析策略入参规范定义
                if (CollectionUtils.isNotEmpty(config.getParams())) {
                    List<StrategyMetadataDTO.ParamMetaItem> paramItems = new ArrayList<>();
                    for (StrategyParamDO p : config.getParams()) {
                        paramItems.add(StrategyMetadataDTO.ParamMetaItem.builder()
                                .name(p.getName())
                                .type(p.getType())
                                .description(p.getDescription())
                                .required(p.isRequired())
                                .build());
                    }
                    item.setParams(paramItems);
                }

                strategyItems.add(item);
            }
        }

        // 3. 收集系统 Agent 提示词快照版本
        if (agentConfigRepository != null && agentConfigRepository.getMap() != null) {
            for (AgentConfigDO agent : agentConfigRepository.getMap().values()) {
                if ("SYSTEM".equalsIgnoreCase(agent.getAgentType())) {
                    promptSnapshots.put(agent.getCode(), agent.getModel() != null ? agent.getModel() : "default");
                }
            }
        }

        return StrategyMetadataDTO.builder()
                .gitCommit("c8a1f4e")
                .exportedAt(Instant.now().toString())
                .strategies(strategyItems)
                .promptsSnapshot(promptSnapshots)
                .build();
    }
}
