package com.shane.orchestragent.biz.model.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Map;

/**
 * 策略推荐/协同执行请求对象
 *
 * @author Shane
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommendRequestVO implements Serializable {

    /** 策略编码 */
    private String code;

    /** 调用方应用标识 */
    private String skyCode;

    /** 链路追踪 TraceId */
    private String traceId;

    /** 流程 ID */
    private String flowId;

    /** 会话 ID */
    private String sessionId;

    /** 业务请求参数 */
    private Map<String, String> bizData;

    /** 是否启用流式传输 */
    @Builder.Default
    private boolean stream = false;
}
