package com.shane.orchestragent.biz.model.response;

import com.shane.orchestragent.biz.model.enums.ResultCacheTypeEnum;
import com.shane.orchestragent.biz.model.flow.FlowProcessText;
import com.shane.orchestragent.biz.sse.SseEmitterUTF8;
import com.shane.orchestragent.common.enums.FlowStateEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 策略推荐/协同执行响应对象
 *
 * @author Shane
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommendResponseVO implements Serializable {

    /** SSE 发射器 (流式响应模式下使用) */
    private SseEmitterUTF8 sseEmitter;

    /** 流程状态 */
    private FlowStateEnum state;

    /** 流程 ID */
    private String flowId;

    /** 会话 ID */
    private String sessionId;

    /** 最终推荐/交付产物 */
    private String result;

    /** 流程过程文案明细 */
    private List<FlowProcessText> processTexts;

    /** 结果缓存命中类型 */
    private ResultCacheTypeEnum cacheType;

    /** 结果缓存键 */
    private String cacheKey;

    public static RecommendResponseVO buildResponse(String flowId, String sessionId, FlowStateEnum state,
                                                    String result, List<FlowProcessText> processTexts,
                                                    ResultCacheTypeEnum cacheType) {
        return buildResponse(flowId, sessionId, state, result, processTexts, cacheType, null);
    }

    public static RecommendResponseVO buildResponse(String flowId, String sessionId, FlowStateEnum state,
                                                    String result, List<FlowProcessText> processTexts,
                                                    ResultCacheTypeEnum cacheType, String cacheKey) {
        return RecommendResponseVO.builder()
                .flowId(flowId)
                .sessionId(sessionId)
                .state(state)
                .result(result)
                .processTexts(processTexts)
                .cacheType(cacheType)
                .cacheKey(cacheKey)
                .build();
    }
}
