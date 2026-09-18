package com.shane.orchestragent.integration.llm.impl;

import com.shane.orchestragent.common.exception.BizException;
import com.shane.orchestragent.common.utils.JsonUtils;
import com.shane.orchestragent.integration.llm.LlmCallback;
import com.shane.orchestragent.integration.llm.LlmClient;
import com.shane.orchestragent.integration.llm.LlmSseDataListener;
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
 * OpenAI / DeepSeek / 通用大模型兼容 HTTP 客户端标准实现
 * 专职负责与外部符合 OpenAI 规范的模型端点进行网络交互与协议解析，纯粹无伪造兜底
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

    private record ResolvedLlmEndpoint(String endpoint, String apiKey, String actualModel) {}

    private String normalizeEndpoint(String url) {
        if (StringUtils.isBlank(url)) {
            return url;
        }
        String clean = url.trim();
        while (clean.endsWith("/")) {
            clean = clean.substring(0, clean.length() - 1);
        }
        if (!clean.endsWith("/chat/completions")) {
            clean = clean + "/chat/completions";
        }
        return clean;
    }

    private ResolvedLlmEndpoint resolveEndpoint(String modelIdentifier) {
        String targetEndpoint = this.endpoint;
        String targetApiKey = this.apiKey;
        String actualModel = modelIdentifier;

        ModelConfigDO modelConfig = null;
        if (modelConfigRepository != null) {
            if (StringUtils.isNotBlank(modelIdentifier)) {
                modelConfig = modelConfigRepository.findByNameOrCode(modelIdentifier);
            }
            if (modelConfig == null) {
                modelConfig = modelConfigRepository.getDefaultModel();
            }
        }

        if (modelConfig != null) {
            if (StringUtils.isNotBlank(modelConfig.getEndpoint())) {
                targetEndpoint = modelConfig.getEndpoint();
            }
            if (StringUtils.isNotBlank(modelConfig.getApiKey())) {
                targetApiKey = modelConfig.getApiKey();
            }
            actualModel = modelConfig.getActualModelName();
        }

        targetEndpoint = normalizeEndpoint(targetEndpoint);

        if (StringUtils.isBlank(actualModel)) {
            actualModel = modelIdentifier;
        }

        return new ResolvedLlmEndpoint(targetEndpoint, targetApiKey, actualModel);
    }

    @Override
    public CompletableFuture<ChatResponseVO> asyncCall(ChatRequestDTO request, LlmCallback callback) throws BizException {
        CompletableFuture<ChatResponseVO> future = new CompletableFuture<>();

        String modelName = request != null ? request.getModel() : null;
        ResolvedLlmEndpoint resolved = resolveEndpoint(modelName);

        if (StringUtils.isBlank(resolved.endpoint()) || StringUtils.isBlank(resolved.apiKey())) {
            BizException ex = new BizException("LLM_CONFIG_MISSING", "大模型接入配置缺失: 未配置有效 endpoint 或 apiKey");
            if (callback != null) {
                callback.onError(ex);
            }
            future.completeExceptionally(ex);
            return future;
        }

        if (StringUtils.isBlank(resolved.actualModel())) {
            BizException ex = new BizException("LLM_MODEL_MISSING", "未指定调用的目标大模型代码 (model)");
            if (callback != null) {
                callback.onError(ex);
            }
            future.completeExceptionally(ex);
            return future;
        }

        try {
            Map<String, Object> body = new HashMap<>();
            body.put("model", resolved.actualModel());
            body.put("messages", request != null ? request.getMessages() : Collections.emptyList());
            if (request != null && request.getTemperature() != null) {
                body.put("temperature", request.getTemperature());
            }
            if (request != null && request.getMaxTokens() != null) {
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
    public CompletableFuture<Void> asyncCall(String requestJson, LlmSseDataListener listener) throws BizException {
        CompletableFuture<Void> future = new CompletableFuture<>();

        ChatRequestDTO request = JsonUtils.parseObject(requestJson, ChatRequestDTO.class);
        String modelName = request != null ? request.getModel() : null;
        ResolvedLlmEndpoint resolved = resolveEndpoint(modelName);

        if (StringUtils.isBlank(resolved.endpoint()) || StringUtils.isBlank(resolved.apiKey())) {
            BizException ex = new BizException("LLM_CONFIG_MISSING", "大模型接入配置缺失: 未配置有效 endpoint 或 apiKey");
            if (listener != null) {
                listener.onError(ex);
            }
            future.completeExceptionally(ex);
            return future;
        }

        try {
            Map<String, Object> bodyMap = JsonUtils.parseMap(requestJson);
            if (bodyMap == null) {
                bodyMap = new HashMap<>();
            }
            bodyMap.put("model", resolved.actualModel());
            String outgoingJson = JsonUtils.toJsonString(bodyMap);

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(resolved.endpoint()))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + resolved.apiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(outgoingJson))
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
        if (map == null) {
            return ChatResponseVO.ofSingle("", null);
        }
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
}
