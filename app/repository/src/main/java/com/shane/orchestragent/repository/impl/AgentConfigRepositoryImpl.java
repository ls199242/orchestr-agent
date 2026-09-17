package com.shane.orchestragent.repository.impl;

import com.shane.orchestragent.repository.AgentConfigRepository;
import com.shane.orchestragent.repository.client.AgentConfigApiClient;
import com.shane.orchestragent.repository.model.AgentConfigDO;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.util.*;

/**
 * 智能体元数据配置仓储标准实现
 * 继承 AbstractConfigRepository 底座，通过 AgentConfigApiClient 从远程管理服务拉取最新智能体配置
 * 维护内存不可变只读索引映射
 *
 * @author Shane
 */
@Primary
@Repository("agentConfigRepository")
public class AgentConfigRepositoryImpl extends AbstractConfigRepository<AgentConfigDO> implements AgentConfigRepository {

    /** 远程智能体配置接口客户端 */
    @Autowired(required = false)
    private AgentConfigApiClient agentConfigApiClient;

    /** 全量智能体配置快照列表 */
    private volatile List<AgentConfigDO> dataList = Collections.emptyList();

    /** 按智能体名称索引的字典快照 */
    private volatile Map<String, AgentConfigDO> dataMapByName = Collections.emptyMap();

    @Override
    public AgentConfigDO getByName(String name) {
        if (StringUtils.isBlank(name)) {
            return null;
        }
        return dataMapByName.get(name);
    }

    @Override
    public Map<String, AgentConfigDO> getMap() {
        return this.dataMapByName;
    }

    @Override
    public List<AgentConfigDO> getAll() {
        return this.dataList;
    }

    @Override
    protected boolean doReload() throws Exception {
        if (agentConfigApiClient == null) {
            logger.warn("[AgentConfigRepository] agentConfigApiClient 未注入");
            return false;
        }
        List<AgentConfigDO> remoteList = agentConfigApiClient.list();
        if (remoteList == null) {
            logger.warn("[AgentConfigRepository] 远程接口未就绪或请求失败，保留现有本地缓存(size={})", dataList.size());
            return false;
        }

        Map<String, AgentConfigDO> map = new HashMap<>();
        for (AgentConfigDO agent : remoteList) {
            if (agent != null && StringUtils.isNotBlank(agent.getName())) {
                map.put(agent.getName(), agent);
            }
        }

        this.dataList = Collections.unmodifiableList(new ArrayList<>(remoteList));
        this.dataMapByName = Collections.unmodifiableMap(map);
        logger.info("[AgentConfigRepository] 从远程接口成功加载 {} 条智能体配置", remoteList.size());
        return true;
    }
}
