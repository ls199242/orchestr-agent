package com.shane.orchestragent.memory.session;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 多轮会话待定策略状态存储 (基于 Caffeine 本地缓存，自带 TTL 与容量保护)
 * 按 sessionId 维护跨轮参数收集状态，淘汰策略与 {@code CaffeineMemoryContext} 对齐
 *
 * @author Shane
 */
@Component
public class SessionStateStore {

    private final Cache<String, SessionPendingState> pendingStore = Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofHours(2))
            .maximumSize(50000)
            .build();

    /**
     * 获取会话待定策略状态
     *
     * @param sessionId 会话标识
     * @return 待定状态 (不存在时返回 null)
     */
    public SessionPendingState get(String sessionId) {
        if (StringUtils.isEmpty(sessionId)) {
            return null;
        }
        return pendingStore.getIfPresent(sessionId);
    }

    /**
     * 保存或覆盖会话待定策略状态
     *
     * @param sessionId 会话标识
     * @param state 待定状态
     */
    public void save(String sessionId, SessionPendingState state) {
        if (StringUtils.isEmpty(sessionId) || state == null) {
            return;
        }
        state.setUpdatedAt(System.currentTimeMillis());
        pendingStore.put(sessionId, state);
    }

    /**
     * 清除会话待定策略状态 (策略成功触发执行后调用)
     *
     * @param sessionId 会话标识
     */
    public void clear(String sessionId) {
        if (StringUtils.isNotEmpty(sessionId)) {
            pendingStore.invalidate(sessionId);
        }
    }
}
