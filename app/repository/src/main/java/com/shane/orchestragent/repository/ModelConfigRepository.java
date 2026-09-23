package com.shane.orchestragent.repository;

import com.shane.orchestragent.repository.model.ModelConfigDO;

/**
 * 大模型配置仓储接口
 * 负责大模型代码、上下文长度限制、接入端点与鉴权凭证的查询与管理
 *
 * @author Shane
 */
public interface ModelConfigRepository extends Repository<ModelConfigDO> {

    @Override
    default String name() {
        return "modelConfigRepository";
    }

    /**
     * 根据模型唯一编码查询模型接入配置
     *
     * @param code 模型代码（如 "gpt-4o", "Deepseek", "deepseek-flash"）
     * @return 模型配置实体
     */
    @Override
    ModelConfigDO getByCode(String code);

    /**
     * 获取系统当前默认推荐的大模型配置
     *
     * @return 默认模型配置
     */
    ModelConfigDO getDefaultModel();
}
