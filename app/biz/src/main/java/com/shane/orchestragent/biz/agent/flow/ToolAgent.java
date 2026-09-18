package com.shane.orchestragent.biz.agent.flow;

import com.shane.orchestragent.biz.agent.base.BaseAgent;
import com.shane.orchestragent.biz.context.AgentContext;
import com.shane.orchestragent.biz.context.StrategyContext;
import com.shane.orchestragent.biz.model.agent.AgentResult;
import com.shane.orchestragent.biz.model.flow.FlowAgentMessage;
import com.shane.orchestragent.common.enums.AgentTypeEnum;
import com.shane.orchestragent.common.exception.BizException;
import com.shane.orchestragent.common.utils.JsonUtils;
import com.shane.orchestragent.prompt.model.AgentPO;
import com.shane.orchestragent.repository.model.ToolConfigDO;
import org.apache.commons.lang3.StringUtils;

import com.shane.orchestragent.common.exception.BizErrorFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;

/**
 * 工具调用智能体 (基于 ToolConfigDO 发起真实外部 HTTP 接口调用与动作交互)
 *
 * @author Shane
 */
public class ToolAgent extends BaseAgent<AgentContext> {

    private static final Logger log = LoggerFactory.getLogger(ToolAgent.class);

    private final ToolConfigDO toolConfig;
    private final HttpClient httpClient;

    public ToolAgent(ToolConfigDO toolConfig) {
        this(toolConfig, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build());
    }

    public ToolAgent(ToolConfigDO toolConfig, HttpClient httpClient) {
        super(Objects.requireNonNull(toolConfig, "toolConfig 不能为空").getName(),
                toolConfig.getDescription(),
                AgentTypeEnum.TOOL);
        if (StringUtils.isBlank(toolConfig.getName())) {
            throw new IllegalArgumentException("toolConfig.name 不能为空");
        }
        this.toolConfig = toolConfig;
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient 不能为空");
    }

    @Override
    public AgentResult execute(AgentContext context) throws BizException {
        if (isStopped()) {
            return null;
        }

        Object request = context.getAgentRequest();
        if (request == null && context instanceof StrategyContext strategyContext) {
            request = strategyContext.getNextAgentRequest();
        }
        String requestJson = request != null ? JsonUtils.toJsonString(request) : "{}";

        String output = callTool(requestJson, context);

        if (context instanceof StrategyContext strategyContext) {
            FlowAgentMessage flowToolMessage = FlowAgentMessage.builder()
                    .agentType(AgentTypeEnum.TOOL)
                    .agentName(getName())
                    .output(output)
                    .timestamp(System.currentTimeMillis())
                    .build();
            strategyContext.addChatMessage(flowToolMessage);
        }

        return new AgentResult(output, null);
    }

    protected String callTool(String requestJson, AgentContext context) throws BizException {
        String endpoint = toolConfig.getEndpoint();
        if (StringUtils.isBlank(endpoint)) {
            throw BizErrorFactory.getInstance().toolEndpointMissing(getName());
        }

        log.info("[ToolAgent: {}] 正在向真实外部端点发起调用: endpoint={}, requestBody={}", getName(), endpoint, requestJson);

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/json; charset=UTF-8")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestJson != null ? requestJson : "{}", StandardCharsets.UTF_8))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("[ToolAgent: {}] 接口调用非成功状态码: code={}, body={}", getName(), response.statusCode(), response.body());
                throw BizErrorFactory.getInstance().toolExecutionError(getName(), "HTTP " + response.statusCode() + ": " + response.body());
            }
            log.info("[ToolAgent: {}] 真实接口调用成功，响应大小: {} 字符", getName(), response.body() != null ? response.body().length() : 0);
            return response.body();
        } catch (BizException e) {
            throw e;
        } catch (IOException e) {
            log.error("[ToolAgent: {}] 工具 HTTP 网络 IO 异常: endpoint={}, error={}", getName(), endpoint, e.getMessage());
            throw BizErrorFactory.getInstance().toolExecutionError(getName(), "网络连接异常: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw BizErrorFactory.getInstance().toolExecutionError(getName(), "调用被中断");
        } catch (Exception e) {
            log.error("[ToolAgent: {}] 工具执行未知异常: endpoint={}, error={}", getName(), endpoint, e.getMessage());
            throw BizErrorFactory.getInstance().toolExecutionError(getName(), "未知异常: " + e.getMessage());
        }
    }

    public ToolConfigDO getToolConfig() {
        return toolConfig;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <PO extends AgentPO> PO getPO() {
        return (PO) AgentPO.builder()
                .name(getName())
                .description(getDescription())
                .agentType(AgentTypeEnum.TOOL.getCode())
                .build();
    }
}
