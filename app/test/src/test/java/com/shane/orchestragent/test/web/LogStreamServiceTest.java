package com.shane.orchestragent.test.web;

import com.shane.orchestragent.web.service.LogStreamService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * LogStreamService 单元测试
 *
 * @author Shane
 */
public class LogStreamServiceTest {

    @Test
    @DisplayName("测试日志文件定位、尾部历史读取、清空与流式创建")
    public void testLogServiceOperations() throws Exception {
        LogStreamService service = new LogStreamService();
        File tempFile = File.createTempFile("test-orchestr-", ".log");
        tempFile.deleteOnExit();

        ReflectionTestUtils.setField(service, "logFilePathConfig", tempFile.getAbsolutePath());

        // 1. 写入测试内容
        try (FileOutputStream fos = new FileOutputStream(tempFile)) {
            fos.write("Line 1: system start\n".getBytes(StandardCharsets.UTF_8));
            fos.write("Line 2: [CONFIG][GET] strategy loaded\n".getBytes(StandardCharsets.UTF_8));
            fos.write("Line 3: [FLOW][STEP_START] execute worker\n".getBytes(StandardCharsets.UTF_8));
            fos.write("Line 4: [API][RESPONSE] ok\n".getBytes(StandardCharsets.UTF_8));
        }

        // 2. 读取最近日志
        List<String> logs = service.getRecentLogs(2);
        Assertions.assertNotNull(logs);
        Assertions.assertEquals(2, logs.size());
        Assertions.assertTrue(logs.get(0).contains("Line 3"));
        Assertions.assertTrue(logs.get(1).contains("Line 4"));

        // 3. 测试创建流式连接
        SseEmitter emitter = service.streamLogs(10, "FLOW");
        Assertions.assertNotNull(emitter);
        emitter.complete();

        // 4. 测试清空日志文件
        boolean cleared = service.clearLogFile();
        Assertions.assertTrue(cleared);
        Assertions.assertEquals(0, tempFile.length());
    }
}
