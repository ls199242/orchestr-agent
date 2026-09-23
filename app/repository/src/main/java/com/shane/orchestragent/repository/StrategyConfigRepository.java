package com.shane.orchestragent.repository;

import com.shane.orchestragent.repository.model.StrategyConfigDO;

import java.util.Map;

/**
 * 编排策略配置仓储接口
 * 负责协同策略元数据的按 ID、按 Code 查询与管理
 *
 * @author Shane
 */
public interface StrategyConfigRepository extends Repository<StrategyConfigDO> {

    @Override
    default String name() {
        return "strategyConfigRepository";
    }

    /**
     * 根据策略业务编码查询策略配置
     *
     * @param code 策略业务编码（如 "react_default_strategy"）
     * @return 策略配置实体
     */
    @Override
    StrategyConfigDO getByCode(String code);

    /**
     * 根据策略业务编码查询策略配置（规范别名）
     *
     * @param code 策略业务编码
     * @return 策略配置实体
     */
    default StrategyConfigDO findStrategyByCode(String code) {
        return getByCode(code);
    }

    /**
     * 根据策略主键 ID 获取策略配置（兼容旧有调用，直接转为按 Code 查询）
     *
     * @param strategyId 策略唯一标识
     * @return 策略配置实体
     */
    default StrategyConfigDO getById(String strategyId) {
        return getByCode(strategyId);
    }

    /**
     * 获取全量策略的 Map 视图 (Key: code)
     *
     * @return 策略字典
     */
    Map<String, StrategyConfigDO> getMap();
}
