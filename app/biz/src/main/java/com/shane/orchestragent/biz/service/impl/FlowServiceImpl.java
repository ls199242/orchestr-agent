package com.shane.orchestragent.biz.service.impl;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.shane.orchestragent.biz.model.flow.FlowProcessText;
import com.shane.orchestragent.biz.model.request.RecommendRequestVO;
import com.shane.orchestragent.biz.service.FlowService;
import com.shane.orchestragent.common.enums.FlowStateEnum;
import com.shane.orchestragent.repository.model.StrategyConfigDO;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 策略流程服务实现 (基于 Caffeine 内存 LRU 缓存)
 *
 * @author Shane
 */
@Service
public class FlowServiceImpl implements FlowService {

    private final Cache<String, FlowStateEnum> stateCache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(60))
            .maximumSize(10000)
            .build();

    private final Cache<String, String> resultCache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(60))
            .maximumSize(10000)
            .build();

    private final Cache<String, List<FlowProcessText>> processTextsCache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(60))
            .maximumSize(10000)
            .build();

    @Override
    public String createFlowId(RecommendRequestVO request, StrategyConfigDO strategyConfig) {
        if (request != null && StringUtils.isNotBlank(request.getFlowId())) {
            return request.getFlowId();
        }
        return UUID.randomUUID().toString();
    }

    @Override
    public FlowStateEnum getState(String flowId) {
        if (StringUtils.isBlank(flowId)) {
            return FlowStateEnum.NONE;
        }
        FlowStateEnum state = stateCache.getIfPresent(flowId);
        return state != null ? state : FlowStateEnum.NONE;
    }

    @Override
    public void markState(String flowId, FlowStateEnum state) {
        if (StringUtils.isNotBlank(flowId) && state != null) {
            stateCache.put(flowId, state);
        }
    }

    @Override
    public void saveProcessText(String flowId, FlowProcessText processText) {
        if (StringUtils.isNotBlank(flowId) && processText != null) {
            List<FlowProcessText> list = processTextsCache.get(flowId, k -> new CopyOnWriteArrayList<>());
            if (list != null) {
                list.add(processText);
            }
        }
    }

    @Override
    public List<FlowProcessText> getProcessTexts(String flowId) {
        if (StringUtils.isBlank(flowId)) {
            return Collections.emptyList();
        }
        List<FlowProcessText> list = processTextsCache.getIfPresent(flowId);
        return list != null ? new ArrayList<>(list) : Collections.emptyList();
    }

    @Override
    public String getResult(String flowId) {
        if (StringUtils.isBlank(flowId)) {
            return null;
        }
        return resultCache.getIfPresent(flowId);
    }

    @Override
    public void setResult(String flowId, String result) {
        if (StringUtils.isNotBlank(flowId) && result != null) {
            resultCache.put(flowId, result);
        }
    }

    @Override
    public void clear(String flowId) {
        if (StringUtils.isNotBlank(flowId)) {
            stateCache.invalidate(flowId);
            resultCache.invalidate(flowId);
            processTextsCache.invalidate(flowId);
        }
    }
}
