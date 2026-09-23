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

    /** 按配置名称索引的映射快照 */
    private volatile Map<String, ModelConfigDO> dataMapByName = Collections.emptyMap();

    @Override
    public ModelConfigDO findByCode(String code) {
        if (StringUtils.isBlank(code)) {
            return null;
        }
        String key = code.toLowerCase().trim();
        return dataMapByCode.get(key);
    }

    @Override
    public ModelConfigDO getByCode(String code) {
        return findByCode(code);
    }

    @Override
    public ModelConfigDO findByName(String name) {
        if (StringUtils.isBlank(name)) {
            return null;
        }
        String key = name.toLowerCase().trim();
        ModelConfigDO model = dataMapByName.get(key);
        if (model != null) {
            return model;
        }
        return dataMapByCode.get(key);
    }

    @Override
    public ModelConfigDO findByNameOrCode(String nameOrCode) {
        if (StringUtils.isBlank(nameOrCode)) {
            return null;
        }
        String key = nameOrCode.toLowerCase().trim();
        ModelConfigDO model = dataMapByName.get(key);
        if (model != null) {
            return model;
        }
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
        Map<String, ModelConfigDO> mapByName = new HashMap<>();
        for (ModelConfigDO model : remoteList) {
            if (model != null) {
                String code = StringUtils.isNotBlank(model.getCode()) ? model.getCode() : model.getName();
                if (StringUtils.isNotBlank(code)) {
                    if (StringUtils.isBlank(model.getCode())) {
                        model.setCode(code);
                    }
                    mapByCode.put(code.toLowerCase().trim(), model);
                }
                if (StringUtils.isNotBlank(model.getName())) {
                    mapByName.put(model.getName().toLowerCase().trim(), model);
                }
                if (StringUtils.isNotBlank(model.getModelName())) {
                    mapByCode.putIfAbsent(model.getModelName().toLowerCase().trim(), model);
                }
            }
        }

        this.dataList = Collections.unmodifiableList(new ArrayList<>(remoteList));
        this.dataMapByCode = Collections.unmodifiableMap(mapByCode);
        this.dataMapByName = Collections.unmodifiableMap(mapByName);
        logger.info("[ModelConfigRepository] 从远程接口成功加载 {} 条大模型配置", remoteList.size());
        return true;
    }
}
