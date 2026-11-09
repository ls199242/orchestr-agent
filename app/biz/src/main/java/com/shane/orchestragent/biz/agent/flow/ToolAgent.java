package com.shane.orchestragent.biz.agent.flow;

import com.shane.orchestragent.biz.agent.base.BaseAgent;
import com.shane.orchestragent.biz.context.AgentContext;
import com.shane.orchestragent.biz.context.StrategyContext;
import com.shane.orchestragent.biz.eval.context.EvalCaseContext;
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
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(15))
                .build());
    }

    public ToolAgent(ToolConfigDO toolConfig, HttpClient httpClient) {
        super(Objects.requireNonNull(toolConfig, "toolConfig 不能为空").getCode(),
                toolConfig.getDescription(),
                AgentTypeEnum.TOOL);
        if (StringUtils.isBlank(toolConfig.getCode())) {
            throw new IllegalArgumentException("toolConfig.code 不能为空");
        }
        this.toolConfig = toolConfig;
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient 不能为空");
    }

    @Override
    protected AgentResult doExecute(AgentContext context) throws BizException {

        Object request = context.getAgentRequest();
        if (request == null && context instanceof StrategyContext strategyContext) {
            request = strategyContext.getNextAgentRequest();
        }
        String requestJson;
        if (request == null) {
            requestJson = "{}";
        } else if (request instanceof String str) {
            String trimmed = str.trim();
            if (trimmed.isEmpty() || "null".equalsIgnoreCase(trimmed)) {
                requestJson = "{}";
            } else {
                requestJson = trimmed;
            }
        } else {
            String json = JsonUtils.toJsonString(request);
            requestJson = (StringUtils.isBlank(json) || "null".equalsIgnoreCase(json.trim())) ? "{}" : json;
        }

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

    /**
     * 发起工具调用
     * <p>
     * 区分评测模式（走 MockEngine 与故障注入）与生产模式（发起真实外部 HTTP 请求）。
     *
     * @param requestJson 请求 JSON 字符串
     * @param context     执行上下文
     * @return 工具执行产出的原始报文字符串
     * @throws BizException 若调用失败、超时或 Mock 匹配缺失
     */
    protected String callTool(String requestJson, AgentContext context) throws BizException {
        if (context instanceof StrategyContext sc && sc.getEvalCaseContext() != null) {
            return executeWithEvalMock(requestJson, sc);
        }
        return executeRealHttpRequest(requestJson, context);
    }

    /**
     * 评测模式下的 Mock 工具执行逻辑
     */
    private String executeWithEvalMock(String requestJson, StrategyContext sc) throws BizException {
        EvalCaseContext evalCtx = sc.getEvalCaseContext();
        String toolCode = getName();

        // 1. 故障注入评估 (超时/报错/慢响应)
        evalCtx.getFaultInjector().checkAndTriggerFault(toolCode);

        // 2. Mock 数据匹配与全量 Trace 记录 (耗时度量使用真实流逝时间)
        long startTs = System.currentTimeMillis();
        try {
            String mockResult = evalCtx.getMockEngine().matchToolMock(toolCode, requestJson);
            long latency = Math.max(0, System.currentTimeMillis() - startTs);
            evalCtx.getTraceCollector().recordToolCall(toolCode, parseArgsMap(requestJson), mockResult, null, latency);
            log.info("[Flow: {}][ToolAgent: {}] 评测 Mock 命中，返回响应 (耗时: {}ms)", sc.getFlowId(), toolCode, latency);
            return mockResult;
        } catch (BizException e) {
            long latency = Math.max(0, System.currentTimeMillis() - startTs);
            evalCtx.getTraceCollector().recordToolCall(toolCode, parseArgsMap(requestJson), null, e.getMessage(), latency);
            throw e;
        }
    }

    /**
     * 真实生产环境下的外部 HTTP 接口调用
     */
    private String executeRealHttpRequest(String requestJson, AgentContext context) throws BizException {
        String endpoint = toolConfig.getEndpoint();
        if (StringUtils.isBlank(endpoint)) {
            throw BizErrorFactory.getInstance().toolEndpointMissing(getName());
        }

        String flowId = (context instanceof StrategyContext sc) ? sc.getFlowId() : "N/A";
        log.info("[Flow: {}][ToolAgent: {}] 正在向真实外部端点发起调用: endpoint={}, requestBody={}", flowId, getName(), endpoint, requestJson);

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .version(HttpClient.Version.HTTP_1_1)
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/json; charset=UTF-8")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestJson != null ? requestJson : "{}", StandardCharsets.UTF_8))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("[Flow: {}][ToolAgent: {}] 接口调用非成功状态码: code={}, body={}", flowId, getName(), response.statusCode(), response.body());
                throw BizErrorFactory.getInstance().toolExecutionError(getName(), "HTTP " + response.statusCode() + ": " + response.body());
            }
            log.info("[Flow: {}][ToolAgent: {}] 真实接口调用成功，响应内容: {}", flowId, getName(), response.body());
            return response.body();
        } catch (BizException e) {
            throw e;
        } catch (IOException e) {
            log.error("[Flow: {}][ToolAgent: {}] 工具 HTTP 网络 IO 异常: endpoint={}, error={}", flowId, getName(), endpoint, e.getMessage());
            throw BizErrorFactory.getInstance().toolExecutionError(getName(), "网络连接异常: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw BizErrorFactory.getInstance().toolExecutionError(getName(), "调用被中断");
        } catch (Exception e) {
            log.error("[Flow: {}][ToolAgent: {}] 工具执行未知异常: endpoint={}, error={}", flowId, getName(), endpoint, e.getMessage());
            throw BizErrorFactory.getInstance().toolExecutionError(getName(), "未知异常: " + e.getMessage());
        }
    }

    /**
     * 将请求 JSON 字符串安全解析为 Map 结构，供 Trace 记录使用
     *
     * @param json 入参 JSON 字符串
     * @return 解析后的 Map，若解析失败或为空则返回空 Map
     */
    private java.util.Map<String, Object> parseArgsMap(String json) {
        if (StringUtils.isBlank(json)) {
            return java.util.Collections.emptyMap();
        }
        try {
            java.util.Map<String, Object> map = JsonUtils.parseMap(json);
            return map != null ? map : java.util.Collections.emptyMap();
        } catch (Exception e) {
            return java.util.Collections.emptyMap();
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
