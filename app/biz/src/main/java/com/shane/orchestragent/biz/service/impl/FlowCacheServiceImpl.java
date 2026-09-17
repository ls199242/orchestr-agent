package com.shane.orchestragent.biz.service.impl;

import com.shane.orchestragent.biz.model.request.RecommendRequestVO;
import com.shane.orchestragent.biz.service.FlowCacheService;
import com.shane.orchestragent.memory.context.MemoryContext;
import com.shane.orchestragent.repository.model.StrategyConfigDO;
import org.springframework.stereotype.Service;

/**
 * 流程结果缓存服务实现
 *
 * @author Shane
 */
@Service
public class FlowCacheServiceImpl implements FlowCacheService {

    private final MemoryContext memoryContext;

    public FlowCacheServiceImpl() {
        this(new com.shane.orchestragent.memory.context.impl.CaffeineMemoryContext());
    }

    public FlowCacheServiceImpl(MemoryContext memoryContext) {
        this.memoryContext = memoryContext;
    }

    @Override
    public MemoryContext createMemoryContext(RecommendRequestVO request, StrategyConfigDO strategyConfig) {
        return memoryContext;
    }
}
