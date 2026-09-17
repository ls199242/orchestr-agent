package com.shane.orchestragent.memory.context;

import com.shane.orchestragent.memory.model.ConversationCacheUnit;
import com.shane.orchestragent.memory.model.MemoryQueryCriteria;

import java.util.List;

/**
 * 记忆上下文接口
 *
 * @author Shane
 */
public interface MemoryContext {

    /**
     * 查询对话单元列表
     *
     * @param criteria 查询条件
     * @return 缓存单元列表
     */
    List<ConversationCacheUnit> getCache(MemoryQueryCriteria criteria);

    /**
     * 保存对话单元
     *
     * @param unit 缓存单元
     */
    void saveCache(ConversationCacheUnit unit);

    /**
     * 清理指定 Session 的记忆
     *
     * @param sessionId 会话ID
     */
    void clear(String sessionId);
}
