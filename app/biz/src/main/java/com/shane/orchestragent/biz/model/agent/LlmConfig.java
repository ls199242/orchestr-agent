package com.shane.orchestragent.biz.model.agent;

import com.shane.orchestragent.prompt.model.PromptTypeEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Map;

/**
 * 大模型调用配置
 *
 * @author Shane
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LlmConfig implements Serializable {

    private String model;
    private Double temperature;
    private Integer maxTokens;
    private Double presencePenalty;
    private Long nodeWaitTime;
    private Map<PromptTypeEnum, String> promptTemplates;

    public Long getNodeWaitTime() {
        return nodeWaitTime != null && nodeWaitTime > 0 ? nodeWaitTime : 30000L;
    }
}
