package com.shane.orchestragent.biz.listener;

import com.shane.orchestragent.biz.agent.Agent;
import com.shane.orchestragent.common.enums.LlmRoleEnum;
import com.shane.orchestragent.integration.llm.model.ChatResponseVO;

/**
 * 智能体流式输出监听器 (修正原 steamListeners 拼写缺陷)
 *
 * @author Shane
 */
public interface LlmAgentStreamListener {

    /**
     * 提示词发送事件
     */
    void onUserPrompt(Agent agent, LlmRoleEnum role, String prompt);

    /**
     * 流式/异步数据增量返回事件
     */
    void onChatResponse(Agent agent, ChatResponseVO data, Throwable error);
}
