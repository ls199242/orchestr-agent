package com.shane.orchestragent.web.service;

import com.shane.orchestragent.repository.DictRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 日志文件流式读取服务
 * 负责定位系统持久化日志文件，实现首屏历史尾部读取 (Tail) 与增量实时监听推送 (SSE)
 * 支持通过 DictRepository 动态调节初始行数与文件增量轮询间隔
 *
 * @author Shane
 */
@Service
@Slf4j
public class LogStreamService {

    @Autowired(required = false)
    private DictRepository dictRepository;

    @Value("${logging.file.name:logs/orchestr-agent.log}")
    private String logFilePathConfig;

    /**
     * 获取当前生效的日志文件路径，自动兼容不同启动工作目录
     */
    public File getActiveLogFile() {
        // 1. 优先尝试配置的原始路径
        Path path1 = Paths.get(logFilePathConfig);
        if (Files.exists(path1)) {
            return path1.toFile();
        }

        // 2. 尝试项目根目录相对路径 logs/orchestr-agent.log
        Path path2 = Paths.get("logs", "orchestr-agent.log");
        if (Files.exists(path2)) {
            return path2.toFile();
        }

        // 3. 尝试 app/web/logs/orchestr-agent.log
        Path path3 = Paths.get("app", "web", "logs", "orchestr-agent.log");
        if (Files.exists(path3)) {
            return path3.toFile();
        }

        // 4. 若文件尚不存在，尝试创建父目录并返回默认路径文件对象
        File targetFile = path1.toFile();
        if (targetFile.getParentFile() != null && !targetFile.getParentFile().exists()) {
            targetFile.getParentFile().mkdirs();
        }
        return targetFile;
    }

    /**
     * 读取指定行数的最近历史日志
     *
     * @param maxLines 最大读取行数
     * @return 历史日志行列表
     */
    public List<String> getRecentLogs(int maxLines) {
        File file = getActiveLogFile();
        if (!file.exists() || file.length() == 0) {
            return Collections.emptyList();
        }

        List<String> lines = new ArrayList<>();
        // 从文件尾部读取最后 256KB 内容以提取最后 maxLines 行
        long fileLen = file.length();
        long readBytes = Math.min(fileLen, 256L * 1024L);
        long seekPosition = fileLen - readBytes;

        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            raf.seek(seekPosition);
            byte[] bytes = new byte[(int) readBytes];
            raf.readFully(bytes);

            String content = new String(bytes, StandardCharsets.UTF_8);
            String[] allLines = content.split("\r?\n");

            // 如果不是从文件首字节读取，第一行可能不完整，丢弃
            int startIdx = (seekPosition > 0 && allLines.length > 1) ? 1 : 0;
            int total = allLines.length;
            int takeCount = Math.min(total - startIdx, maxLines);
            int begin = total - takeCount;

            for (int i = begin; i < total; i++) {
                if (i >= startIdx) {
                    lines.add(allLines[i]);
                }
            }
        } catch (Exception e) {
            log.warn("[LogStreamService] 读取历史日志发生异常: {}", e.getMessage());
        }

