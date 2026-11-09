package com.shane.orchestragent.common.time;

import org.apache.commons.lang3.StringUtils;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * 评测固定/冻结时间提供者
 * <p>
 * 评测时将 `now` 锁定为特定日期（如 2026-11-07），使提示词中相对时间（如“下周五”、“12月20日”）计算具有确定性答案。
 *
 * @author Shane
 */
public class FixedTimeProvider implements TimeProvider {

    private final long fixedEpochMillis;

    /**
     * 根据固定毫秒时间戳构造
     *
     * @param fixedEpochMillis 固定毫秒数
     */
    public FixedTimeProvider(long fixedEpochMillis) {
        this.fixedEpochMillis = fixedEpochMillis;
    }

    /**
     * 根据日期或 ISO 字符串构造 (支持 yyyy-MM-dd 或标准 ISO-8601)
     *
     * @param timeStr 时间字符串 (如 "2026-11-07")
     */
    public FixedTimeProvider(String timeStr) {
        if (StringUtils.isBlank(timeStr)) {
            this.fixedEpochMillis = System.currentTimeMillis();
            return;
        }
        String clean = timeStr.trim();
        long millis;
        if (clean.length() == 10 && clean.contains("-")) {
            // yyyy-MM-dd
            LocalDate localDate = LocalDate.parse(clean, DateTimeFormatter.ISO_LOCAL_DATE);
            millis = localDate.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli();
        } else {
            try {
                millis = java.time.Instant.parse(clean).toEpochMilli();
            } catch (Exception e) {
                millis = System.currentTimeMillis();
            }
        }
        this.fixedEpochMillis = millis;
    }

    @Override
    public long currentTimeMillis() {
        return fixedEpochMillis;
    }
}
