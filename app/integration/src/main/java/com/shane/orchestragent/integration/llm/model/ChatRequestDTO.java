package com.shane.orchestragent.integration.llm.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 大模型请求 DTO
 *
 * @author Shane
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatRequestDTO implements Serializable {

    private String model;
    private List<ChatMessageDTO> messages;
    private Double temperature;
    private Integer maxTokens;
    private Double presencePenalty;
    private boolean stream;
}
