package com.shane.orchestragent.biz.model.agent;

import com.shane.orchestragent.integration.llm.model.ChatResponseVO;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 大模型智能体结果
 *
 * @author Shane
 */
@Getter
@Setter
@NoArgsConstructor
public class LlmAgentResult extends AgentResult {

    private ChatResponseVO rawResponse;

    public LlmAgentResult(String output, String reasoningContent, ChatResponseVO rawResponse) {
        super(output, reasoningContent);
        this.rawResponse = rawResponse;
    }
}
