package com.shane.orchestragent.web.dto;

import com.shane.orchestragent.biz.model.flow.EvaluatorResult;
import com.shane.orchestragent.biz.model.flow.FlowProcessText;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 智能体编排调用响应数据传输对象 (DTO)
 * 供 Controller 向外部客户端返回结构化数据
 *
 * @author Shane
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentInvokeResponseDTO implements Serializable {

    /** 流程执行唯一标识 (FlowId) */
    private String flowId;

    /** 会话唯一标识 (SessionId) */
    private String sessionId;

    /** 多智能体协同最终生成的答复产物 */
    private String reply;

    /** 流程执行状态 (INITIAL, RUNNING, FINISHED, ERROR, STOPPED 等) */
    private String state;

    /** 战略验收评估器 (Evaluator) 的质检自愈反馈结果 */
    private EvaluatorResult evaluation;

    /** 流程执行过程中的文案明细列表 */
    private List<FlowProcessText> processTexts;

    /** 流程执行耗时统计 (毫秒) */
    private Long costMs;
}
