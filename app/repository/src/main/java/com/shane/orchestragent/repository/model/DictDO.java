package com.shane.orchestragent.repository.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 全局运行字典键值实体
 * 用于在系统中动态存储和更新运行时参数（如超时时间、最大步数上限等）
 *
 * @author Shane
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DictDO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 配置项键名 */
    private String key;

    /** 配置项字符串取值 */
    private String value;

    /** 配置项业务含义描述 */
    private String description;
}
