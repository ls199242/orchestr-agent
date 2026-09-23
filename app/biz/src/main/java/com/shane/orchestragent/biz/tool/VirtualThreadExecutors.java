package com.shane.orchestragent.biz.tool;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 虚拟线程执行器 (基于 JDK 21 虚拟线程原生特性)
 *
 * @author Shane
 */
public class VirtualThreadExecutors {

    private static final ExecutorService EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(EXECUTOR::shutdown));
    }

    public static ExecutorService get() {
        return EXECUTOR;
    }
}
