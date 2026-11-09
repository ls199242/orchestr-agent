package com.shane.orchestragent.biz.eval.model;

import com.shane.orchestragent.biz.model.flow.PlanResult;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Map;

/**
 * 评测执行触发请求 DTO (由外部 eval 系统的 Runner 发送给 Agent 的 POST /eval/run 端点)
 *
 * @author Shane
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvalRunRequestDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 目标评测用例唯一标识 (如 "flight_001") */
    private String caseId;

    /** 运行批次标识 (如 "run_0042")，用于区分多次全量批次 */
    private String runId;

    /** 重复运行序号 (如 1..5，用于多次采样测定方差) */
    @Builder.Default
    private int repeatNo = 1;

    /** 运行模式: FREE(端到端规划) / PINNED_PLAN(固定注入DAG以单测调度执行) */
    @Builder.Default
    private String mode = "FREE";

    /** 目标策略业务编码 (可选，若为空则由 Coordinator 意图识别动态分流) */
    private String strategyCode;

    /** 用户主输入诉求文本 (如 "我想12月20日从上海飞东京,帮我看看航班") */
    private String message;

    /** 会话级动态注入参数 (可选，若为空则默认读取用例 Bundle 中的定义) */
    private SessionParams session;

    /** 固定 DAG 规划拓扑 (当 mode == "PINNED_PLAN" 时生效，直接驱动 Supervisor 执行) */
    private PlanResult pinnedDag;

    /** 临时运行覆盖选项字典 */
    private Map<String, Object> options;

    /**
     * 会话参数声明
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SessionParams implements Serializable {
        /** 用户标识 (如 "U001") */
        private String userId;
        /** 模拟固定时间串 (如 "2026-11-07") */
        private String now;
        /** 会话 ID (可选) */
        private String sessionId;
    }
}
