package com.shane.orchestragent.memory.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Date;

/**
 * 对话缓存单元
 *
 * @author Shane
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationCacheUnit implements Serializable {

    private String sessionId;
    private String messageId;
    private String role;
    private String content;
    private String agentType;
    private Date createTime;
}
