package com.shane.orchestragent.repository.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Map;

/**
 * 协同专精工作智能体配置 (原 StrategyExpertDO)
 *
 * @author Shane
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StrategyWorkerDO implements Serializable {

    private String name;
    private String description;
    private String prompt;
    private String model;
    private Double temperature;
    private Integer maxTokens;
    private Long nodeWaitTime;
    private Map<String, Object> extra;
}
