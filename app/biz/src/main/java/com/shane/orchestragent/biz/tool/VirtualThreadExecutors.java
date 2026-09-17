package com.shane.orchestragent.biz.tool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 线程执行器 (优先适配 JDK 21 虚拟线程特性，JDK 17 环境平滑降级为高效线程池)
 *
 * @author Shane
 */
public class VirtualThreadExecutors {

    private static final Logger log = LoggerFactory.getLogger(VirtualThreadExecutors.class);
    private static final ExecutorService EXECUTOR = createExecutor();

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(EXECUTOR::shutdown));
    }

    private static ExecutorService createExecutor() {
        try {
            Method method = Executors.class.getMethod("newVirtualThreadPerTaskExecutor");
            return (ExecutorService) method.invoke(null);
        } catch (Exception e) {
            log.info("Virtual threads not available in current JDK runtime, falling back to cached thread pool");
            return Executors.newCachedThreadPool();
        }
    }

    public static ExecutorService get() {
        return EXECUTOR;
    }
}
