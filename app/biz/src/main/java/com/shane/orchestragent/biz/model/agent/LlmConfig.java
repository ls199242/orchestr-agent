package com.shane.orchestragent.biz.model.agent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 大模型调用配置 (纯粹执行与模型参数，彻底解耦提示词模板)
 *
 * @author Shane
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LlmConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    private String model;
    private Double temperature;
    private Integer maxTokens;
    private Double presencePenalty;
    private Long nodeWaitTime;

    /** 智能体固定系统提示词模板 */
    private String systemPrompt;

    /** 智能体用户提示词模板（带占位符） */
    private String userPrompt;

    public Long getNodeWaitTime() {
        return nodeWaitTime != null && nodeWaitTime > 0 ? nodeWaitTime : 30000L;
    }
}
