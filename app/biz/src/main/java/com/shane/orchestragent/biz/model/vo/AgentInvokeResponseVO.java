package com.shane.orchestragent.biz.model.vo;

import com.shane.orchestragent.biz.model.flow.EvaluatorResult;
import com.shane.orchestragent.biz.model.flow.FlowProcessText;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 智能体编排调用响应业务对象 (VO)
 *
 * @author Shane
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentInvokeResponseVO implements Serializable {

    /** 流程执行唯一标识 (FlowId) */
    private String flowId;

    /** 会话唯一标识 (SessionId) */
    private String sessionId;

    /** 多智能体协同最终生成的答复文本或交付成果物 */
    private String reply;

    /** 流程当前运行状态 (如: INITIAL, RUNNING, FINISHED, ERROR, STOPPED) */
    private String state;

    /** Evaluator 验收评估器的质检自愈反馈结果 (包含是否通过、打分、诊断与整改建议) */
    private EvaluatorResult evaluation;

    /** 流程执行期间各节点产生的明细文案列表 */
    private List<FlowProcessText> processTexts;

    /** 整个流程从启动到结束的执行耗时 (毫秒) */
    private Long costMs;
}
