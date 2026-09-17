package com.shane.orchestragent.memory.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 会话历史视图对象
 *
 * @author Shane
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionLogVO implements Serializable {

    private String role;
    private String content;
    private String messageType;
    private Long timestamp;
}
