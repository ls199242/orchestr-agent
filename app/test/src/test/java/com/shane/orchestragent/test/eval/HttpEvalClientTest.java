package com.shane.orchestragent.test.eval;

import com.shane.orchestragent.biz.eval.client.impl.HttpEvalClient;
import com.shane.orchestragent.biz.eval.model.CaseMockBundleDTO;
import com.shane.orchestragent.biz.eval.model.ExecutionTraceDTO;
import com.shane.orchestragent.common.exception.BizException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * HttpEvalClient 端点请求与契约解析单元测试
 * 基于轻量 JDK 内置 HttpServer 进行 HTTP 请求报文与响应序列化断言
 *
 * @author Shane
 */
public class HttpEvalClientTest {

    private HttpServer server;
    private int port;
    private HttpEvalClient client;

    @BeforeEach
    public void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        port = server.getAddress().getPort();
        server.start();

        client = new HttpEvalClient();
        client.setEvalBaseUrl("http://127.0.0.1:" + port);
    }

    @AfterEach
    public void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("测试调用 POST /api/eval/traces 成功持久化 Trace")
    public void testRecordTraceSuccess() throws BizException {
        AtomicReference<String> receivedPath = new AtomicReference<>();
        AtomicReference<String> receivedBody = new AtomicReference<>();

        server.createContext("/api/eval/traces", exchange -> {
            receivedPath.set(exchange.getRequestURI().getPath());
            byte[] bodyBytes = exchange.getRequestBody().readAllBytes();
            receivedBody.set(new String(bodyBytes, StandardCharsets.UTF_8));

            String resp = "{\"code\":200,\"message\":\"success\",\"data\":{\"traceId\":\"t_test_123\",\"saved\":true,\"nodesCount\":1}}";
            byte[] respBytes = resp.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, respBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(respBytes);
            }
        });

        ExecutionTraceDTO trace = ExecutionTraceDTO.builder()
                .traceId("t_test_123")
                .caseId("flight_001")
                .runId("run_1")
                .env("test")
                .source("EVAL")
                .build();

        Map<String, Object> result = client.recordTrace(trace);

        Assertions.assertNotNull(result);
        Assertions.assertEquals("t_test_123", result.get("traceId"));
        Assertions.assertEquals(true, result.get("saved"));
        Assertions.assertEquals("/api/eval/traces", receivedPath.get());
        Assertions.assertTrue(receivedBody.get().contains("t_test_123"));
        Assertions.assertTrue(receivedBody.get().contains("flight_001"));
    }

    @Test
    @DisplayName("测试外部 eval 服务返回 500 时正确抛出 BizException")
    public void testRecordTraceServerError() {
        server.createContext("/api/eval/traces", exchange -> {
            String err = "{\"code\":500,\"message\":\"数据库插入失败\"}";
            byte[] bytes = err.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(500, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });

        ExecutionTraceDTO trace = ExecutionTraceDTO.builder().traceId("t_err_500").build();
        BizException ex = Assertions.assertThrows(BizException.class, () -> client.recordTrace(trace));
        Assertions.assertEquals("EVAL_SERVER_ERROR", ex.getErrorCode());
    }

    @Test
    @DisplayName("测试空 Trace 或空 traceId 保护")
    public void testRecordTraceValidation() {
        Assertions.assertThrows(BizException.class, () -> client.recordTrace(null));
        Assertions.assertThrows(BizException.class, () -> client.recordTrace(new ExecutionTraceDTO()));
    }

    @Test
    @DisplayName("测试拉取用例包 GET /api/eval/cases/{caseId}/bundle 正常返回")
    public void testGetCaseBundleSuccess() throws BizException {
        server.createContext("/api/eval/cases/flight_001/bundle", exchange -> {
            String resp = "{\"code\":200,\"data\":{\"caseId\":\"flight_001\",\"options\":{\"strictMock\":true}}}";
            byte[] bytes = resp.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });

        CaseMockBundleDTO bundle = client.getCaseBundle("flight_001", "run_10", 1);
        Assertions.assertNotNull(bundle);
        Assertions.assertEquals("flight_001", bundle.getCaseId());
        Assertions.assertTrue(bundle.getOptions().isStrictMock());
    }
}
