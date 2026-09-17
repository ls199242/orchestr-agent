package com.shane.orchestragent.web.controller;

import com.shane.orchestragent.common.annotation.Api;
import com.shane.orchestragent.common.enums.LogModuleEnum;
import com.shane.orchestragent.common.model.BaseResult;
import com.shane.orchestragent.web.service.LogStreamService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 实时日志流式推送与管理控制器
 * 提供基于 SSE 的日志实时流式监听接口、最近历史日志查询以及日志文件清理功能
 *
 * @author Shane
 */
@RestController
@RequestMapping("/api/agent/logs")
public class LogStreamController {

    private final LogStreamService logStreamService;

    public LogStreamController(LogStreamService logStreamService) {
        this.logStreamService = logStreamService;
    }

    /**
     * 1. 实时流式读取系统日志 (SSE)
     *
     * @param tail 初始读取最近日志行数，默认 100 行
     * @param filter 可选，过滤关键词
     * @return SseEmitter 持续推送日志事件
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Api(logModule = LogModuleEnum.API_LOG_STREAM, desc = "实时日志流式监听", recordResponse = false)
    public SseEmitter streamLogs(@RequestParam(value = "tail", defaultValue = "100") int tail,
                                 @RequestParam(value = "filter", required = false) String filter) {
        return logStreamService.streamLogs(tail, filter);
    }

    /**
     * 2. 获取最近历史日志行列表
     *
     * @param tail 最近行数，默认 100 行
     * @return 历史日志行列表
     */
    @GetMapping("/recent")
    public BaseResult<List<String>> getRecentLogs(@RequestParam(value = "tail", defaultValue = "100") int tail) {
        return BaseResult.ok(logStreamService.getRecentLogs(tail));
    }

    /**
     * 3. 获取日志文件基本元信息
     */
    @GetMapping("/info")
    public BaseResult<Map<String, Object>> getLogInfo() {
        File file = logStreamService.getActiveLogFile();
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("path", file.getAbsolutePath());
        info.put("exists", file.exists());
        info.put("sizeBytes", file.exists() ? file.length() : 0);
        return BaseResult.ok(info);
    }

    /**
     * 4. 清空日志文件内容
     */
    @PostMapping("/clear")
    @Api(logModule = LogModuleEnum.API_LOG_STREAM, desc = "清空日志文件")
    public BaseResult<String> clearLogs() {
        boolean success = logStreamService.clearLogFile();
        if (success) {
            return BaseResult.ok("日志文件已成功清空");
        } else {
            return BaseResult.fail("CLEAR_ERROR", "清空日志文件失败或日志文件不存在");
        }
    }
}
