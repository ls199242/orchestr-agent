package com.shane.orchestragent.biz.eval.trace;

import com.shane.orchestragent.biz.eval.model.ExecutionTraceDTO;
import com.shane.orchestragent.common.time.TimeProvider;
import org.apache.commons.collections4.CollectionUtils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 线程安全的全量 Trace 收集与聚合器
 * <p>
 * 职责：
 * <ul>
 *   <li>高并发线程安全地记录调度事件流水 (schedEvents) 与取消事件</li>
 *   <li>沉淀各角色的输入/输出 Token、耗时与原始报文</li>
 *   <li>维护工具调用频次与未命中 Mock 的缺口计数器</li>
 *   <li>流程结束时计算关键路径耗时 (criticalPathMs) 与全量成本汇总指标</li>
 * </ul>
 *
 * @author Shane
 */
public class TraceCollector {

    private final String traceId;
    private final String caseId;
    private final String runId;
    private final int repeatNo;
    private final String mode;
    private final TimeProvider timeProvider;
    private final long initTs;

    private final AtomicLong startTs = new AtomicLong(0L);
    private final AtomicLong endTs = new AtomicLong(0L);
    private volatile String finalStatus = "completed";

    private volatile ExecutionTraceDTO.CoordinatorTrace coordinatorTrace;
    private final List<ExecutionTraceDTO.PlannerAttemptTrace> plannerAttempts = new CopyOnWriteArrayList<>();
    private final List<ExecutionTraceDTO.SchedEventTrace> schedEvents = new CopyOnWriteArrayList<>();
    private final List<ExecutionTraceDTO.NodeTrace> nodes = new CopyOnWriteArrayList<>();
    private final List<ExecutionTraceDTO.EvaluatorRoundTrace> evaluatorRounds = new CopyOnWriteArrayList<>();
    private volatile ExecutionTraceDTO.ReporterTrace reporterTrace;
    private volatile ExecutionTraceDTO.CancelInfoTrace cancelInfoTrace;

    private final List<ExecutionTraceDTO.ToolCallTrace> pendingToolCalls = new CopyOnWriteArrayList<>();
    private final Map<String, AtomicInteger> toolCallCounters = new ConcurrentHashMap<>();
    private final AtomicInteger mockGapsCounter = new AtomicInteger(0);
    private final AtomicInteger loopCounter = new AtomicInteger(0);

    private final AtomicInteger totalTokensIn = new AtomicInteger(0);
    private final AtomicInteger totalTokensOut = new AtomicInteger(0);

    private String env = "test";
    private String source = "EVAL";
    private String userId;
    private String gitCommit = "dev";
    private String strategyCode = "";
    private final Map<String, String> promptSnapshots = new ConcurrentHashMap<>();

    /**
     * 构造 Trace 收集器
     *
     * @param traceId      链路唯一标识
     * @param caseId       用例标识
     * @param runId        批次标识
     * @param repeatNo     重复序号
     * @param mode         运行模式
     * @param timeProvider 时间提供者 (支持固定冻结时钟)
     */
    public TraceCollector(String traceId, String caseId, String runId, int repeatNo, String mode, TimeProvider timeProvider) {
        this.traceId = traceId;
        this.caseId = caseId;
        this.runId = runId;
        this.repeatNo = repeatNo;
        this.mode = mode != null ? mode : "FREE";
        this.timeProvider = timeProvider != null ? timeProvider : TimeProvider.system();
        // 耗时测量与调度流水必须采用系统单调物理时钟，不受评测业务冻结日期影响
        this.initTs = System.currentTimeMillis();
        this.startTs.set(this.initTs);
    }

    /**
     * 设置运行环境 (如 "test", "prod", "staging")
     *
     * @param env 运行环境
     */
    public void setEnv(String env) {
        if (env != null) {
            this.env = env;
        }
    }

    public String getEnv() {
        return env;
    }

    /**
     * 设置数据来源 (如 "EVAL", "ONLINE")
     *
     * @param source 数据来源
     */
    public void setSource(String source) {
        if (source != null) {
            this.source = source;
        }
    }

