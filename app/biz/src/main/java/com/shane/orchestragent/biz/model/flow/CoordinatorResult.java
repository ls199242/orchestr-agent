package com.shane.orchestragent.biz.model.flow;

import com.shane.orchestragent.biz.model.enums.CoordinatorActionEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Map;

/**
 * 多轮对话意图识别协同产出结果
 * 承载意图决策动作、直答/追问话术、选定策略与抽取到的策略入参
 *
 * @author Shane
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CoordinatorResult implements Serializable {

    /** 本轮意图决策动作 (DIRECT_REPLY / COLLECT / EXECUTE) */
    private CoordinatorActionEnum action;

    /** 直接答复或追问话术文本 (DIRECT_REPLY / COLLECT 时必填) */
    private String reply;

    /** 选定的目标策略业务编码 (COLLECT / EXECUTE 时必填) */
    private String strategyCode;

    /** 本轮从用户消息与会话历史中抽取到的策略入参键值对 */
    private Map<String, Object> params;

    /**
     * 是否需要移交 Planner 启动 ReAct 协同循环
     * 当且仅当 action == EXECUTE（策略参数已收集齐备）时返回 true
     */
    public boolean isHandoffToPlanner() {
        return CoordinatorActionEnum.EXECUTE == action;
    }
}
