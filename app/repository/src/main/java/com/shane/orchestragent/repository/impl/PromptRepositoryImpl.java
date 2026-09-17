package com.shane.orchestragent.repository.impl;

import com.shane.orchestragent.repository.PromptRepository;
import com.shane.orchestragent.repository.client.PromptConfigApiClient;
import com.shane.orchestragent.repository.model.PromptConfigDO;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.util.*;

/**
 * 提示词模板配置仓储标准实现
 * 继承 AbstractConfigRepository 底座，通过 PromptConfigApiClient 从远程管理平台动态获取最新 Prompt 模板
 *
 * @author Shane
 */
@Primary
@Repository("promptConfigRepository")
public class PromptRepositoryImpl extends AbstractConfigRepository<PromptConfigDO> implements PromptRepository {

    /** 远程提示词接口客户端 */
    @Autowired(required = false)
    private PromptConfigApiClient promptConfigApiClient;

    /** 全量提示词列表快照 */
    private volatile List<PromptConfigDO> dataList = Collections.emptyList();

    /** 按名称索引字典快照 */
    private volatile Map<String, PromptConfigDO> dataMapByName = Collections.emptyMap();

    /** 按编码索引字典快照 */
    private volatile Map<String, PromptConfigDO> dataMapByCode = Collections.emptyMap();

    @Override
    public PromptConfigDO get(String name) {
        if (StringUtils.isBlank(name)) {
            return null;
        }
        if (dataMapByName.containsKey(name)) {
            return dataMapByName.get(name);
        }
        return dataMapByCode.get(name);
    }

    @Override
    public PromptConfigDO findByCode(String code) {
        if (StringUtils.isBlank(code)) {
            return null;
        }
        return dataMapByCode.get(code);
    }

    @Override
    public List<PromptConfigDO> getAll() {
        return this.dataList;
    }

    @Override
    protected boolean doReload() throws Exception {
        if (promptConfigApiClient == null) {
            logger.warn("[PromptConfigRepository] promptConfigApiClient 未注入");
            return false;
        }
        List<PromptConfigDO> remoteList = promptConfigApiClient.list();
        if (remoteList == null) {
            logger.warn("[PromptConfigRepository] 远程接口未就绪或请求失败，保留现有本地缓存(size={})", dataList.size());
            return false;
        }

        Map<String, PromptConfigDO> mapByName = new HashMap<>();
        Map<String, PromptConfigDO> mapByCode = new HashMap<>();
        for (PromptConfigDO prompt : remoteList) {
            if (prompt != null) {
                if (StringUtils.isNotBlank(prompt.getName())) {
                    mapByName.put(prompt.getName(), prompt);
                }
                if (StringUtils.isNotBlank(prompt.getCode())) {
                    mapByCode.put(prompt.getCode(), prompt);
                }
            }
        }

        this.dataList = Collections.unmodifiableList(new ArrayList<>(remoteList));
        this.dataMapByName = Collections.unmodifiableMap(mapByName);
        this.dataMapByCode = Collections.unmodifiableMap(mapByCode);
        logger.info("[PromptConfigRepository] 从远程接口成功加载 {} 条提示词配置", remoteList.size());
        return true;
    }
}
