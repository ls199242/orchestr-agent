package com.shane.orchestragent.repository;

/**
 * 仓储刷新变更通知监听回调接口
 *
 * @author Shane
 */
@FunctionalInterface
public interface RepositoryReloadCallback {

    /**
     * 当仓储完成数据重载后触发的回调处理方法
     *
     * @param repositoryName 发生重新加载的仓储名称
     */
    void onReload(String repositoryName);
}
