package com.shane.orchestragent.integration.llm.model;

import com.shane.orchestragent.common.enums.LlmRoleEnum;
import lombok.NoArgsConstructor;

/**
 * 系统消息 DTO
 *
 * @author Shane
 */
@NoArgsConstructor
public class SystemChatMessageDTO extends ChatMessageDTO {

    public SystemChatMessageDTO(String content) {
        super(LlmRoleEnum.SYSTEM.getRole(), content);
    }
}
