package com.shane.orchestragent.prompt.model;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 工作智能体元数据 PO (原 ExpertPO)
 *
 * @author Shane
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class WorkerPO extends AgentPO {

    private String spec;
    private String skills;
}
