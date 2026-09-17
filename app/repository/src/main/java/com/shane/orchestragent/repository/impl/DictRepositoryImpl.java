package com.shane.orchestragent.repository.impl;

import com.shane.orchestragent.repository.DictRepository;
import com.shane.orchestragent.repository.client.DictApiClient;
import com.shane.orchestragent.repository.model.DictDO;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.util.*;

/**
 * 全局运行字典仓储标准实现
 * 继承 AbstractConfigRepository 底座，通过 DictApiClient 从远程配置中心拉取运行时系统参数
 * 提供快速无锁只读访问与内置缺省参数自动保底
 *
 * @author Shane
 */
@Primary
@Repository("dictRepository")
public class DictRepositoryImpl extends AbstractConfigRepository<DictDO> implements DictRepository {

    /** 远程字典接口客户端 */
    @Autowired(required = false)
    private DictApiClient dictApiClient;

    /** 字典实体列表快照 */
    private volatile List<DictDO> dataList = Collections.emptyList();

    /** 字典键值映射字典快照 */
    private volatile Map<String, String> dictMap = Collections.emptyMap();

    @Override
    public String getValue(String key, String defaultValue) {
        if (StringUtils.isBlank(key) || !dictMap.containsKey(key)) {
            return defaultValue;
        }
        String val = dictMap.get(key);
        return val != null ? val : defaultValue;
    }

    @Override
    public boolean getValue(String key, boolean defaultValue) {
        String val = getValue(key, null);
        if (val == null) {
            return defaultValue;
        }
        try {
            return Boolean.parseBoolean(val.trim());
        } catch (Exception e) {
            return defaultValue;
        }
    }

    @Override
    public int getValue(String key, int defaultValue) {
        String val = getValue(key, null);
        if (val == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(val.trim());
        } catch (Exception e) {
            return defaultValue;
        }
    }

    @Override
    public long getValue(String key, long defaultValue) {
        String val = getValue(key, null);
        if (val == null) {
            return defaultValue;
        }
        try {
            return Long.parseLong(val.trim());
        } catch (Exception e) {
            return defaultValue;
        }
    }

    @Override
    public Map<String, String> getDictMap() {
        return this.dictMap;
    }

    @Override
    public List<DictDO> getAll() {
        return this.dataList;
    }

    @Override
    protected boolean doReload() throws Exception {
        if (dictApiClient == null) {
            logger.warn("[DictRepository] dictApiClient 未注入");
            return false;
        }
        List<DictDO> remoteList = dictApiClient.list();
        if (remoteList == null) {
            logger.warn("[DictRepository] 远程接口未就绪或请求失败，保留现有本地缓存(size={})", dataList.size());
            return false;
        }

        Map<String, String> map = new HashMap<>();
        for (DictDO dict : remoteList) {
            if (dict != null && StringUtils.isNotBlank(dict.getKey())) {
                map.put(dict.getKey(), dict.getValue());
            }
        }

        this.dataList = Collections.unmodifiableList(new ArrayList<>(remoteList));
        this.dictMap = Collections.unmodifiableMap(map);
        logger.info("[DictRepository] 从远程接口成功加载 {} 条字典配置", remoteList.size());
        return true;
    }
}
