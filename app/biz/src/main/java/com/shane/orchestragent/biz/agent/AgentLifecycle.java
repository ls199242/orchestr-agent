package com.shane.orchestragent.biz.agent;

/**
 * 智能体生命周期接口
 *
 * @author Shane
 */
public interface AgentLifecycle {

    void start();

    void stop();

    boolean isStopped();
}
