package com.shane.orchestragent.repository.impl;

import com.shane.orchestragent.repository.StrategyConfigRepository;
import com.shane.orchestragent.repository.client.StrategyConfigApiClient;
import com.shane.orchestragent.repository.model.StrategyConfigDO;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.util.*;

/**
 * 编排策略配置仓储标准实现
 * 继承 AbstractConfigRepository 底座，通过 StrategyConfigApiClient 从管理中心拉取最新策略配置
 * 支持按 ID、按 Code 双重索引检索，并维护内存不可变只读快照
 *
 * @author Shane
 */
@Primary
@Repository("strategyConfigRepository")
public class StrategyConfigRepositoryImpl extends AbstractConfigRepository<StrategyConfigDO> implements StrategyConfigRepository {

    /** 远程策略接口客户端 */
    @Autowired(required = false)
    private StrategyConfigApiClient strategyConfigApiClient;

    /** 全量策略列表不可变快照 */
    private volatile List<StrategyConfigDO> dataList = Collections.emptyList();

    /** 按策略主键 ID 索引字典快照 */
    private volatile Map<String, StrategyConfigDO> dataMapById = Collections.emptyMap();

    /** 按策略编码 Code 索引字典快照 */
    private volatile Map<String, StrategyConfigDO> dataMapByCode = Collections.emptyMap();

    @Override
    public StrategyConfigDO getById(String strategyId) {
        if (StringUtils.isBlank(strategyId)) {
            return null;
        }
        return dataMapById.get(strategyId);
    }

    @Override
    public StrategyConfigDO findStrategyByCode(String code) {
        if (StringUtils.isBlank(code)) {
            return null;
        }
        return dataMapByCode.get(code);
    }

    @Override
    public Map<String, StrategyConfigDO> getMap() {
        return this.dataMapById;
    }

    @Override
    public List<StrategyConfigDO> getAll() {
        return this.dataList;
    }

    @Override
    protected boolean doReload() throws Exception {
        if (strategyConfigApiClient == null) {
            logger.warn("[StrategyConfigRepository] strategyConfigApiClient 未注入");
            return false;
        }
        List<StrategyConfigDO> remoteList = strategyConfigApiClient.list();
        if (remoteList == null) {
            logger.warn("[StrategyConfigRepository] 远程接口未就绪或请求失败，保留现有本地缓存(size={})", dataList.size());
            return false;
        }

        Map<String, StrategyConfigDO> mapById = new HashMap<>();
        Map<String, StrategyConfigDO> mapByCode = new HashMap<>();
        for (StrategyConfigDO cfg : remoteList) {
            if (cfg != null) {
                if (StringUtils.isNotBlank(cfg.getStrategyId())) {
                    mapById.put(cfg.getStrategyId(), cfg);
                }
                if (StringUtils.isNotBlank(cfg.getCode())) {
                    mapByCode.put(cfg.getCode(), cfg);
                }
            }
        }

        this.dataList = Collections.unmodifiableList(new ArrayList<>(remoteList));
        this.dataMapById = Collections.unmodifiableMap(mapById);
        this.dataMapByCode = Collections.unmodifiableMap(mapByCode);
        logger.info("[StrategyConfigRepository] 从远程接口成功加载 {} 条策略配置", remoteList.size());
        return true;
    }
}
