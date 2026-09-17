package com.shane.orchestragent.biz.flow;

import com.shane.orchestragent.biz.context.StrategyContext;
import com.shane.orchestragent.common.enums.FlowStateEnum;
import com.shane.orchestragent.common.exception.BizException;

/**
 * 策略编排流接口
 *
 * @author Shane
 */
public interface StrategyFlow {

    String getFlowId();

    void initialize() throws BizException;

    void start();

    String execute() throws BizException;

    void stop();

    void addStreamListener(com.shane.orchestragent.biz.listener.FlowStreamListener listener);

    void addAgentEventListener(com.shane.orchestragent.biz.listener.FlowAgentEventListener listener);

    FlowStateEnum getState();

    StrategyContext getContext();
}
