package com.shane.orchestragent.biz.model.flow;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 流程步骤过程文案
 *
 * @author Shane
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlowProcessText implements Serializable {

    /** 流程节点标题 */
    private String title;

    /** 流程节点释义说明 */
    private String description;

    /** 时间戳 */
    private Long timestamp;
}
