package com.shane.orchestragent.prompt.model;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 专精智能体元数据 PO
 *
 * @author Shane
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ExpertPO extends AgentPO {

    private String spec;
    private String skills;
}
