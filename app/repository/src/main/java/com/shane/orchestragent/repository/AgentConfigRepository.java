package com.shane.orchestragent.repository;

import com.shane.orchestragent.repository.model.AgentConfigDO;

import java.util.Map;

/**
 * 智能体元数据配置仓储接口
 *
 * @author Shane
 */
public interface AgentConfigRepository extends Repository<AgentConfigDO> {

    @Override
    default String name() {
        return "agentConfigRepository";
    }

    /**
     * 根据智能体业务代码获取其配置
     *
     * @param code 智能体编码（如 arrival_recommend_worker, ROUTER 等）
     * @return 智能体配置实体
     */
    @Override
    AgentConfigDO getByCode(String code);

    /**
     * 根据智能体名称获取其配置
     *
     * @param name 智能体名称
     * @return 智能体配置实体
     */
    AgentConfigDO getByName(String name);

    /**
     * 根据智能体唯一名称获取配置（规范别名）
     *
     * @param agentName 智能体名称
     * @return 智能体配置实体
     */
    default AgentConfigDO getAgent(String agentName) {
        return getByName(agentName);
    }

    /**
     * 获取全量智能体配置字典视图 (Key: AgentCode)
     *
     * @return 智能体字典快照
     */
    Map<String, AgentConfigDO> getMap();
}
