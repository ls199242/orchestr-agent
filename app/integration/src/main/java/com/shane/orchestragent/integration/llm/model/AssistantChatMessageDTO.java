package com.shane.orchestragent.integration.llm.model;

import com.shane.orchestragent.common.enums.LlmRoleEnum;
import lombok.NoArgsConstructor;

/**
 * 助手消息 DTO
 *
 * @author Shane
 */
@NoArgsConstructor
public class AssistantChatMessageDTO extends ChatMessageDTO {

    public AssistantChatMessageDTO(String content) {
        super(LlmRoleEnum.ASSISTANT.getRole(), content);
    }
}
