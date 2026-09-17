package com.shane.orchestragent.repository;

import com.shane.orchestragent.repository.model.ToolConfigDO;

import java.util.Map;

/**
 * 工具元数据配置仓储接口
 *
 * @author Shane
 */
public interface ToolConfigRepository extends Repository<ToolConfigDO> {

    @Override
    default String name() {
        return "toolConfigRepository";
    }

    /**
     * 根据工具名称获取工具配置
     *
     * @param name 工具名称
     * @return 工具配置实体
     */
    ToolConfigDO getByName(String name);

    /**
     * 根据工具 ID 或名称查询工具配置（规范别名）
     *
     * @param id 工具 ID 或名称
     * @return 工具配置实体
     */
    default ToolConfigDO findToolById(String id) {
        return getByName(id);
    }

    /**
     * 根据工具业务编码查询工具配置
     *
     * @param code 工具编码
     * @return 工具配置实体
     */
    ToolConfigDO findByCode(String code);


    /**
     * 获取全量工具字典快照 (Key: ToolName)
     *
     * @return 工具字典
     */
    Map<String, ToolConfigDO> getMap();
}
