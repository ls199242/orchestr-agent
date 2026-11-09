package com.shane.orchestragent.biz.eval.client.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.shane.orchestragent.biz.eval.client.EvalClient;
import com.shane.orchestragent.biz.eval.model.CaseMockBundleDTO;
import com.shane.orchestragent.biz.eval.model.ExecutionTraceDTO;
import com.shane.orchestragent.common.exception.BizException;
import com.shane.orchestragent.common.model.BaseResult;
import com.shane.orchestragent.common.utils.JsonUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;

/**
 * 基于 HTTP 协议的 Eval 客户端标准实现
 * <p>
 * 职责：
 * <ul>
 *   <li>向外部 eval 系统发起 `GET /api/eval/cases/{caseId}/bundle` 请求</li>
 *   <li>透传 `X-Run-Id` 和 `X-Repeat-No` 请求头</li>
 *   <li>解析服务端响应（支持裸数据或 BaseResult 统一包装格式）</li>
 *   <li>处理网络超时、连接异常与 404 Case 未找到等场景并抛出规范的 {@link BizException}</li>
 * </ul>
 *
 * @author Shane
 */
@Component
public class HttpEvalClient implements EvalClient {

    private static final Logger log = LoggerFactory.getLogger(HttpEvalClient.class);

    private final HttpClient httpClient;

    /** eval 系统服务端基础端点地址 (如 "http://127.0.0.1:8088") */
    @Value("${orchestragent.eval.base-url:http://127.0.0.1:8088}")
    private String evalBaseUrl;

    /**
     * 默认构造器，初始化轻量级 HTTP 客户端
     */
    public HttpEvalClient() {
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * 允许外部配置注入或单测修改 baseUrl
     *
     * @param evalBaseUrl 服务端地址
     */
    public void setEvalBaseUrl(String evalBaseUrl) {
        this.evalBaseUrl = evalBaseUrl;
    }

    /**
     * 根据用例标识远程拉取运行包
     *
     * @param caseId   用例标识 (如 "flight_001")
     * @param runId    批次运行 ID
     * @param repeatNo 采样执行序号
     * @return 运行包数据对象
     * @throws BizException 若请求失败或解析异常
     */
    @Override
    public CaseMockBundleDTO getCaseBundle(String caseId, String runId, Integer repeatNo) throws BizException {
        if (StringUtils.isBlank(caseId)) {
            throw new BizException("CASE_ID_EMPTY", "用例标识 caseId 不能为空");
        }

        String url = StringUtils.stripEnd(evalBaseUrl, "/") + "/api/eval/cases/" + caseId + "/bundle";
        log.info("[EvalClient] 正在向 eval 系统拉取用例运行包: url={}, caseId={}", url, caseId);

        HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("Accept", "application/json")
                .GET();

        if (StringUtils.isNotBlank(runId)) {
            reqBuilder.header("X-Run-Id", runId);
        }
        if (repeatNo != null) {
            reqBuilder.header("X-Repeat-No", String.valueOf(repeatNo));
        }

        try {
            HttpResponse<String> response = httpClient.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 404) {
                log.error("[EvalClient] 用例未在 eval 系统中找到: caseId={}", caseId);
                throw new BizException("CASE_NOT_FOUND", "eval 系统中未找到用例: " + caseId);
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.error("[EvalClient] eval 系统返回异常 HTTP {}: {}", response.statusCode(), response.body());
                throw new BizException("EVAL_SERVER_ERROR", "eval 服务响应异常 HTTP " + response.statusCode() + ": " + response.body());
            }

            String body = response.body();
            // 兼容支持直接返回 CaseMockBundleDTO 或被 BaseResult<CaseMockBundleDTO> 包装的返回格式
            CaseMockBundleDTO bundle = parseBundle(body);
            if (bundle == null) {
                throw new BizException("EVAL_BUNDLE_EMPTY", "从 eval 系统解析到的 CaseMockBundle 为空");
            }

            log.info("[EvalClient] 成功获取用例 [{}] 运行包, toolsCount={}, ragCount={}, faultsCount={}",
                    caseId,
                    bundle.getToolMocks() != null ? bundle.getToolMocks().size() : 0,
                    bundle.getRagMocks() != null ? bundle.getRagMocks().size() : 0,
                    bundle.getFaultInjections() != null ? bundle.getFaultInjections().size() : 0);

            return bundle;
        } catch (BizException be) {
            throw be;
        } catch (Exception e) {
            log.error("[EvalClient] 连接 eval 系统失败: url={}, error={}", url, e.getMessage());
            throw new BizException("EVAL_NETWORK_ERROR", "无法连接 eval 系统 (" + url + "): " + e.getMessage(), e);
        }
    }

