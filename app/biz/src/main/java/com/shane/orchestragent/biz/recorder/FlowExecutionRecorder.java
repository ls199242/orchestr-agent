package com.shane.orchestragent.biz.recorder;

import com.shane.orchestragent.biz.listener.FlowAgentEventListener;
import com.shane.orchestragent.biz.listener.FlowStateChangeListener;

/**
 * 流程执行记录器 (统一聚合状态流转与节点执行切面)
 *
 * @author Shane
 */
public interface FlowExecutionRecorder extends FlowStateChangeListener, FlowAgentEventListener {
}
