package com.shane.orchestragent.biz.eval.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.*;

/**
 * 完整结构化执行链路 Trace 数据载荷 (对齐评测契约规范与 M3 格式)
 * <p>
 * 职责：
 * <ul>
 *   <li>记录运行元信息、固定时钟、代码快照及提示词版本</li>
 *   <li>完整记录 Coordinator、Planner、Supervisor、Expert、Evaluator、Reporter 等各角色的执行流水</li>
 *   <li>沉淀调度事件时间轴 (schedEvents)，支持评估并发拓扑、调度延迟与甘特图回溯</li>
 *   <li>捕获工具调用完整入参和原始返回结果，支撑硬规则真相核对</li>
 *   <li>汇总全流程 Token 消耗、总耗时与关键路径耗时 (criticalPathMs)，支撑 M6 成本统计</li>
 * </ul>
 *
 * @author Shane
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecutionTraceDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 链路追踪唯一标识 (如 "t_flight001_1700000000") */
    private String traceId;

    /** 关联评测用例唯一标识 (如 "flight_001") */
    private String caseId;

    /** 批次运行 ID (如 "run_0042") */
    private String runId;

    /** 用例重复执行序号 (从 1 开始) */
    private Integer repeatNo;

    /** 运行模式: FREE(端到端规划) / PINNED_PLAN(固定注入DAG) / ISOLATED(单节点单跑) */
    private String mode;

    /** 运行环境: prod(生产线上) / staging(预发) / test(测试) / dev(开发) / local(本地) */
    @Builder.Default
    private String env = "test";

    /** 数据来源: ONLINE(线上真实流量) / EVAL(评测评估) / SHADOW(影子重放) / BENCHMARK(基准测试) */
    @Builder.Default
    private String source = "EVAL";

    /** 会话级运行环境快照 (用户标识与注入的固定时钟) */
    private SessionInfo session;

    /** 流程终态: completed, failed, cancelled, timeout */
    private String finalStatus;

    /** Evaluator 验收自愈打回循环总次数 */
    private Integer loopCount;

    /** 运行期配置与代码版本快照 */
    private SnapshotInfo snapshot;

    /** Coordinator 意图识别与决策记录 (如有触发) */
    private CoordinatorTrace coordinator;

    /** Planner 任务规划尝试记录列表 (支持记录多次尝试与静态代码校验结果) */
    @Builder.Default
    private List<PlannerAttemptTrace> plannerAttempts = new ArrayList<>();

    /** Supervisor DAG 调度器事件时间轴 (用于调度正确性断言与甘特图展示) */
    @Builder.Default
    private List<SchedEventTrace> schedEvents = new ArrayList<>();

    /** 流程中断与取消响应控制记录 (用于取消传播延迟测评) */
    private CancelInfoTrace cancelInfo;

    /** 各 DAG 步骤/Expert 节点的执行明细流水 */
    @Builder.Default
    private List<NodeTrace> nodes = new ArrayList<>();

    /** Evaluator 战略质检逐轮记录 */
    @Builder.Default
    private List<EvaluatorRoundTrace> evaluatorRounds = new ArrayList<>();

    /** Reporter 成果最终交付汇总记录 */
    private ReporterTrace reporter;

    /** 全流程成本指标汇总 (M6 成本统计单一来源) */
    private TotalsTrace totals;

    /**
     * 会话环境信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SessionInfo implements Serializable {
        /** 用户唯一标识 (由用例注入) */
        private String userId;
        /** 注入的固定冻结时钟 (如 2026-11-07T00:00:00Z) */
        private String now;
    }

    /**
     * 系统配置快照信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SnapshotInfo implements Serializable {
        /** 代码 Commit Hash */
        private String gitCommit;
        /** 各智能体角色提示词模板版本/模型标识映射表 */
        private Map<String, String> prompts;
        /** 当前执行的策略编码或编排标识 */
        private String strategy;
    }

    /**
     * Coordinator 意图识别记录
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CoordinatorTrace implements Serializable {
        /** 意图识别结构化决策输出 (包含 action, strategyCode, params, reply 等) */
        private Map<String, Object> output;
        /** 输入消耗 Token 数 */
        private Integer tokensIn;
        /** 输出消耗 Token 数 */
        private Integer tokensOut;
        /** 决策执行耗时 (毫秒) */
        private Long latencyMs;
    }

    /**
     * Planner 任务规划尝试记录
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PlannerAttemptTrace implements Serializable {
        /** 尝试序号 (从 1 开始) */
        private Integer attemptNo;
        /** Planner 规划产出的原始 DAG 步骤拓扑对象 */
        private Object outputDag;
        /** 静态代码规则校验结果 (环检测、池校验、必选覆盖) */
        private ValidationResult validation;
        /** 本次规划耗时 (毫秒) */
        private Long latencyMs;
        /** 提示词消耗 Token */
        private Integer tokensIn;
        /** 生成输出消耗 Token */
        private Integer tokensOut;
    }

    /**
     * DAG 静态校验结果
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ValidationResult implements Serializable {
        /** 是否通过静态拓扑校验 */
        private boolean passed;
        /** 拒绝原因列表 (如存在环、依赖不存在、不在池内等) */
        @Builder.Default
        private List<String> rejectReasons = new ArrayList<>();
    }

    /**
     * 调度事件时间轴流水项 (对应 M3 sched_events)
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SchedEventTrace implements Serializable {
        /** 相对耗时毫秒数 (从流程启动时刻 0 开始算起) */
        private Long ts;
        /** DAG 步骤唯一标识 (如 "s1", "s2") */
        private String nodeId;
        /** 执行智能体或工具名称 */
        private String agent;
        /** 事件类型: ready, start, end, retry, timeout, skip, cancel */
        private String event;
        /** 节点状态: running, success, failed, skipped, cancelled */
        private String status;
        /** 事件详细描述文案 */
        private String detail;
    }

    /**
     * 取消响应与传播监控记录
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CancelInfoTrace implements Serializable {
        /** 取消请求发起的相对时间戳 (毫秒) */
        private Long cancelRequestedTs;
        /** 取消触发原因 (如必要节点失败、外部中断) */
        private String cancelReason;
        /** 各正在执行的在途节点收到中断并响应完成的确认列表 */
        @Builder.Default
        private List<NodeCancelAck> nodeAcks = new ArrayList<>();
    }

    /**
     * 单个在途任务取消响应确认项
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NodeCancelAck implements Serializable {
        /** 节点步骤 ID */
        private String nodeId;
        /** 收到并完成中断的相对时间戳 (毫秒) */
        private Long ackTs;
        /** 从发起取消到该节点实际退出的响应延时 (毫秒) */
        private Long latencyMs;
    }

    /**
     * 单个 Expert 节点/DAG 步骤的完整执行记录
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NodeTrace implements Serializable {
        /** 步骤唯一标识 */
        private String nodeId;
        /** 目标智能体名称 (如 "flight_preference_expert") */
        private String agent;
        /** 终态: success, skipped, failed, cancelled */
        private String status;
        /** 是否为必要步骤 (从策略配置解析或 onFailure 策略推导) */
        private Boolean required;
        /** 执行尝试次数 (从 1 开始) */
        private Integer attempt;
        /** 所处自愈回环轮次 (首次为 0) */
        private Integer loopRound;
        /** 装配完成的下游输入文本 (stitched_input) */
        private String stitchedInput;
        /** 节点在执行期间触发的所有外部工具调用列表 (完整保留入参与出参) */
        @Builder.Default
        private List<ToolCallTrace> toolCalls = new ArrayList<>();
        /** 节点在执行期间触发的知识库检索流水列表 */
        @Builder.Default
        private List<RagCallTrace> ragCalls = new ArrayList<>();
        /** 智能体格式化输出文本 */
        private String output;
        /** 输入 Token 消耗 */
        private Integer tokensIn;
        /** 输出 Token 消耗 */
        private Integer tokensOut;
        /** 节点纯执行耗时 (毫秒) */
        private Long latencyMs;
    }

    /**
     * 工具调用明细 (完整保留参数与原始返回以供事实规则核查)
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ToolCallTrace implements Serializable {
        /** 工具业务编码 (如 "query_flight_schedule_tool") */
        private String toolName;
        /** 调用参数字典 */
        private Map<String, Object> args;
        /** 完整原始响应报文 (禁止截断，用于比对真实航班/订单存在性) */
        private String result;
        /** 异常错误信息 (如有) */
        private String error;
        /** 单次工具调用耗时 (毫秒) */
        private Long latencyMs;
    }

    /**
     * 知识库/RAG 调用明细
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RagCallTrace implements Serializable {
        /** 知识库/伴生节点名称 */
        private String ragName;
        /** 检索查询词 */
        private String query;
        /** 检索命中词条数 */
        private Integer hits;
        /** 检索耗时 (毫秒) */
        private Long latencyMs;
    }

    /**
     * Evaluator 战略目标验收轮次明细 (每轮一条)
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EvaluatorRoundTrace implements Serializable {
        /** 质检自愈轮次 (0 表示首轮验收) */
        private Integer loopRound;
        /** 验收判定结论: pass, insufficient, failed */
        private String verdict;
        /** 质量评分 (0~100) */
        private Integer score;
        /** 质检评审详细意见 */
        private String critique;
        /** 点名需要打回重跑的问题步骤 ID 列表 (如 ["s2"]) */
        @Builder.Default
        private List<String> blamedStepIds = new ArrayList<>();
        /** 针对性整改建议动作 */
        private String suggestedRemedy;
        /** 评估耗时 (毫秒) */
        private Long latencyMs;
        /** 输入消耗 Token */
        private Integer tokensIn;
        /** 输出消耗 Token */
        private Integer tokensOut;
    }

    /**
     * Reporter 最终成果交付记录
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReporterTrace implements Serializable {
        /** 面向用户的最终排版交付文案 */
        private String finalAnswer;
        /** 汇编消耗 Token */
        private Integer tokensIn;
        /** 交付文案 Token */
        private Integer tokensOut;
        /** 汇编耗时 (毫秒) */
        private Long latencyMs;
    }

    /**
     * 全流程各项成本与指标统计汇总 (M6 成本统计)
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TotalsTrace implements Serializable {
        /** 流程端到端全链路总耗时 (毫秒) */
        private Long totalLatencyMs;
        /** 关键路径耗时 (毫秒，用于衡量并行调度收益) */
        private Long criticalPathMs;
        /** 全流程消耗总 Token 数 */
        private Integer totalTokens;
        /** 全流程消耗提示词输入 Token */
        private Integer totalTokensIn;
        /** 全流程模型生成输出 Token */
        private Integer totalTokensOut;
        /** Planner 规划尝试总次数 */
        private Integer plannerAttempts;
        /** 自愈回环重跑总轮次 */
        private Integer loopCount;
        /** 各外部工具调用频次统计表 (如 {"query_flight_schedule_tool": 3}) */
        @Builder.Default
        private Map<String, Integer> toolCallsCount = new LinkedHashMap<>();
        /** 未命中 Mock 的缺口累计次数 (严格模式下未命中抛错前记录) */
        private Integer mockGapsCount;
    }
}