    /**
     * 兼容性反序列化响应报文
     *
     * @param body HTTP 响应报文内容
     * @return 解析完成的 Bundle 对象
     */
    private CaseMockBundleDTO parseBundle(String body) {
        if (StringUtils.isBlank(body)) {
            return null;
        }
        try {
            // 尝试以 BaseResult<T> 包装结构进行解析
            BaseResult<CaseMockBundleDTO> baseResult = JsonUtils.parseObject(body, new TypeReference<BaseResult<CaseMockBundleDTO>>() {});
            if (baseResult != null && baseResult.getData() != null) {
                return baseResult.getData();
            }
        } catch (Exception ignored) {
        }

        // 尝试以直接实体 JSON 解析
        return JsonUtils.parseObject(body, CaseMockBundleDTO.class);
    }

    /**
     * 向 eval 评测服务上报并持久化链路 Trace 数据
     *
     * @param trace 汇编完成的完整结构化 Trace 载荷
     * @return 评估服务端存储确认结果字典
     * @throws BizException 若请求失败或服务端返回异常
     */
    @Override
    public Map<String, Object> recordTrace(ExecutionTraceDTO trace) throws BizException {
        if (trace == null || StringUtils.isBlank(trace.getTraceId())) {
            throw new BizException("TRACE_INVALID", "待存储的 Trace 载荷或 traceId 不能为空");
        }

        String url = StringUtils.stripEnd(evalBaseUrl, "/") + "/api/eval/traces";
        log.info("[EvalClient] 正在向 eval 系统上报 Trace: url={}, traceId={}, caseId={}", url, trace.getTraceId(), trace.getCaseId());

        String jsonPayload = JsonUtils.toJsonString(trace);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.error("[EvalClient] eval 系统保存 Trace 返回异常 HTTP {}: {}", response.statusCode(), response.body());
                throw new BizException("EVAL_SERVER_ERROR", "eval 服务存储 Trace 响应异常 HTTP " + response.statusCode() + ": " + response.body());
            }

            String body = response.body();
            Map<String, Object> result = parseTraceResult(body);
            log.info("[EvalClient] 成功上报并存储 Trace: traceId={}, result={}", trace.getTraceId(), result);
            return result != null ? result : Collections.emptyMap();
        } catch (BizException be) {
            throw be;
        } catch (Exception e) {
            log.error("[EvalClient] 连接 eval 系统存储 Trace 失败: url={}, error={}", url, e.getMessage());
            throw new BizException("EVAL_NETWORK_ERROR", "无法连接 eval 系统存储 Trace (" + url + "): " + e.getMessage(), e);
        }
    }

    /**
     * 兼容性反序列化 Trace 上报响应报文
     *
     * @param body HTTP 响应报文内容
     * @return 解析完成的 Map 字典
     */
    private Map<String, Object> parseTraceResult(String body) {
        if (StringUtils.isBlank(body)) {
            return Collections.emptyMap();
        }
        try {
            BaseResult<Map<String, Object>> baseResult = JsonUtils.parseObject(body, new TypeReference<BaseResult<Map<String, Object>>>() {});
            if (baseResult != null && baseResult.getData() != null) {
                return baseResult.getData();
            }
        } catch (Exception ignored) {
        }
        try {
            Map<String, Object> map = JsonUtils.parseMap(body);
            if (map != null) {
                return map;
            }
        } catch (Exception ignored) {
        }
        return Collections.emptyMap();
    }
}
