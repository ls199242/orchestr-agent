package com.shane.orchestragent.repository;

import com.shane.orchestragent.repository.model.PromptGroupConfigDO;

/**
 * 提示词组编排绑定仓储接口
 *
 * @author Shane
 */
public interface PromptGroupRepository extends Repository<PromptGroupConfigDO> {

    @Override
    default String name() {
        return "promptGroupRepository";
    }

    /**
     * 根据策略编码与流程拓扑类型获取匹配的提示词组配置
     *
     * @param strategyCode 策略编码
     * @param flowType     流程类型（如 "REACT", "MULTIPLE_CHAT"）
     * @return 提示词组配置
     */
    PromptGroupConfigDO getByStrategyCode(String strategyCode, String flowType);

    /**
     * 根据流程类型、提示词组编码和版本号精确获取提示词组配置
     *
     * @param flowType 流程类型
     * @param code     提示词组编码
     * @param version  版本号
     * @return 提示词组配置
     */
    PromptGroupConfigDO getByCodeVersion(String flowType, String code, String version);
}
