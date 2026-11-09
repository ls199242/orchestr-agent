package com.shane.orchestragent.biz.eval.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.*;

/**
 * 评测用例运行包契约载荷 DTO (由外部 eval 系统响应提供)
 * <p>
 * 包含单条用例执行所需的环境上下文快照、各工具/RAG 的 Mock 数据集及故障注入规则。
 * Agent 接收此对象后构建独立的 {@link com.shane.orchestragent.biz.eval.context.EvalCaseContext}。
 *
 * @author Shane
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CaseMockBundleDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 用例唯一业务标识 (如 "flight_001") */
    private String caseId;

    /** 派生用例所继承的基础用例标识 (如 "flight_001")，用于复用大体积 Mock 数据集 */
    private String mockRef;

    /** 会话身份与冻结时钟注入配置 */
    private SessionConfig session;

    /** 评测行为控制选项 */
    @Builder.Default
    private OptionsConfig options = new OptionsConfig();

    /** 故障注入规则列表 (超时、异常、慢响应、空响应) */
    @Builder.Default
    private List<FaultInjectionRuleDTO> faultInjections = new ArrayList<>();

    /** 外部接口/工具 Mock 规则字典 (以 toolCode 为 key 索引) */
    @Builder.Default
    private Map<String, List<ToolMockRuleDTO>> toolMocks = new HashMap<>();

    /** 知识库/RAG 检索 Mock 规则字典 (以 ragName 为 key 索引) */
    @Builder.Default
    private Map<String, List<RagMockRuleDTO>> ragMocks = new HashMap<>();

    /**
     * 会话环境注入配置
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SessionConfig implements Serializable {
        /** 模拟用户 ID (如 "U001") */
        private String userId;
        /** 固定冻结时间 ISO 字符串 (如 "2026-11-07T00:00:00Z" 或 "2026-11-07") */
        private String frozenTime;
    }

    /**
     * 评测行为选项
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OptionsConfig implements Serializable {
        /** 是否开启严格 Mock 模式 (未命中 Mock 立即抛错并记为 Mock 缺口，禁止静默访问真实环境) */
        @Builder.Default
        private boolean strictMock = true;

        /** 流程端到端最大超时等待毫秒数 */
        @Builder.Default
        private long timeoutMs = 60000L;
    }

    /**
     * 故障注入规则声明
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FaultInjectionRuleDTO implements Serializable {
        /** 目标智能体、步骤 ID 或工具名称 (如 "flight_preference_expert", "query_flight_schedule_tool") */
        private String target;

        /** 故障类型: timeout(超时挂起), error(异常中断), empty(空响应), slow(模拟延迟) */
        private String type;

        /** 触发第几次尝试 (默认 1，仅在指定尝试轮次触发) */
        @Builder.Default
        private int onAttempt = 1;

        /** 模拟耗时毫秒数 (用于 slow 或 timeout 超时挂起) */
        private Long delayMs;

        /** 自定义模拟抛出的异常文案 */
        private String errorMessage;
    }

    /**
     * 工具 Mock 规则
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ToolMockRuleDTO implements Serializable {
        /** 规则唯一标识 (如 "rule_sf_1220") */
        private String ruleId;

        /** 匹配条件字典 (入参的子集或全部精确匹配，如 {"departureDate": "2026-12-20"}) */
        private Map<String, Object> match;

        /** 命中时返回的单次静态响应报文 (JSON 字符串) */
        private String returnBody;

        /** 按次序返回列表 (支持回环用例，消费完后停留或循环复用) */
        private List<String> sequence;
    }

    /**
     * RAG 知识库 Mock 规则
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RagMockRuleDTO implements Serializable {
        /** 匹配 query 的关键词 (若为空则默认匹配) */
        private String queryMatch;

        /** 检索命中返回的参考知识库内容 */
        private String content;

        /** 模拟命中条数 */
        @Builder.Default
        private int hits = 1;
    }
}
