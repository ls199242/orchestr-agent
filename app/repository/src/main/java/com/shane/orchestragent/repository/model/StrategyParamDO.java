package com.shane.orchestragent.repository.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 策略入参定义实体
 * 声明策略执行所需的单个参数元信息，供多轮意图识别 (CoordinatorAgent) 进行参数收集与完整性终审
 *
 * @author Shane
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StrategyParamDO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 参数名（与策略提示词中的占位符变量名一致，如 flightNo） */
    private String name;

    /** 参数类型 (string / number / integer / boolean) */
    private String type;

    /** 参数业务含义描述（用于注入意图识别提示词与生成追问话术） */
    private String description;

    /** 是否必填（必填参数未收集齐备前严禁触发策略执行） */
    private boolean required;
}
