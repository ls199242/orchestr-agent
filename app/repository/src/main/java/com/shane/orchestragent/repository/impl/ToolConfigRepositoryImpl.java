package com.shane.orchestragent.repository.impl;

import com.shane.orchestragent.repository.ToolConfigRepository;
import com.shane.orchestragent.repository.client.ToolConfigApiClient;
import com.shane.orchestragent.repository.model.ToolConfigDO;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.util.*;

/**
 * 工具元数据配置仓储标准实现
 * 继承 AbstractConfigRepository 底座，通过 ToolConfigApiClient 从远程管理服务拉取最新工具定义与 JSON Schema
 * 具备双重索引查询能力与内存不可变只读快照
 *
 * @author Shane
 */
@Primary
@Repository("toolConfigRepository")
public class ToolConfigRepositoryImpl extends AbstractConfigRepository<ToolConfigDO> implements ToolConfigRepository {

    /** 远程工具配置接口客户端 */
    @Autowired(required = false)
    private ToolConfigApiClient toolConfigApiClient;

    /** 全量工具列表快照 */
    private volatile List<ToolConfigDO> dataList = Collections.emptyList();

    /** 按编码索引快照 */
    private volatile Map<String, ToolConfigDO> dataMapByCode = Collections.emptyMap();

    @Override
    public ToolConfigDO getByCode(String code) {
        if (StringUtils.isBlank(code)) {
            return null;
        }
        return dataMapByCode.get(code);
    }

    @Override
    public Map<String, ToolConfigDO> getMap() {
        return this.dataMapByCode;
    }

    @Override
    public List<ToolConfigDO> getAll() {
        return this.dataList;
    }

    @Override
    protected boolean doReload() throws Exception {
        if (toolConfigApiClient == null) {
            logger.warn("[ToolConfigRepository] toolConfigApiClient 未注入");
            return false;
        }
        List<ToolConfigDO> remoteList = toolConfigApiClient.list();
        if (remoteList == null) {
            logger.warn("[ToolConfigRepository] 远程接口未就绪或请求失败，保留现有本地缓存(size={})", dataList.size());
            return false;
        }

        Map<String, ToolConfigDO> mapByCode = new HashMap<>();
        for (ToolConfigDO tool : remoteList) {
            if (tool != null && StringUtils.isNotBlank(tool.getCode())) {
                mapByCode.put(tool.getCode(), tool);
            }
        }

        this.dataList = Collections.unmodifiableList(new ArrayList<>(remoteList));
        this.dataMapByCode = Collections.unmodifiableMap(mapByCode);
        logger.info("[ToolConfigRepository] 从远程接口成功加载 {} 条工具配置", remoteList.size());
        return true;
    }
}
