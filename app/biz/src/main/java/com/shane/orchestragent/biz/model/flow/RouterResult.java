package com.shane.orchestragent.biz.model.flow;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 路由分流产出结果 (原 CoordinatorResult)
 *
 * @author Shane
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RouterResult implements Serializable {

    /** 是否移交给规划器处理复杂任务 */
    private boolean handoffToPlanner;

    /** 直接回答文本 (当无需移交规划器时) */
    private String reply;
}
