package com.shane.orchestragent.memory.session;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Map;

/**
 * 多轮会话待定策略收集状态
 * 记录意图识别已选定但必填参数尚未收集齐备的目标策略及已累积的入参，支撑跨轮参数收集
 *
 * @author Shane
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionPendingState implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 待定策略业务编码 */
    private String strategyCode;

    /** 跨轮累积已收集的策略入参键值对 (本轮新值覆盖旧值) */
    private Map<String, Object> collectedParams;

    /** 最近一次更新时间戳 (毫秒) */
    private long updatedAt;
}
