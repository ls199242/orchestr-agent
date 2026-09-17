package com.shane.orchestragent.biz.listener;

import com.shane.orchestragent.common.enums.FlowStateEnum;

/**
 * 流程状态变更监听器
 *
 * @author Shane
 */
public interface FlowStateChangeListener {

    void onStateChange(String flowId, FlowStateEnum oldState, FlowStateEnum newState);
}
