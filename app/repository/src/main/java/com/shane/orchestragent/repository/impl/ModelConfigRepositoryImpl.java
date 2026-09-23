package com.shane.orchestragent.repository.impl;

import com.shane.orchestragent.repository.ModelConfigRepository;
import com.shane.orchestragent.repository.client.ModelConfigApiClient;
import com.shane.orchestragent.repository.model.ModelConfigDO;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.util.*;

/**
 * 大模型配置仓储标准实现
 * 继承 AbstractConfigRepository 底座，通过 ModelConfigApiClient 从远程管理服务拉取最新模型参数与端点
 *
 * @author Shane
 */
@Primary
@Repository("modelConfigRepository")
public class ModelConfigRepositoryImpl extends AbstractConfigRepository<ModelConfigDO> implements ModelConfigRepository {

    /** 远程大模型配置接口客户端 */
    @Autowired(required = false)
    private ModelConfigApiClient modelConfigApiClient;

    /** 全量大模型配置列表快照 */
    private volatile List<ModelConfigDO> dataList = Collections.emptyList();

    /** 按模型代码索引的映射快照 */
    private volatile Map<String, ModelConfigDO> dataMapByCode = Collections.emptyMap();

    @Override
    public ModelConfigDO getByCode(String code) {
        if (StringUtils.isBlank(code)) {
            return null;
        }
        String key = code.toLowerCase().trim();
        return dataMapByCode.get(key);
    }

    @Override
    public ModelConfigDO getDefaultModel() {
        return dataList.isEmpty() ? null : dataList.get(0);
    }

    @Override
    public List<ModelConfigDO> getAll() {
        return this.dataList;
    }

    @Override
    protected boolean doReload() throws Exception {
        if (modelConfigApiClient == null) {
            logger.warn("[ModelConfigRepository] modelConfigApiClient 未注入");
            return false;
        }
        List<ModelConfigDO> remoteList = modelConfigApiClient.list();
        if (remoteList == null) {
            logger.warn("[ModelConfigRepository] 远程接口未就绪或请求失败，保留现有本地缓存(size={})", dataList.size());
            return false;
        }

        Map<String, ModelConfigDO> mapByCode = new HashMap<>();
        for (ModelConfigDO model : remoteList) {
            if (model != null && StringUtils.isNotBlank(model.getCode())) {
                mapByCode.put(model.getCode().toLowerCase().trim(), model);
            }
        }

        this.dataList = Collections.unmodifiableList(new ArrayList<>(remoteList));
        this.dataMapByCode = Collections.unmodifiableMap(mapByCode);
        logger.info("[ModelConfigRepository] 从远程接口成功加载 {} 条大模型配置", remoteList.size());
        return true;
    }
}
