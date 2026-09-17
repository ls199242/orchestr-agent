package com.shane.orchestragent.repository;

import java.util.List;

/**
 * 通用仓储顶层抽象接口
 * 定义配置元数据仓储的标准行为契约，包括标识名称、数据全量获取、热重载以及变更监听回调
 *
 * @param <T> 仓储管理的数据实体类型
 * @author Shane
 */
public interface Repository<T> {

    /**
     * 仓储唯一名称标识
     *
     * @return 仓储名称
     */
    String name();

    /**
     * 手动触发配置缓存刷新与重新加载
     */
    void reload();

    /**
     * 获取仓储中当前缓存的全部配置项集合快照
     *
     * @return 全部配置实体列表
     */
    List<T> getAll();

    /**
     * 注册仓储刷新监听回调器
     *
     * @param callback 刷新回调实例
     */
    void addCallback(RepositoryReloadCallback callback);

    /**
     * 移除已注册的刷新监听回调器
     *
     * @param callback 刷新回调实例
     */
    void removeCallback(RepositoryReloadCallback callback);
}
