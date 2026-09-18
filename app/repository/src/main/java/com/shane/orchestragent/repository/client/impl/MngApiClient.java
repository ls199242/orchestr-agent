package com.shane.orchestragent.repository.client.impl;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * 轻量级 HTTP 接口通信执行器
 * 基于 JDK 11+ 原生 HttpClient 实现，负责向外部配置管理中心发起 HTTP 调用
 *
 * @author Shane
 */
public class MngApiClient {

    private static final Logger log = LoggerFactory.getLogger(MngApiClient.class);

    private static final String METHOD_GET = "GET";
    private static final String METHOD_POST = "POST";

    /** 原生 HTTP 客户端实例 */
    private final HttpClient httpClient;

    /**
     * 默认构造函数，初始化具备 30 秒连接超时的原生 HTTP 客户端
     */
    public MngApiClient() {
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    /**
     * 执行 HTTP 接口调用
     *
     * @param url     目标接口完整 URL 地址
     * @param request 请求体 JSON 字符串（若为空则默认使用 GET 方法）
     * @return 响应报文原始字符串
     */
    public String callApi(String url, String request) {
        String method = StringUtils.isNotBlank(request) ? METHOD_POST : METHOD_GET;
        HttpRequest httpRequest = buildHttpRequest(url, method, request);

        try {
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("[MngApiClient] HTTP 接口调用非 200 状态: url={}, statusCode={}, body={}",
                        url, response.statusCode(), response.body());
                throw new RuntimeException("Http Response Error: code=" + response.statusCode() + ", message=" + response.body());
            }
            return response.body();
        } catch (IOException e) {
            log.warn("[MngApiClient] HTTP 网络 IO 异常: url={}, error={}", url, e.getMessage());
            throw new RuntimeException("Http IO Error: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("[MngApiClient] HTTP 调用线程中断: url={}", url);
            throw new RuntimeException("Http Interrupted", e);
        }
    }

    /**
     * 构建 HttpRequest 请求对象
     */
    private HttpRequest buildHttpRequest(String url, String method, String request) {
        HttpRequest.Builder builder = HttpRequest.newBuilder().uri(URI.create(url));
        builder.header("Accept-Charset", "utf-8");
        builder.header("Content-Type", "application/json");

        HttpRequest.BodyPublisher bodyPublisher;
        if (StringUtils.isNotBlank(request)) {
            bodyPublisher = HttpRequest.BodyPublishers.ofString(request);
        } else {
            bodyPublisher = HttpRequest.BodyPublishers.noBody();
        }
        builder.method(method, bodyPublisher);
        builder.timeout(Duration.ofSeconds(30));
        return builder.build();
    }
}
