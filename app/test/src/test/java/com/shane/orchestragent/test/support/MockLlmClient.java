package com.shane.orchestragent.test.support;

import com.shane.orchestragent.common.exception.BizException;
import com.shane.orchestragent.integration.llm.LlmCallback;
import com.shane.orchestragent.integration.llm.LlmClient;
import com.shane.orchestragent.integration.llm.LlmSseDataListener;
import com.shane.orchestragent.integration.llm.model.ChatMessageDTO;
import com.shane.orchestragent.integration.llm.model.ChatRequestDTO;
import com.shane.orchestragent.integration.llm.model.ChatResponseVO;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * 仅用于单元测试与集成测试的 Mock 大模型客户端
 * 严格归属于 src/test 范围，杜绝污染生产环境代码
 *
 * @author Shane
 */
public class MockLlmClient implements LlmClient {

    private Function<ChatRequestDTO, ChatResponseVO> customResponder;

    public void setCustomResponder(Function<ChatRequestDTO, ChatResponseVO> responder) {
        this.customResponder = responder;
    }

    @Override
    public CompletableFuture<ChatResponseVO> asyncCall(ChatRequestDTO request, LlmCallback callback) throws BizException {
        ChatResponseVO response = resolveResponse(request);
        if (callback != null) {
            callback.onData(response);
            callback.onComplete(response);
        }
        return CompletableFuture.completedFuture(response);
    }

    @Override
    public CompletableFuture<Void> asyncCall(String requestJson, LlmSseDataListener listener) throws BizException {
        ChatRequestDTO request = com.shane.orchestragent.common.utils.JsonUtils.parseObject(requestJson, ChatRequestDTO.class);
        ChatResponseVO response = resolveResponse(request);
        if (listener != null) {
            String content = (response != null && response.getFirstMessage() != null)
                    ? response.getFirstMessage().getContent() : "";
            String escaped = content.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
            String chunk = "{\"id\":\"mock-sse-1\",\"choices\":[{\"delta\":{\"content\":\"" + escaped + "\"}}]}";
            listener.onEvent("message", chunk);
            listener.onEvent("message", "data: [DONE]");
        }
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public ChatResponseVO chat(ChatRequestDTO request) throws BizException {
        return resolveResponse(request);
    }

    private ChatResponseVO resolveResponse(ChatRequestDTO request) {
        if (customResponder != null && request != null) {
            ChatResponseVO custom = customResponder.apply(request);
            if (custom != null) {
                return custom;
            }
        }

        StringBuilder allText = new StringBuilder();
        if (request != null && request.getMessages() != null) {
            for (ChatMessageDTO msg : request.getMessages()) {
                if (msg != null && msg.getContent() != null) {
                    allText.append(msg.getContent()).append("\n");
                }
            }
        }
        String text = allText.toString();

        if (text.contains("ConductorAgent") || text.contains("调度指挥") || text.contains("调度与指挥专家")) {
            if (text.contains("WorkerAgent") || text.contains("已成功检索") || text.contains("产出")
                    || text.contains("当前已执行步数: 1") || text.contains("当前已执行步数: 2")) {
                return ChatResponseVO.ofSingle("{\"next\": \"FINISH\", \"request\": {}}", null);
            } else {
                return ChatResponseVO.ofSingle("{\"next\": \"search_worker\", \"request\": {\"query\": \"航班信息\"}}", null);
            }
        } else if (text.contains("PlannerAgent") || text.contains("任务规划专家") || text.contains("战略任务规划") || text.contains("\"steps\":")) {
            return ChatResponseVO.ofSingle("{\"steps\": [{\"step\": 1, \"agent\": \"search_worker\", \"description\": \"查询目标信息\"}]}", null);
        } else if (text.contains("EvaluatorAgent") || text.contains("战略目标审查") || text.contains("战略核验") || text.contains("\"pass\":")) {
            return ChatResponseVO.ofSingle("{\"pass\": true, \"score\": 100, \"critique\": \"目标完全达成\", \"suggestedRemedy\": \"\"}", null);
        } else if (text.contains("RouterAgent") || text.contains("handoffToPlanner")) {
            return ChatResponseVO.ofSingle("{\"handoffToPlanner\": true, \"reply\": \"正在为您规划任务...\"}", null);
        } else if (text.contains("ReporterAgent") || text.contains("成果汇报") || text.contains("交付成果报告")) {
            return ChatResponseVO.ofSingle("【OrchestrAgent 任务完成】已成功处理您的需求，各智能体协同顺畅完成规划与交付。", null);
        } else {
            return ChatResponseVO.ofSingle("【WorkerAgent search_worker 产出】已成功检索并处理完成相关航班与业务信息。", null);
        }
    }
}
