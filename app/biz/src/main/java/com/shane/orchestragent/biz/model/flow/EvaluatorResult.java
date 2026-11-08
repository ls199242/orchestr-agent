package com.shane.orchestragent.biz.model.flow;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 战略目标审查与自愈评估结果
 *
 * @author Shane
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvaluatorResult implements Serializable {

    /** 战略目标核验是否通过 */
    private boolean pass;

    /** 综合达成度评分 (0 - 100) */
    private int score;

    /** 批评诊断意见 (指出遗漏信息、未满足约束或逻辑漏洞) */
    private String critique;

    /** 调度修正建议 (供 Supervisor 派发补救任务) */
    private String suggestedRemedy;

    /** 责任步骤溯源清单 (指定哪些 stepId 验收失败需要局部重跑) */
    private List<String> blamedStepIds;

    /** 细项检查列表 */
    private List<EvaluationCheckItem> checkItems;
}
