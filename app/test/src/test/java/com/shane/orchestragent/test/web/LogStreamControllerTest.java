package com.shane.orchestragent.test.web;

import com.shane.orchestragent.common.model.BaseResult;
import com.shane.orchestragent.web.controller.LogStreamController;
import com.shane.orchestragent.web.service.LogStreamService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * LogStreamController 控制器单元测试
 *
 * @author Shane
 */
public class LogStreamControllerTest {

    private LogStreamController controller;
    private LogStreamService service;
    private File tempFile;

    @BeforeEach
    public void setup() throws Exception {
        service = new LogStreamService();
        tempFile = File.createTempFile("test-controller-log-", ".log");
        tempFile.deleteOnExit();
        ReflectionTestUtils.setField(service, "logFilePathConfig", tempFile.getAbsolutePath());

        controller = new LogStreamController(service);
    }

    @Test
    @DisplayName("测试 LogStreamController 查询最近日志、文件元信息与清空")
    public void testControllerOperations() throws Exception {
        try (FileOutputStream fos = new FileOutputStream(tempFile)) {
            fos.write("Line 1: [API][REQUEST] invoke\n".getBytes(StandardCharsets.UTF_8));
            fos.write("Line 2: [CONFIG][GET] strategy=biz_travel\n".getBytes(StandardCharsets.UTF_8));
            fos.write("Line 3: [FLOW][STEP_START] Planner\n".getBytes(StandardCharsets.UTF_8));
        }

        // 1. 测试 recent
        BaseResult<List<String>> recent = controller.getRecentLogs(2);
        Assertions.assertTrue(recent.isSuccess());
        Assertions.assertEquals(2, recent.getData().size());

        // 2. 测试 info
        BaseResult<Map<String, Object>> info = controller.getLogInfo();
        Assertions.assertTrue(info.isSuccess());
        Assertions.assertTrue((Boolean) info.getData().get("exists"));

        // 3. 测试 clear
        BaseResult<String> clearResult = controller.clearLogs();
        Assertions.assertTrue(clearResult.isSuccess());
        Assertions.assertEquals(0, tempFile.length());
    }
}
