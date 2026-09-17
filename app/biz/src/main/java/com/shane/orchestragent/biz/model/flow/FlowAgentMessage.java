package com.shane.orchestragent.biz.model.flow;

import com.shane.orchestragent.common.enums.AgentTypeEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 流程中智能体消息记录
 *
 * @author Shane
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlowAgentMessage implements Serializable {

    private AgentTypeEnum agentType;
    private String agentName;
    private String output;
    private Long timestamp;
}
