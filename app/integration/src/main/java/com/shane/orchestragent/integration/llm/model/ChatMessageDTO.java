package com.shane.orchestragent.integration.llm.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 基础聊天消息 DTO
 *
 * @author Shane
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageDTO implements Serializable {

    private String role;
    private String content;
    private String name;

    public ChatMessageDTO(String role, String content) {
        this.role = role;
        this.content = content;
    }
}
