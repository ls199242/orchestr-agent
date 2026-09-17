package com.shane.orchestragent.memory.model;

import lombok.Data;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * 记忆查询准则
 *
 * @author Shane
 */
@Data
public class MemoryQueryCriteria implements Serializable {

    public static final String KEY_SESSION_ID = "sessionId";

    private final Map<String, Object> criteria = new HashMap<>();
    private int limit = 50;

    public static MemoryQueryCriteria create() {
        return new MemoryQueryCriteria();
    }

    public MemoryQueryCriteria addCriteria(String key, Object value) {
        criteria.put(key, value);
        return this;
    }

    public MemoryQueryCriteria limit(int limit) {
        this.limit = limit;
        return this;
    }

    public String getSessionId() {
        Object val = criteria.get(KEY_SESSION_ID);
        return val != null ? val.toString() : null;
    }
}
