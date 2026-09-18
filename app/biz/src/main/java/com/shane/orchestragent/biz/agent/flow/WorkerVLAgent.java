package com.shane.orchestragent.biz.agent.flow;

import com.shane.orchestragent.biz.context.StrategyContext;
import com.shane.orchestragent.biz.model.agent.LlmConfig;
import com.shane.orchestragent.biz.service.LlmService;
import com.shane.orchestragent.common.exception.BizException;
import com.shane.orchestragent.integration.llm.model.ChatMessageDTO;
import com.shane.orchestragent.integration.llm.model.UserChatMessageDTO;
import com.shane.orchestragent.prompt.service.PromptService;
import com.shane.orchestragent.repository.model.AgentConfigDO;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 视觉多模态工作智能体 (支持图片、文档多模态交互)
 *
 * @author Shane
 */
public class WorkerVLAgent extends WorkerAgent {

    public WorkerVLAgent(AgentConfigDO agentConfig, LlmConfig llmConfig, LlmService llmService, PromptService promptService) {
        super(agentConfig, llmConfig, llmService, promptService);
    }

    @Override
    protected List<ChatMessageDTO> buildMessages(StrategyContext context) throws BizException {
        Map<String, Object> request = context.getNextAgentRequest();
        String imageUrl = request != null && request.get("imageUrl") != null ? String.valueOf(request.get("imageUrl")) : "";
        String text = request != null && request.get("text") != null ? String.valueOf(request.get("text")) : "分析此视觉图像信息";

        String content = "【多模态输入】图片地址: " + imageUrl + "\n分析诉求: " + text;
        return Collections.singletonList(new UserChatMessageDTO(content));
    }
}
