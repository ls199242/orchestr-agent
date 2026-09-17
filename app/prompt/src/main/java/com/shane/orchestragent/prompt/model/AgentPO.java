package com.shane.orchestragent.prompt.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 智能体元数据 PO
 *
 * @author Shane
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentPO implements Serializable {

    private String name;
    private String description;
    private String prompt;
    private String agentType;
}
