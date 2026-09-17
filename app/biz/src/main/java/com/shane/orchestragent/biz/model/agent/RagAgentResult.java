package com.shane.orchestragent.biz.model.agent;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;

/**
 * 知识库检索结果包装
 *
 * @author Shane
 */
@Getter
@Setter
@NoArgsConstructor
public class RagAgentResult extends AgentResult {

    private Map<String, Object> tokenUsages;

    public RagAgentResult(String output, String reasoningContent, Map<String, Object> tokenUsages) {
        super(output, reasoningContent);
        this.tokenUsages = tokenUsages;
    }
}