        return lines;
    }

    /**
     * 创建并启动日志流式监听 SSE 发射客户端
     *
     * @param tailLines 初始历史行数
     * @param filterKeyword 过滤关键词
     * @return SseEmitter 实例
     */
    public SseEmitter streamLogs(int tailLines, String filterKeyword) {
        // 设置无超时长连接
        SseEmitter emitter = new SseEmitter(0L);
        AtomicBoolean isRunning = new AtomicBoolean(true);

        emitter.onCompletion(() -> isRunning.set(false));
        emitter.onTimeout(() -> isRunning.set(false));
        emitter.onError(e -> isRunning.set(false));

        // 启动后台线程持续 tail 文件增量推送
        Thread tailerThread = new Thread(() -> {
            File logFile = getActiveLogFile();
            RandomAccessFile raf = null;
            try {
                // 1. 发送初始欢迎事件与历史记录
                emitter.send(SseEmitter.event().name("init").data("日志流建立成功: " + logFile.getAbsolutePath()));

                int defaultMaxLines = dictRepository != null ? dictRepository.getValue(DictRepository.Keys.KEY_LOG_STREAM_MAX_LINES, DictRepository.Defaults.DEFAULT_LOG_STREAM_MAX_LINES) : 1000;
                int actualTail = tailLines > 0 ? tailLines : defaultMaxLines;
                List<String> recentLogs = getRecentLogs(actualTail);
                for (String line : recentLogs) {
                    if (matchFilter(line, filterKeyword)) {
                        emitter.send(SseEmitter.event().name("log").data(line));
                    }
                }

                // 2. 准备开始实时追随文件
                if (logFile.exists()) {
                    raf = new RandomAccessFile(logFile, "r");
                    // 定位至当前文件末尾
                    raf.seek(logFile.length());
                }

                ByteArrayOutputStream lineBuffer = new ByteArrayOutputStream();
                byte[] readBuf = new byte[4096];
                long lastPingTime = System.currentTimeMillis();

                while (isRunning.get()) {
                    if (logFile.exists()) {
                        if (raf == null) {
                            raf = new RandomAccessFile(logFile, "r");
                        }

                        long fileLen = logFile.length();
                        long curPos = raf.getFilePointer();

                        // 检测日志被外部轮转清空或截断
                        if (fileLen < curPos) {
                            raf.seek(0);
                            curPos = 0;
                        }

                        if (fileLen > curPos) {
                            int n = raf.read(readBuf);
                            if (n > 0) {
                                for (int i = 0; i < n; i++) {
                                    byte b = readBuf[i];
                                    if (b == '\n') {
                                        String rawLine = lineBuffer.toString(StandardCharsets.UTF_8);
                                        lineBuffer.reset();
                                        if (rawLine.endsWith("\r")) {
                                            rawLine = rawLine.substring(0, rawLine.length() - 1);
                                        }
                                        if (matchFilter(rawLine, filterKeyword)) {
                                            emitter.send(SseEmitter.event().name("log").data(rawLine));
                                        }
                                    } else {
                                        lineBuffer.write(b);
                                    }
                                }
                            }
                        }
                    }

                    // 每 15 秒发送一次心跳保活
                    if (System.currentTimeMillis() - lastPingTime > 15000) {
                        emitter.send(SseEmitter.event().name("ping").data("heartbeat"));
                        lastPingTime = System.currentTimeMillis();
                    }

                    // 稍作休眠等待新日志写入，轮询间隔受 DictRepository 动态控制
                    int pollMs = dictRepository != null ? dictRepository.getValue(DictRepository.Keys.KEY_LOG_STREAM_POLL_INTERVAL_MS, DictRepository.Defaults.DEFAULT_LOG_STREAM_POLL_INTERVAL_MS) : 200;
                    Thread.sleep(Math.max(50, pollMs));
                }
            } catch (Exception e) {
                // 客户端断开等常规网络异常
                log.debug("[LogStreamService] 日志流连接结束: {}", e.getMessage());
            } finally {
                if (raf != null) {
                    try {
                        raf.close();
                    } catch (IOException ignored) {
                    }
                }
                emitter.complete();
            }
        });
        tailerThread.setName("log-tailer-sse");
        tailerThread.setDaemon(true);
        tailerThread.start();

        return emitter;
    }

    /**
     * 清空当前日志文件内容
     */
    public boolean clearLogFile() {
        File file = getActiveLogFile();
        if (file.exists()) {
            try (FileOutputStream fos = new FileOutputStream(file, false)) {
                fos.write(new byte[0]);
                fos.flush();
                return true;
            } catch (Exception e) {
                log.error("[LogStreamService] 清空日志文件失败", e);
                return false;
            }
        }
        return false;
    }

    private boolean matchFilter(String line, String keyword) {
        if (StringUtils.isBlank(keyword)) {
            return true;
        }
        return line != null && line.toLowerCase().contains(keyword.toLowerCase());
    }
}
