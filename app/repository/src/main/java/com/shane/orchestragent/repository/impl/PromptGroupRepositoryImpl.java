package com.shane.orchestragent.repository.impl;

import com.shane.orchestragent.repository.PromptGroupRepository;
import com.shane.orchestragent.repository.client.PromptGroupConfigApiClient;
import com.shane.orchestragent.repository.model.PromptGroupConfigDO;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.util.*;

/**
 * 提示词组编排绑定仓储标准实现
 * 继承 AbstractConfigRepository 底座，通过 PromptGroupConfigApiClient 从远程管理服务拉取最新提示词组绑定关系
 *
 * @author Shane
 */
@Primary
@Repository("promptGroupRepository")
public class PromptGroupRepositoryImpl extends AbstractConfigRepository<PromptGroupConfigDO> implements PromptGroupRepository {

    /** 远程提示词组接口客户端 */
    @Autowired(required = false)
    private PromptGroupConfigApiClient promptGroupConfigApiClient;

    /** 全量提示词组列表快照 */
    private volatile List<PromptGroupConfigDO> dataList = Collections.emptyList();

    /** 按策略编码索引快照 (Key: strategyCode + "_" + flowType) */
    private volatile Map<String, PromptGroupConfigDO> dataMapByStrategy = Collections.emptyMap();

    /** 按复合 Key 索引快照 (Key: flowType + "_" + code + "_" + version) */
    private volatile Map<String, PromptGroupConfigDO> dataMapByCodeVersion = Collections.emptyMap();

    @Override
    public PromptGroupConfigDO getByStrategyCode(String strategyCode, String flowType) {
        String key = buildStrategyKey(strategyCode, flowType);
        return dataMapByStrategy.get(key);
    }

    @Override
    public PromptGroupConfigDO getByCodeVersion(String flowType, String code, String version) {
        String key = buildCodeVersionKey(flowType, code, version);
        return dataMapByCodeVersion.get(key);
    }

    @Override
    public List<PromptGroupConfigDO> getAll() {
        return this.dataList;
    }

    @Override
    protected boolean doReload() throws Exception {
        if (promptGroupConfigApiClient == null) {
            logger.warn("[PromptGroupRepository] promptGroupConfigApiClient 未注入");
            return false;
        }
        List<PromptGroupConfigDO> remoteList = promptGroupConfigApiClient.list();
        if (remoteList == null) {
            logger.warn("[PromptGroupRepository] 远程接口未就绪或请求失败，保留现有本地缓存(size={})", dataList.size());
            return false;
        }

        Map<String, PromptGroupConfigDO> mapByStrategy = new HashMap<>();
        Map<String, PromptGroupConfigDO> mapByCodeVersion = new HashMap<>();

        for (PromptGroupConfigDO group : remoteList) {
            if (group != null) {
                if (StringUtils.isNotBlank(group.getStrategyCode())) {
                    mapByStrategy.put(buildStrategyKey(group.getStrategyCode(), group.getFlowType()), group);
                }
                if (StringUtils.isNotBlank(group.getCode())) {
                    mapByCodeVersion.put(buildCodeVersionKey(group.getFlowType(), group.getCode(), group.getVersion()), group);
                }
            }
        }

        this.dataList = Collections.unmodifiableList(new ArrayList<>(remoteList));
        this.dataMapByStrategy = Collections.unmodifiableMap(mapByStrategy);
        this.dataMapByCodeVersion = Collections.unmodifiableMap(mapByCodeVersion);
        logger.info("[PromptGroupRepository] 从远程接口成功加载 {} 条提示词组配置", remoteList.size());
        return true;
    }

    private String buildStrategyKey(String strategyCode, String flowType) {
        return (strategyCode != null ? strategyCode.toLowerCase() : "") + "_" +
                (flowType != null ? flowType.toUpperCase() : "");
    }

    private String buildCodeVersionKey(String flowType, String code, String version) {
        return (flowType != null ? flowType.toUpperCase() : "") + "_" +
                (code != null ? code.toLowerCase() : "") + "_" +
                (version != null ? version : "latest");
    }
}
