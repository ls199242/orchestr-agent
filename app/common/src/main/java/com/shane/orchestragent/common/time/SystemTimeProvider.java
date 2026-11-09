package com.shane.orchestragent.common.time;

/**
 * 生产默认系统真实时间提供者
 *
 * @author Shane
 */
public class SystemTimeProvider implements TimeProvider {

    public static final SystemTimeProvider INSTANCE = new SystemTimeProvider();

    @Override
    public long currentTimeMillis() {
        return System.currentTimeMillis();
    }
}
