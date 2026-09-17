package com.shane.orchestragent.biz.listener;

import com.shane.orchestragent.biz.agent.Agent;
import com.shane.orchestragent.integration.llm.model.ChatResponseVO;

/**
 * 流程级流式监听器
 *
 * @author Shane
 */
public interface FlowStreamListener {

    void onStream(Agent agent, ChatResponseVO chunk);
}
