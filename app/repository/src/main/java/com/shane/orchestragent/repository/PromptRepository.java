package com.shane.orchestragent.repository;

import com.shane.orchestragent.repository.model.PromptConfigDO;

/**
 * 提示词模板配置仓储接口
 *
 * @author Shane
 */
public interface PromptRepository extends Repository<PromptConfigDO> {

    @Override
    default String name() {
        return "promptConfigRepository";
    }

    /**
     * 根据提示词名称获取模板配置
     *
     * @param name 提示词名称（如 "PLANNER_SYSTEM"）
     * @return 提示词配置实体
     */
    PromptConfigDO get(String name);

    /**
     * 根据提示词编码获取模板配置
     *
     * @param code 提示词编码
     * @return 提示词配置实体
     */
    PromptConfigDO findByCode(String code);
}
