package com.shane.orchestragent.biz.listener;

import com.shane.orchestragent.biz.flow.StrategyFlow;

/**
 * 策略流程调试器监听器
 *
 * @author Shane
 */
public interface FlowDebuggerEventListener {

    void onMessage(StrategyFlow flow, String message);

    void onError(StrategyFlow flow, Exception e);
}
