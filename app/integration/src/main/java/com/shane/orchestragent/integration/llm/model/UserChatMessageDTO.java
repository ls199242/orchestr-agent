package com.shane.orchestragent.integration.llm.model;

import com.shane.orchestragent.common.enums.LlmRoleEnum;
import lombok.NoArgsConstructor;

/**
 * 用户消息 DTO
 *
 * @author Shane
 */
@NoArgsConstructor
public class UserChatMessageDTO extends ChatMessageDTO {

    public UserChatMessageDTO(String content) {
        super(LlmRoleEnum.USER.getRole(), content);
    }
}