    public String getSource() {
        return source;
    }

    /**
     * 设置用户标识
     *
     * @param userId 调用用户 ID
     */
    public void setUserId(String userId) {
        this.userId = userId;
    }

    /**
     * 设置代码 Git Commit Hash
     *
     * @param gitCommit 代码提交版本
     */
    public void setGitCommit(String gitCommit) {
        this.gitCommit = gitCommit;
    }

    /**
     * 设置当前执行策略编码
     *
     * @param strategyCode 策略编码
     */
    public void setStrategyCode(String strategyCode) {
        this.strategyCode = strategyCode;
    }

    /**
     * 记录智能体角色当前采用的提示词版本或模型快照
     *
     * @param role    角色名称 (如 "PLANNER", "COORDINATOR")
     * @param version 版本或模型标识
     */
    public void recordPromptSnapshot(String role, String version) {
        if (role != null && version != null) {
            promptSnapshots.put(role, version);
        }
    }

    /**
     * 标记流程执行终态并封存结束时间戳
     *
     * @param status 流程终态 (completed, failed, cancelled, timeout)
     */
    public void setFinalStatus(String status) {
        this.finalStatus = status;
        this.endTs.set(System.currentTimeMillis());
    }

    /**
     * 记录 Coordinator 意图识别与参数提取流水
     *
     * @param output    结构化意图决策产出
     * @param inTokens  输入提示词 Token
     * @param outTokens 输出模型生成 Token
     * @param latencyMs 决策耗时 (毫秒)
     */
    public void recordCoordinator(Map<String, Object> output, int inTokens, int outTokens, long latencyMs) {
        this.coordinatorTrace = ExecutionTraceDTO.CoordinatorTrace.builder()
                .output(output)
                .tokensIn(inTokens)
                .tokensOut(outTokens)
                .latencyMs(latencyMs)
                .build();
        totalTokensIn.addAndGet(inTokens);
        totalTokensOut.addAndGet(outTokens);
    }

    /**
     * 记录 Planner 规划尝试流水与静态代码校验结论
     *
     * @param attemptNo     规划尝试序号 (第几次)
     * @param outputDag     产出的 DAG 结构体
     * @param passed        静态校验是否通过
     * @param rejectReasons 若被代码拒绝，记录拒绝原因列表
     * @param latencyMs     本次规划耗时 (毫秒)
     * @param inTokens      输入 Token
     * @param outTokens     输出 Token
     */
    public void recordPlannerAttempt(int attemptNo, Object outputDag, boolean passed, List<String> rejectReasons,
                                     long latencyMs, int inTokens, int outTokens) {
        ExecutionTraceDTO.ValidationResult validation = ExecutionTraceDTO.ValidationResult.builder()
                .passed(passed)
                .rejectReasons(rejectReasons != null ? new ArrayList<>(rejectReasons) : Collections.emptyList())
                .build();

        ExecutionTraceDTO.PlannerAttemptTrace attempt = ExecutionTraceDTO.PlannerAttemptTrace.builder()
                .attemptNo(attemptNo)
                .outputDag(outputDag)
                .validation(validation)
                .latencyMs(latencyMs)
                .tokensIn(inTokens)
                .tokensOut(outTokens)
                .build();
        plannerAttempts.add(attempt);
        totalTokensIn.addAndGet(inTokens);
        totalTokensOut.addAndGet(outTokens);
    }

    /**
     * 记录 Supervisor 调度事件流水 (用于还原时间线拓扑与甘特图)
     *
     * @param nodeId 步骤 ID (如 "s1")
     * @param agent  目标智能体名称
     * @param event  事件类型: ready, start, end, retry, timeout, skip, cancel
     * @param status 当前状态: running, success, failed, skipped, cancelled
     * @param detail 事件详情文案
     */
    public void recordSchedEvent(String nodeId, String agent, String event, String status, String detail) {
        long relativeTs = Math.max(0, System.currentTimeMillis() - initTs);
        schedEvents.add(ExecutionTraceDTO.SchedEventTrace.builder()
                .ts(relativeTs)
                .nodeId(nodeId)
                .agent(agent)
                .event(event)
                .status(status)
                .detail(detail)
                .build());
    }

