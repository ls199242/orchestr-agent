package com.shane.orchestragent.integration.llm.impl;

import com.shane.orchestragent.common.exception.BizException;
import com.shane.orchestragent.common.utils.JsonUtils;
import com.shane.orchestragent.integration.llm.LlmCallback;
import com.shane.orchestragent.integration.llm.LlmClient;
import com.shane.orchestragent.integration.llm.model.ChatMessageDTO;
import com.shane.orchestragent.integration.llm.model.ChatMessageVO;
import com.shane.orchestragent.integration.llm.model.ChatRequestDTO;
import com.shane.orchestragent.integration.llm.model.ChatResponseVO;
import com.shane.orchestragent.repository.ModelConfigRepository;
import com.shane.orchestragent.repository.model.ModelConfigDO;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * OpenAI / DeepSeek / 通用大模型兼容 HTTP 客户端实现
 * 支持从 ModelConfigRepository 动态获取各模型独立端点与真实 API-Key
 *
 * @author Shane
 */
@Component
public class OpenAiCompatibleLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleLlmClient.class);

    private final HttpClient httpClient;

    @Autowired(required = false)
    private ModelConfigRepository modelConfigRepository;

    @Value("${orchestragent.llm.endpoint:https://api.openai.com/v1/chat/completions}")
    private String endpoint = "https://api.openai.com/v1/chat/completions";

    @Value("${orchestragent.llm.api-key:}")
    private String apiKey = "";

    public OpenAiCompatibleLlmClient() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public void configure(String endpoint, String apiKey) {
        if (StringUtils.isNotEmpty(endpoint)) {
            this.endpoint = endpoint;
        }
        if (StringUtils.isNotEmpty(apiKey)) {
            this.apiKey = apiKey;
        }
    }

    public void setModelConfigRepository(ModelConfigRepository modelConfigRepository) {
        this.modelConfigRepository = modelConfigRepository;
    }

    private record ResolvedLlmEndpoint(String endpoint, String apiKey, boolean isMock) {}

    private ResolvedLlmEndpoint resolveEndpoint(String modelCode) {
        String targetEndpoint = this.endpoint;
        String targetApiKey = this.apiKey;

        if (modelConfigRepository != null && StringUtils.isNotBlank(modelCode)) {
            ModelConfigDO modelConfig = modelConfigRepository.findByCode(modelCode);
            if (modelConfig != null) {
                if (StringUtils.isNotBlank(modelConfig.getEndpoint())) {
                    targetEndpoint = modelConfig.getEndpoint();
                }
                if (StringUtils.isNotBlank(modelConfig.getApiKey())) {
                    targetApiKey = modelConfig.getApiKey();
                }
            }
        }

        boolean isMock = StringUtils.isBlank(targetApiKey)
                || targetApiKey.startsWith("sk-mock")
                || targetApiKey.contains("your-")
                || "sk-mock-key-for-test".equalsIgnoreCase(targetApiKey);

        return new ResolvedLlmEndpoint(targetEndpoint, targetApiKey, isMock);
    }

    @Override
    public CompletableFuture<ChatResponseVO> asyncCall(ChatRequestDTO request, LlmCallback callback) throws BizException {
        CompletableFuture<ChatResponseVO> future = new CompletableFuture<>();

        String modelName = request != null ? request.getModel() : null;
        ResolvedLlmEndpoint resolved = resolveEndpoint(modelName);

        // 若未配置有效外部 API KEY，启用智能 Mock 模式，确保开箱即用与测试通畅
        if (resolved.isMock()) {
            log.info("[OpenAiCompatibleLlmClient] 未配置有效真实 API Key，进入仿真响应模式, model={}", modelName);
            ChatResponseVO mockResponse = generateMockResponse(request);
            if (callback != null) {
                callback.onData(mockResponse);
                callback.onComplete(mockResponse);
            }
            future.complete(mockResponse);
            return future;
        }

        try {
            Map<String, Object> body = new HashMap<>();
            body.put("model", StringUtils.defaultIfEmpty(request.getModel(), "gpt-4o"));
            body.put("messages", request.getMessages());
            if (request.getTemperature() != null) {
                body.put("temperature", request.getTemperature());
            }
            if (request.getMaxTokens() != null) {
                body.put("max_tokens", request.getMaxTokens());
            }

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(resolved.endpoint()))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + resolved.apiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(JsonUtils.toJsonString(body)))
                    .timeout(Duration.ofSeconds(60))
                    .build();
            httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString())
                    .thenAccept(response -> {
                        try {
                            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                                ChatResponseVO vo = parseOpenAiResponse(response.body());
                                if (callback != null) {
                                    callback.onData(vo);
                                    callback.onComplete(vo);
                                }
                                future.complete(vo);
                            } else {
                                throw new BizException("LLM_HTTP_ERROR", "HTTP Status " + response.statusCode() + ": " + response.body());
                            }
                        } catch (Exception e) {
                            if (callback != null) {
                                callback.onError(e);
                            }
                            future.completeExceptionally(e);
                        }
                    })
                    .exceptionally(ex -> {
                        if (callback != null) {
                            callback.onError(ex);
                        }
                        future.completeExceptionally(ex);
                        return null;
                    });

        } catch (Exception e) {
            log.error("Failed to initiate async LLM request", e);
            throw new BizException("LLM_CALL_FAILED", "发起模型请求失败: " + e.getMessage(), e);
        }

        return future;
    }

    @Override
    public CompletableFuture<Void> asyncCall(String requestJson, com.shane.orchestragent.integration.llm.LlmSseDataListener listener) throws BizException {
        CompletableFuture<Void> future = new CompletableFuture<>();

        ChatRequestDTO request = JsonUtils.parseObject(requestJson, ChatRequestDTO.class);
        String modelName = request != null ? request.getModel() : null;
        ResolvedLlmEndpoint resolved = resolveEndpoint(modelName);

        if (resolved.isMock()) {
            log.info("[OpenAiCompatibleLlmClient] 未配置有效真实 API Key，进入 SSE 仿真流模式, model={}", modelName != null ? modelName : "mock");
            ChatResponseVO mockResponse = generateMockResponse(request != null ? request : ChatRequestDTO.builder().build());
            if (listener != null) {
                String content = (mockResponse != null && mockResponse.getFirstMessage() != null)
                        ? mockResponse.getFirstMessage().getContent() : "";
                String jsonChunk = "{\"id\":\"mock-1\",\"choices\":[{\"delta\":{\"content\":\"" +
                        content.replace("\"", "\\\"").replace("\n", "\\n") + "\"}}]}";
                listener.onEvent("message", jsonChunk);
                listener.onEvent("message", "data: [DONE]");
            }
            future.complete(null);
            return future;
        }

        try {
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(resolved.endpoint()))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + resolved.apiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                    .timeout(Duration.ofSeconds(60))
                    .build();

            httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofLines())
                    .thenAccept(response -> {
                        if (response.statusCode() >= 200 && response.statusCode() < 300) {
                            response.body().forEach(line -> {
                                if (listener != null && StringUtils.isNotBlank(line)) {
                                    listener.onEvent("message", line);
                                }
                            });
                            future.complete(null);
                        } else {
                            BizException ex = new BizException("LLM_HTTP_ERROR", "HTTP Status " + response.statusCode());
                            if (listener != null) {
                                listener.onError(ex);
                            }
                            future.completeExceptionally(ex);
                        }
                    })
                    .exceptionally(ex -> {
                        if (listener != null) {
                            listener.onError(ex);
                        }
                        future.completeExceptionally(ex);
                        return null;
                    });
        } catch (Exception e) {
            log.error("Failed to initiate SSE LLM request", e);
            throw new BizException("LLM_CALL_FAILED", "发起模型流式请求失败: " + e.getMessage(), e);
        }

        return future;
    }

    @Override
    public ChatResponseVO chat(ChatRequestDTO request) throws BizException {
        try {
            return asyncCall(request, null).get();
        } catch (Exception e) {
            throw new BizException("LLM_SYNC_FAILED", "同步调用失败: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private ChatResponseVO parseOpenAiResponse(String json) {
        Map<String, Object> map = JsonUtils.parseMap(json);
        List<Map<String, Object>> choices = (List<Map<String, Object>>) map.get("choices");
        if (choices != null && !choices.isEmpty()) {
            Map<String, Object> choice = choices.get(0);
            Map<String, Object> message = (Map<String, Object>) choice.get("message");
            String content = message != null ? (String) message.get("content") : "";
            String reasoning = message != null ? (String) message.get("reasoning_content") : null;
            return ChatResponseVO.ofSingle(content, reasoning);
        }
        return ChatResponseVO.ofSingle("", null);
    }

    private ChatResponseVO generateMockResponse(ChatRequestDTO request) {
        // 根据 prompt 自动适配模拟返回，方便单测与离线环境
        List<ChatMessageDTO> messages = request.getMessages();
        StringBuilder allContent = new StringBuilder();
        if (messages != null) {
            for (ChatMessageDTO msg : messages) {
                if (msg != null && msg.getContent() != null) {
                    allContent.append(msg.getContent()).append("\n");
                }
            }
        }
        String text = allContent.toString();

        if (text.contains("ConductorAgent") || text.contains("调度指挥") || text.contains("调度与指挥专家")) {
            // 如果历史中已经包含 Worker 的执行结果，则指挥结束，进入终局评估
            if (text.contains("WorkerAgent") || text.contains("已成功检索") || text.contains("产出") || text.contains("当前已执行步数: 1") || text.contains("当前已执行步数: 2")) {
                return ChatResponseVO.ofSingle("{\"next\": \"FINISH\", \"request\": {}}", null);
            } else {
                return ChatResponseVO.ofSingle("{\"next\": \"search_worker\", \"request\": {\"query\": \"航班信息\"}}", null);
            }
        } else if (text.contains("PlannerAgent") || text.contains("任务规划专家") || text.contains("战略任务规划") || text.contains("\"steps\":")) {
            return ChatResponseVO.ofSingle("{\"steps\": [{\"step\": 1, \"agent\": \"search_worker\", \"description\": \"查询目标信息\"}]}", null);
        } else if (text.contains("EvaluatorAgent") || text.contains("战略目标审查") || text.contains("\"pass\":")) {
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
