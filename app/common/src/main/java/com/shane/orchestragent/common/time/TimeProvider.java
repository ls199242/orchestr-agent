package com.shane.orchestragent.common.time;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 统一时间提供者接口
 * 支持生产环境真实系统时钟与评测环境固定冻结时钟注入
 *
 * @author Shane
 */
public interface TimeProvider {

    /**
     * 获取当前时间戳（毫秒）
     *
     * @return 当前时间毫秒数
     */
    long currentTimeMillis();

    /**
     * 获取当前 Instant 对象
     *
     * @return 当前 Instant
     */
    default Instant currentInstant() {
        return Instant.ofEpochMilli(currentTimeMillis());
    }

    /**
     * 获取当前 ISO-8601 格式化时间字符串
     *
     * @return ISO-8601 时间串，如 2026-11-07T00:00:00Z
     */
    default String currentIsoString() {
        return DateTimeFormatter.ISO_INSTANT.format(currentInstant());
    }

    /**
     * 格式化输出指定时区的时间字符串
     *
     * @param pattern 格式模板，如 yyyy-MM-dd
     * @param zoneId  时区
     * @return 格式化后的时间串
     */
    default String format(String pattern, ZoneId zoneId) {
        return DateTimeFormatter.ofPattern(pattern).withZone(zoneId != null ? zoneId : ZoneId.systemDefault()).format(currentInstant());
    }

    /**
     * 获取系统真实时间提供者
     *
     * @return 真实系统时钟
     */
    static TimeProvider system() {
        return SystemTimeProvider.INSTANCE;
    }

    /**
     * 创建固定冻结时间提供者
     *
     * @param fixedEpochMillis 固定时间戳毫秒数
     * @return 冻结时间提供者
     */
    static TimeProvider fixed(long fixedEpochMillis) {
        return new FixedTimeProvider(fixedEpochMillis);
    }

    /**
     * 解析 ISO 时间字符串并创建固定时间提供者
     *
     * @param isoString 如 "2026-11-07" 或 "2026-11-07T00:00:00Z"
     * @return 冻结时间提供者
     */
    static TimeProvider fixed(String isoString) {
        return new FixedTimeProvider(isoString);
    }
}