    /**
     * 记录必要节点失败时触发的级联取消请求
     *
     * @param reason 取消触发原因
     */
    public void recordCancelRequested(String reason) {
        if (this.cancelInfoTrace == null) {
            this.cancelInfoTrace = ExecutionTraceDTO.CancelInfoTrace.builder()
                    .cancelRequestedTs(Math.max(0, System.currentTimeMillis() - initTs))
                    .cancelReason(reason)
                    .build();
        }
    }

    /**
     * 记录正在执行的在途节点完成中断取消的确认时点 (用于测评取消传播延迟)
     *
     * @param nodeId    步骤 ID
     * @param latencyMs 从发起取消到收到中断退出的响应延时 (毫秒)
     */
    public void recordCancelAck(String nodeId, long latencyMs) {
        if (this.cancelInfoTrace != null && nodeId != null) {
            long relativeTs = Math.max(0, System.currentTimeMillis() - initTs);
            this.cancelInfoTrace.getNodeAcks().add(ExecutionTraceDTO.NodeCancelAck.builder()
                    .nodeId(nodeId)
                    .ackTs(relativeTs)
                    .latencyMs(latencyMs)
                    .build());
        }
    }

    /**
     * 记录单个 Expert 节点的完整执行明细流水
     *
     * @param nodeTrace 节点明细载荷
     */
    public void recordNodeExecution(ExecutionTraceDTO.NodeTrace nodeTrace) {
        if (nodeTrace != null) {
            if (CollectionUtils.isEmpty(nodeTrace.getToolCalls()) && !pendingToolCalls.isEmpty()) {
                nodeTrace.setToolCalls(new ArrayList<>(pendingToolCalls));
                pendingToolCalls.clear();
            }
            nodes.add(nodeTrace);
            if (nodeTrace.getTokensIn() != null) {
                totalTokensIn.addAndGet(nodeTrace.getTokensIn());
            }
            if (nodeTrace.getTokensOut() != null) {
                totalTokensOut.addAndGet(nodeTrace.getTokensOut());
            }
        }
    }

    /**
     * 记录外部工具调用行为 (累计调用频次与工具原始未截断报文)
     *
     * @param toolCode  工具唯一编码
     * @param args      调用实参字典
     * @param result    完整返回报文
     * @param error     错误信息
     * @param latencyMs 调用耗时
     */
    public void recordToolCall(String toolCode, Map<String, Object> args, String result, String error, long latencyMs) {
        toolCallCounters.computeIfAbsent(toolCode, k -> new AtomicInteger(0)).incrementAndGet();
        pendingToolCalls.add(ExecutionTraceDTO.ToolCallTrace.builder()
                .toolName(toolCode)
                .args(args != null ? new HashMap<>(args) : Collections.emptyMap())
                .result(result)
                .error(error)
                .latencyMs(latencyMs)
                .build());
    }

    /**
     * 记录未命中预置 Mock 的缺口计数 (严格模式下用于标记缺口)
     */
    public void recordMockGap() {
        mockGapsCounter.incrementAndGet();
    }

    /**
     * 记录 Evaluator 战略目标质检验收轮次
     *
     * @param loopRound 质检轮次 (0 表示首次)
     * @param verdict   判定结论 (pass, insufficient)
     * @param score     质量评分
     * @param critique  质检反馈说明
     * @param blamedStepIds 点名打回的步骤列表
     * @param remedy    建议整改动作
     * @param latencyMs 质检耗时 (毫秒)
     * @param inTokens  输入 Token
     * @param outTokens 输出 Token
     */
    public void recordEvaluatorRound(int loopRound, String verdict, int score, String critique,
                                     List<String> blamedStepIds, String remedy, long latencyMs,
                                     int inTokens, int outTokens) {
        loopCounter.set(loopRound);
        evaluatorRounds.add(ExecutionTraceDTO.EvaluatorRoundTrace.builder()
                .loopRound(loopRound)
                .verdict(verdict)
                .score(score)
                .critique(critique)
                .blamedStepIds(blamedStepIds != null ? new ArrayList<>(blamedStepIds) : Collections.emptyList())
                .suggestedRemedy(remedy)
                .latencyMs(latencyMs)
                .tokensIn(inTokens)
                .tokensOut(outTokens)
                .build());
        totalTokensIn.addAndGet(inTokens);
        totalTokensOut.addAndGet(outTokens);
    }

