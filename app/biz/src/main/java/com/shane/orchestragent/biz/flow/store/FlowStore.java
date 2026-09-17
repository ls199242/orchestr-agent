package com.shane.orchestragent.biz.flow.store;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.shane.orchestragent.biz.flow.StrategyFlow;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

/**
 * 策略流实例缓存管理 (采用 Caffeine 实现 TTL 与最大容量淘汰保护，彻底修复原代码无界静态 Map 导致的 OOM 内存泄漏)
 *
 * @author Shane
 */
public final class FlowStore {

    private static final Logger log = LoggerFactory.getLogger(FlowStore.class);

    private static volatile Cache<String, StrategyFlow> flowCache = createCache(30, 10000);

    private static Cache<String, StrategyFlow> createCache(int expireMinutes, int maxSize) {
        return Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(Math.max(1, expireMinutes)))
                .maximumSize(Math.max(10, maxSize))
                .removalListener((key, value, cause) -> {
                    log.info("[FlowStore] 流程实例淘汰清理: flowId={}, 原因: {}", key, cause);
                })
                .build();
    }

    public static synchronized void configure(int expireMinutes, int maxSize) {
        Cache<String, StrategyFlow> oldCache = flowCache;
        Cache<String, StrategyFlow> newCache = createCache(expireMinutes, maxSize);
        if (oldCache != null) {
            newCache.putAll(oldCache.asMap());
        }
        flowCache = newCache;
        log.info("[FlowStore] 动态更新流程实例缓存策略: expireMinutes={}, maxSize={}", expireMinutes, maxSize);
    }

    private FlowStore() {
    }

    public static void add(String flowId, StrategyFlow flow) {
        if (StringUtils.isNotEmpty(flowId) && flow != null) {
            flowCache.put(flowId, flow);
        }
    }

    public static StrategyFlow get(String flowId) {
        return StringUtils.isNotEmpty(flowId) ? flowCache.getIfPresent(flowId) : null;
    }

    public static void remove(String flowId) {
        if (StringUtils.isNotEmpty(flowId)) {
            flowCache.invalidate(flowId);
        }
    }

    public static long size() {
        return flowCache.estimatedSize();
    }
}
