package com.shane.orchestragent.biz.service;

import com.shane.orchestragent.biz.model.request.RecommendRequestVO;
import com.shane.orchestragent.memory.context.MemoryContext;
import com.shane.orchestragent.repository.model.StrategyConfigDO;

/**
 * 流程结果缓存服务
 *
 * @author Shane
 */
public interface FlowCacheService {

    MemoryContext createMemoryContext(RecommendRequestVO request, StrategyConfigDO strategyConfig);
}
