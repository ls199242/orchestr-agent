package com.shane.orchestragent.repository.client;

import java.util.List;

/**
 * 远程配置接口数据获取客户端统一顶层接口
 * 定义从外部管理中心或端点服务拉取配置列表的标准契约
 *
 * @param <T> 配置实体模型类型
 * @author Shane
 */
public interface ApiClient<T> {

    /**
     * 从远程管理接口拉取对应类型的全量配置列表
     *
     * @return 配置实体列表
     */
    List<T> list();
}
