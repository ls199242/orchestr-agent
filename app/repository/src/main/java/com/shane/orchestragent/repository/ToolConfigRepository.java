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
     * 根据工具业务编码查询工具配置
     *
     * @param code 工具编码
     * @return 工具配置实体
     */
    @Override
    ToolConfigDO getByCode(String code);

    /**
     * 获取全量工具字典快照 (Key: ToolCode)
     *
     * @return 工具字典
     */
    Map<String, ToolConfigDO> getMap();
}
