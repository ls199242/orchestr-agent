package com.shane.orchestragent.biz.model.flow;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 战略评估维度检查细项
 *
 * @author Shane
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvaluationCheckItem implements Serializable {

    private String dimension;
    private boolean passed;
    private String detail;
}
