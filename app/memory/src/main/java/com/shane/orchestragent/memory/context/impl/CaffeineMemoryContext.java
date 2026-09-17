package com.shane.orchestragent.memory.context.impl;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.shane.orchestragent.memory.context.MemoryContext;
import com.shane.orchestragent.memory.model.ConversationCacheUnit;
import com.shane.orchestragent.memory.model.MemoryQueryCriteria;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 基于高性能 Caffeine 本地缓存的记忆上下文实现 (自带 TTL 与容量保护，消除内存泄漏)
 *
 * @author Shane
 */
@Component
public class CaffeineMemoryContext implements MemoryContext {

    private final Cache<String, List<ConversationCacheUnit>> sessionStore = Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofHours(2))
            .maximumSize(50000)
            .build();

    @Override
    public List<ConversationCacheUnit> getCache(MemoryQueryCriteria criteria) {
        if (criteria == null) {
            return Collections.emptyList();
        }
        String sessionId = criteria.getSessionId();
        if (StringUtils.isEmpty(sessionId)) {
            return Collections.emptyList();
        }
        List<ConversationCacheUnit> list = sessionStore.getIfPresent(sessionId);
        if (list == null || list.isEmpty()) {
            return Collections.emptyList();
        }
        int limit = criteria.getLimit() > 0 ? criteria.getLimit() : 50;
        if (list.size() <= limit) {
            return new ArrayList<>(list);
        }
        return new ArrayList<>(list.subList(list.size() - limit, list.size()));
    }

    @Override
    public void saveCache(ConversationCacheUnit unit) {
        if (unit == null || StringUtils.isEmpty(unit.getSessionId())) {
            return;
        }
        sessionStore.asMap().compute(unit.getSessionId(), (k, v) -> {
            if (v == null) {
                v = new CopyOnWriteArrayList<>();
            }
            v.add(unit);
            return v;
        });
    }

    @Override
    public void clear(String sessionId) {
        if (StringUtils.isNotEmpty(sessionId)) {
            sessionStore.invalidate(sessionId);
        }
    }
}