    /**
     * 记录 Reporter 成果汇报输出
     *
     * @param finalAnswer 交付文案
     * @param inTokens    输入 Token
     * @param outTokens   输出 Token
     * @param latencyMs   耗时 (毫秒)
     */
    public void recordReporter(String finalAnswer, int inTokens, int outTokens, long latencyMs) {
        this.reporterTrace = ExecutionTraceDTO.ReporterTrace.builder()
                .finalAnswer(finalAnswer)
                .tokensIn(inTokens)
                .tokensOut(outTokens)
                .latencyMs(latencyMs)
                .build();
        totalTokensIn.addAndGet(inTokens);
        totalTokensOut.addAndGet(outTokens);
    }

    /**
     * 计算并构造最终完整的强契约 ExecutionTraceDTO 对象
     *
     * @return 完整结构化 Trace
     */
    public ExecutionTraceDTO buildTrace() {
        long end = endTs.get() > 0 ? endTs.get() : System.currentTimeMillis();
        long totalLatency = Math.max(0, end - startTs.get());

        Map<String, Integer> toolCounts = new LinkedHashMap<>();
        toolCallCounters.forEach((k, v) -> toolCounts.put(k, v.get()));

        int tIn = totalTokensIn.get();
        int tOut = totalTokensOut.get();

        long criticalPath = calculateCriticalPath(totalLatency);

        ExecutionTraceDTO.TotalsTrace totals = ExecutionTraceDTO.TotalsTrace.builder()
                .totalLatencyMs(totalLatency)
                .criticalPathMs(criticalPath)
                .totalTokens(tIn + tOut)
                .totalTokensIn(tIn)
                .totalTokensOut(tOut)
                .plannerAttempts(Math.max(1, plannerAttempts.size()))
                .loopCount(loopCounter.get())
                .toolCallsCount(toolCounts)
                .mockGapsCount(mockGapsCounter.get())
                .build();

        return ExecutionTraceDTO.builder()
                .traceId(traceId)
                .caseId(caseId)
                .runId(runId)
                .repeatNo(repeatNo)
                .mode(mode)
                .env(env)
                .source(source)
                .session(ExecutionTraceDTO.SessionInfo.builder()
                        .userId(userId)
                        .now(timeProvider.currentIsoString())
                        .build())
                .finalStatus(finalStatus)
                .loopCount(loopCounter.get())
                .snapshot(ExecutionTraceDTO.SnapshotInfo.builder()
                        .gitCommit(gitCommit)
                        .prompts(new HashMap<>(promptSnapshots))
                        .strategy(strategyCode)
                        .build())
                .coordinator(coordinatorTrace)
                .plannerAttempts(new ArrayList<>(plannerAttempts))
                .schedEvents(new ArrayList<>(schedEvents))
                .cancelInfo(cancelInfoTrace)
                .nodes(new ArrayList<>(nodes))
                .evaluatorRounds(new ArrayList<>(evaluatorRounds))
                .reporter(reporterTrace)
                .totals(totals)
                .build();
    }

    /**
     * 关键路径耗时估算计算 (用于评估并行并发收益)
     */
    private long calculateCriticalPath(long defaultLatency) {
        if (CollectionUtils.isEmpty(nodes)) {
            return defaultLatency;
        }
        long maxNodeChain = 0;
        for (ExecutionTraceDTO.NodeTrace node : nodes) {
            if (node.getLatencyMs() != null) {
                maxNodeChain += node.getLatencyMs();
            }
        }
        return Math.min(defaultLatency, maxNodeChain > 0 ? maxNodeChain : defaultLatency);
    }
}
