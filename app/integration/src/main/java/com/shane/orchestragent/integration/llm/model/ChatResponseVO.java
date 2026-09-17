package com.shane.orchestragent.integration.llm.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * 完整大模型响应结果 VO
 *
 * @author Shane
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponseVO implements Serializable {

    private String id;
    private String model;
    private List<ChatMessageVO> chatMessages;

    public ChatMessageVO getFirstMessage() {
        if (chatMessages != null && !chatMessages.isEmpty()) {
            return chatMessages.get(0);
        }
        return null;
    }

    public static ChatResponseVO ofSingle(String content, String reasoningContent) {
        ChatMessageVO vo = ChatMessageVO.builder()
                .role("assistant")
                .content(content)
                .reasoningContent(reasoningContent)
                .build();
        return ChatResponseVO.builder()
                .chatMessages(Collections.singletonList(vo))
                .build();
    }
}
