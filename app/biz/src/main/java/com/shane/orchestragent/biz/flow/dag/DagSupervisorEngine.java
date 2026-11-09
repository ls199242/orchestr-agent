package com.shane.orchestragent.biz.flow.dag;

import com.shane.orchestragent.biz.agent.Agent;
import com.shane.orchestragent.biz.agent.flow.RagAgent;
import com.shane.orchestragent.biz.context.StrategyContext;
import com.shane.orchestragent.biz.context.impl.StepStrategyContext;
import com.shane.orchestragent.biz.eval.model.ExecutionTraceDTO;
import com.shane.orchestragent.biz.eval.trace.TraceCollector;
import com.shane.orchestragent.biz.model.agent.AgentResult;
import com.shane.orchestragent.biz.model.agent.ExpertErrorAgentResult;
import com.shane.orchestragent.biz.model.flow.*;
import com.shane.orchestragent.common.enums.AgentTypeEnum;
import com.shane.orchestragent.common.exception.BizErrorFactory;
import com.shane.orchestragent.common.exception.BizException;
import com.shane.orchestragent.common.utils.JsonUtils;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

/**
 * 纯代码 DAG 并发调度中枢引擎
 * <p>
 * 职责：
 * <ul>
 *   <li>静态 DAG 拓扑校验与 Kahn 算法环检测 (Fail-Fast 铁律)</li>
 *   <li>基于依赖就绪度的动态分轮并发调度 (虚拟线程并发驱动，杜绝平台线程耗尽与嵌套饥饿死锁)</li>
 *   <li>机械化参数求值装配 ($global.x / $s1.output.y)</li>
 *   <li>超时中断守护、失败重试与输出契约校验 (outputKeys)</li>
 *   <li>基于传递闭包 (Closure) 的局部精准重跑与结果复用自愈闭环</li>
 * </ul>
 *
 * @author Shane
 */
public class DagSupervisorEngine {

    private static final Logger log = LoggerFactory.getLogger(DagSupervisorEngine.class);

    /** 单步超时看门狗定时调度器 (单守护线程轻量中断，不占用任务线程池资源) */
    private static final ScheduledExecutorService TIMEOUT_SCHEDULER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "dag-step-timeout-watchdog");
        t.setDaemon(true);
        return t;
    });

    private final ExecutorService executorService;
    private final boolean internalPool;

    public DagSupervisorEngine() {
        this(null);
    }

    public DagSupervisorEngine(ExecutorService executorService) {
        if (executorService != null) {
            this.executorService = executorService;
            this.internalPool = false;
        } else {
            // 采用命名虚拟线程执行器：随用随建、自动卸载、零队列堆积、无平台线程饥饿死锁
            ThreadFactory factory = Thread.ofVirtual().name("dag-supervisor-worker-", 1).factory();
            this.executorService = Executors.newThreadPerTaskExecutor(factory);
            this.internalPool = true;
        }
    }

    /**
     * 首次调度执行完整 DAG
     *
     * @param plan    任务规划图
     * @param context 策略运行上下文
     * @return 执行状态黑板
     * @throws BizException 若校验失败或执行异常
     */
    public DagExecutionState run(PlanResult plan, StrategyContext context) throws BizException {
        return run(plan, context, null);
    }

    /**
     * 首次调度执行完整 DAG (携带单步执行结果监听回调)
     *
     * @param plan         任务规划图
     * @param context      策略运行上下文
     * @param stepCallback 单步完成回调 (Agent, AgentResult)
     * @return 执行状态黑板
     * @throws BizException 若校验失败或执行异常
     */
    public DagExecutionState run(PlanResult plan, StrategyContext context,
                                 BiConsumer<Agent, AgentResult> stepCallback) throws BizException {
        validate(plan, context);
        DagExecutionState state = new DagExecutionState(plan);
        drive(plan, state, context, Collections.emptyMap(), stepCallback);
        return state;
    }

    /**
     * Evaluator 打回后的局部重跑 (Self-Correction Loop)
     *
     * @param plan     任务规划图
     * @param state    已有执行黑板
     * @param feedback 针对各步骤的打回反馈 (key: stepId, value: critique/remedy)
     * @param context  策略运行上下文
     * @return 更新后的执行状态黑板
     * @throws BizException 若重跑失败
     */
    public DagExecutionState rerun(PlanResult plan, DagExecutionState state,
                                   Map<String, String> feedback, StrategyContext context) throws BizException {
        return rerun(plan, state, feedback, context, null);
    }

    /**
     * Evaluator 打回后的局部重跑 (Self-Correction Loop，携带单步执行结果监听回调)
     *
     * @param plan         任务规划图
     * @param state        已有执行黑板
     * @param feedback     针对各步骤的打回反馈 (key: stepId, value: critique/remedy)
     * @param context      策略运行上下文
     * @param stepCallback 单步完成回调 (Agent, AgentResult)
     * @return 更新后的执行状态黑板
     * @throws BizException 若重跑失败
     */
    public DagExecutionState rerun(PlanResult plan, DagExecutionState state,
                                   Map<String, String> feedback, StrategyContext context,
                                   BiConsumer<Agent, AgentResult> stepCallback) throws BizException {
        if (feedback == null || feedback.isEmpty()) {
            drive(plan, state, context, Collections.emptyMap(), stepCallback);
            return state;
        }

        for (String id : feedback.keySet()) {
            if (!state.getStatus().containsKey(id)) {
                throw new BizException("DAG_BLAMED_STEP_NOT_FOUND", "Evaluator 打回了不存在的步骤 ID: " + id);
            }
        }

        // 计算受波及的传递下游闭包：被点名的步骤 + 依赖它们的所有下游全部作废；上游已成功的节点结果原样复用
        Set<String> invalidated = closure(plan, feedback.keySet());
        log.info("[Flow: {}][DAG_ENGINE] 触发局部重跑，作废步骤集合: {}", context.getFlowId(), invalidated);

        for (String id : invalidated) {
            state.getStatus().put(id, DagStepStatus.PENDING);
            state.getOutputs().remove(id);
            state.getRawOutputs().remove(id);
            state.getErrors().remove(id);
        }

        drive(plan, state, context, feedback, stepCallback);
        return state;
    }

    // ==========================================
    // 调度核心主循环
    // ==========================================

    /**
     * 核心调度主循环：分轮识别就绪步骤，单层线程池并发驱动，收集结果并推进 DAG 状态
     */
    private void drive(PlanResult plan, DagExecutionState state,
                       StrategyContext context, Map<String, String> feedback,
                       BiConsumer<Agent, AgentResult> stepCallback) throws BizException {
        BlockingQueue<DagOutcome> done = new LinkedBlockingQueue<>();
        Map<String, FlowDagStep> byId = plan.getSteps().stream()
                .collect(Collectors.toMap(FlowDagStep::getId, s -> s));

        Map<String, Future<?>> activeFutures = new ConcurrentHashMap<>();
        int running = 0;
        int round = 0;
        boolean aborted = false;
        String abortReason = null;
        String flowId = context != null ? context.getFlowId() : "N/A";
        TraceCollector traceCollector = (context != null && context.getEvalCaseContext() != null)
                ? context.getEvalCaseContext().getTraceCollector()
                : null;

        while (true) {
            if (isStopped(context) || executorService.isShutdown()) {
                cancelFutures(activeFutures, traceCollector);
                throw BizErrorFactory.getInstance().strategyFlowStopped();
            }

            // 1. 扫描所有依赖已满足的 PENDING 步骤，并发提交
            if (!aborted) {
                List<FlowDagStep> readySteps = new ArrayList<>();
                for (FlowDagStep s : plan.getSteps()) {
                    if (state.getStatus().get(s.getId()) == DagStepStatus.PENDING && isReady(s, state)) {
                        readySteps.add(s);
                    }
                }
                if (!readySteps.isEmpty()) {
                    round++;
                    List<String> readyDesc = readySteps.stream()
                            .map(s -> s.getId() + " (" + s.getAgent() + ")")
                            .toList();
                    log.info("[Flow: {}][SUPERVISOR][ROUND {}] 识别到本轮就绪待调度节点 (共 {} 个): {}，正在并发提交执行...",
                            flowId, round, readySteps.size(), readyDesc);
                    for (FlowDagStep s : readySteps) {
                        state.getStatus().put(s.getId(), DagStepStatus.RUNNING);
                        running++;
                        recordStepStartEvents(traceCollector, s);
                        String stepFb = feedback.get(s.getId());
                        try {
                            Future<?> future = executorService.submit(() -> {
                                try {
                                    DagOutcome outcome = runStep(s, state, context, stepFb, stepCallback);
                                    done.add(outcome);
                                } catch (Throwable t) {
                                    done.add(new DagOutcome(s.getId(), null, null, "系统执行异常: " + t.getMessage(), 1));
                                } finally {
                                    activeFutures.remove(s.getId());
                                }
                            });
                            activeFutures.put(s.getId(), future);
                        } catch (RejectedExecutionException e) {
                            cancelFutures(activeFutures, traceCollector);
                            throw BizErrorFactory.getInstance().strategyFlowStopped();
                        }
                    }
                }
            }

            if (running == 0) {
                break;
            }

            // 2. 阻塞等待任意一个子任务执行完成
            DagOutcome o;
            try {
                o = done.take();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                cancelFutures(activeFutures, traceCollector);
                throw BizErrorFactory.getInstance().strategyFlowStopped();
            }

            running--;
            state.getAttempts().merge(o.getStepId(), o.getAttempts(), Integer::sum);
            FlowDagStep completedStep = byId.get(o.getStepId());
            String agentName = completedStep != null ? completedStep.getAgent() : o.getStepId();

            if (o.getError() == null) {
                if (o.getOutput() != null) {
                    state.getOutputs().put(o.getStepId(), o.getOutput());
                }
                if (o.getRawOutput() != null) {
                    state.getRawOutputs().put(o.getStepId(), o.getRawOutput());
                }
                state.getStatus().put(o.getStepId(), DagStepStatus.SUCCEEDED);
                log.info("[Flow: {}][SUPERVISOR] 步骤 [{}] ({}) 执行完成 (尝试次数: {})",
                        flowId, o.getStepId(), agentName, o.getAttempts());

                recordStepSuccessTrace(traceCollector, completedStep, agentName, o, context);
            } else {
                state.getErrors().put(o.getStepId(), o.getError());
                FlowDagStep step = byId.get(o.getStepId());
                if (step != null && step.getOnFailure() == OnFailureAction.SKIP) {
                    state.getStatus().put(o.getStepId(), DagStepStatus.SKIPPED);
                    log.warn("[Flow: {}][SUPERVISOR] 步骤 [{}] ({}) 失败但策略为 SKIP，已跳过: {}",
                            flowId, o.getStepId(), step.getAgent(), o.getError());
                    recordStepSkippedTrace(traceCollector, step, o, context);
                } else {
                    state.getStatus().put(o.getStepId(), DagStepStatus.FAILED);
                    aborted = true;
                    abortReason = o.getError();
                    if (traceCollector != null) {
                        traceCollector.recordCancelRequested("必要节点失败触发级联取消: " + o.getError());
                    }
                    cancelFutures(activeFutures, traceCollector);
                    log.error("[Flow: {}][SUPERVISOR] 步骤 [{}] ({}) 失败导致 DAG 中止: {}",
                            flowId, o.getStepId(), step != null ? step.getAgent() : "", o.getError());

                    recordStepFailedTrace(traceCollector, step, o, context);
                }
            }
        }

        if (aborted) {
            state.getStatus().replaceAll((id, s) -> s == DagStepStatus.PENDING ? DagStepStatus.CANCELLED : s);
            throw new BizException("DAG_STEP_FAILED", "DAG 执行失败中断: " + abortReason);
        }

        log.info("[Flow: {}][SUPERVISOR] 全流程 DAG 所有节点执行完成 (共调度 {} 轮)", flowId, round);
    }

    /**
     * 判断单步是否所有依赖均已就绪
     */
    private boolean isReady(FlowDagStep s, DagExecutionState st) {
        for (String dep : s.allDeps()) {
            DagStepStatus ds = st.getStatus().get(dep);
            if (ds != DagStepStatus.SUCCEEDED && ds != DagStepStatus.SKIPPED) {
                return false;
            }
        }
        return true;
    }

    // ==========================================
    // 单步执行管线
    // ==========================================

    /**
     * 单个步骤的执行管线：参数装配 -> 伴生 RAG -> 超时与重试控制 -> 契约校验
     */
    private DagOutcome runStep(FlowDagStep step, DagExecutionState st, StrategyContext context,
                               String stepFeedback, BiConsumer<Agent, AgentResult> stepCallback) {
        Agent agent = context.getAgents().get(step.getAgent());
        if (agent == null) {
            return new DagOutcome(step.getId(), null, null, "目标 Agent 未注册: " + step.getAgent(), 0);
        }

        // 1. 机械化参数求值装配
        Map<String, Object> resolvedInputs = assembleInputs(step, st, context);

        // 2. 线程隔离的局部上下文
        StepStrategyContext stepContext = new StepStrategyContext(context, resolvedInputs, stepFeedback);

        // 3. 伴生 RAG 前置加载
        preloadCompanionRag(agent, stepContext, context);

        // 4. 单线程重试与超时控制循环 (当前线程同步执行，杜绝嵌套提交)
        return executeWithRetryAndTimeout(agent, step, stepContext, resolvedInputs, stepCallback);
    }

    /**
     * 步骤执行与重试主流程（同步调用 Agent，采用全局调度器超时中断守护）
     */
    private DagOutcome executeWithRetryAndTimeout(Agent agent, FlowDagStep step,
                                                  StepStrategyContext stepContext,
                                                  Map<String, Object> resolvedInputs,
                                                  BiConsumer<Agent, AgentResult> stepCallback) {
        int maxRetries = Math.max(0, step.getMaxRetries());
        long timeoutMs = step.getTimeoutMs() > 0 ? step.getTimeoutMs() : 30000L;
        String lastError = null;
        int attempt = 0;

        while (attempt <= maxRetries) {
            attempt++;
            final int curAttempt = attempt;
            if (lastError != null) {
                stepContext.setProperties("lastError", lastError);
            }

            log.info("[Flow: {}][SUPERVISOR][STEP {}] 启动步骤 ({}) 第 {}/{} 次调用，入参装配: {}",
                    stepContext.getFlowId(), step.getId(), step.getAgent(), curAttempt, maxRetries + 1,
                    JsonUtils.toJsonString(resolvedInputs));

            Thread currentThread = Thread.currentThread();
            ScheduledFuture<?> timeoutWatchdog = null;
            if (timeoutMs > 0) {
                timeoutWatchdog = TIMEOUT_SCHEDULER.schedule(currentThread::interrupt, timeoutMs, TimeUnit.MILLISECONDS);
            }

            AgentResult result = null;
            boolean timedOut = false;
            try {
                // 直接在当前工作线程同步执行，杜绝二次提交与同线程池饥饿死锁
                result = agent.execute(stepContext);
            } catch (Exception e) {
                if (Thread.interrupted() || isTimeoutException(e)) {
                    timedOut = true;
                    lastError = "执行超时 (" + timeoutMs + "ms)";
                } else {
                    lastError = "执行异常: " + (e.getCause() != null ? e.getCause().getMessage() : e.getMessage());
                }
            } finally {
                if (timeoutWatchdog != null) {
                    timeoutWatchdog.cancel(false);
                }
                if (Thread.interrupted()) {
                    timedOut = true;
                    if (lastError == null) {
                        lastError = "执行超时 (" + timeoutMs + "ms)";
                    }
                }
            }

            if (timedOut) {
                continue;
            }

            if (result == null || StringUtils.isBlank(result.getOutput())) {
                lastError = "节点产出为空";
                continue;
            }

            String rawOutput = result.getOutput().trim();

            // Expert 业务错误拦截
            if (agent.getType() == AgentTypeEnum.EXPERT) {
                ExpertErrorAgentResult errorResult = ExpertErrorAgentResult.format(rawOutput);
                if (errorResult != null) {
                    lastError = "Expert 抛出业务异常: " + errorResult.getErrorMessage();
                    continue;
                }
            }

            // 结构化解析
            Object structuredOutput = tryParseJson(rawOutput);

            // 契约校验
            String contractError = validateOutputContract(step, agent, structuredOutput);
            if (contractError != null) {
                lastError = contractError;
                continue;
            }

            // 执行成功回调
            notifyStepCallback(stepCallback, agent, resolvedInputs, rawOutput);

            log.info("[Flow: {}][SUPERVISOR][STEP {}] 节点 [{}] 执行完成，产出摘要: {}",
                    stepContext.getFlowId(), step.getId(), step.getAgent(), StringUtils.abbreviate(rawOutput, 200));

            return new DagOutcome(step.getId(), structuredOutput, rawOutput, null, curAttempt);
        }

        return new DagOutcome(step.getId(), null, null, lastError, attempt);
    }

    /**
     * 判断异常是否由超时或打断引起
     */
    private boolean isTimeoutException(Exception e) {
        if (e instanceof TimeoutException || e instanceof InterruptedException) {
            return true;
        }
        if (e.getCause() instanceof TimeoutException || e.getCause() instanceof InterruptedException) {
            return true;
        }
        String msg = e.getMessage();
        return msg != null && (msg.contains("timed out") || msg.contains("Timeout") || msg.contains("interrupt"));
    }

    /**
     * 契约字段校验
     */
    private String validateOutputContract(FlowDagStep step, Agent agent, Object structuredOutput) {
        if (CollectionUtils.isEmpty(step.getOutputKeys())) {
            return null;
        }
        if (agent.getType() == AgentTypeEnum.EXPERT) {
            // Expert 专精节点产出为自然语言/Markdown 推荐内容，放行强校验
            return null;
        }
        if (!(structuredOutput instanceof Map<?, ?> outMap)) {
            return "产出并非 JSON 对象，无法满足契约校验: " + step.getOutputKeys();
        }
        List<String> missingKeys = step.getOutputKeys().stream()
                .filter(k -> !containsKeyIgnoreCase(outMap, k))
                .toList();
        if (!missingKeys.isEmpty()) {
            return "产出缺少必要契约字段: " + missingKeys;
        }
        return null;
    }

    /**
     * 步骤完成回调通知
     */
    private void notifyStepCallback(BiConsumer<Agent, AgentResult> stepCallback,
                                    Agent agent,
                                    Map<String, Object> resolvedInputs,
                                    String rawOutput) {
        if (stepCallback == null) {
            return;
        }
        try {
            String detail;
            if (agent.getType() == AgentTypeEnum.TOOL) {
                detail = "【入参 requestBody】:\n" + JsonUtils.toJsonString(resolvedInputs) + "\n\n【响应 responseBody】:\n" + rawOutput;
            } else {
                detail = rawOutput;
            }
            stepCallback.accept(agent, new AgentResult(detail, null));
        } catch (Exception e) {
            log.warn("[DAG_ENGINE] 步骤完成回调执行异常: {}", e.getMessage());
        }
    }

    /**
     * 专精 Expert 伴生 RAG 隐式前置加载
     */
    private void preloadCompanionRag(Agent agent, StepStrategyContext stepContext, StrategyContext context) {
        if (agent.getType() != AgentTypeEnum.EXPERT) {
            return;
        }
        String companionRagName = RagAgent.buildRagName(agent.getName());
        Agent companionRag = context.getAgents().get(companionRagName);
        if (companionRag != null) {
            try {
                companionRag.execute(stepContext);
            } catch (Exception e) {
                log.warn("[Flow: {}][DAG_ENGINE][RAG] 伴生 RAG 检索异常 (非致命): {}", context.getFlowId(), e.getMessage());
            }
        }
    }

    /**
     * 机械化参数求值装配
     */
    private Map<String, Object> assembleInputs(FlowDagStep step, DagExecutionState st, StrategyContext context) {
        Map<String, Object> resolvedInputs = new LinkedHashMap<>();
        if (step.getInputs() != null) {
            step.getInputs().forEach((k, expr) -> {
                Object val = resolveExpr(expr, st, context);
                if (val != null) {
                    resolvedInputs.put(k, val);
                }
            });
        }
        return resolvedInputs;
    }

    // ==========================================
    // 拓扑分析与 Kahn 算法静态校验
    // ==========================================

    /**
     * 静态 DAG 规则校验 (Fail-Fast 铁律)
     */
    public static void validate(PlanResult plan, StrategyContext context) throws BizException {
        if (plan == null || CollectionUtils.isEmpty(plan.getSteps())) {
            throw BizErrorFactory.getInstance().plannerReturnEmpty();
        }

        Set<String> ids = new HashSet<>();
        for (FlowDagStep s : plan.getSteps()) {
            if (StringUtils.isBlank(s.getId())) {
                throw new BizException("DAG_VALIDATION_ERROR", "步骤 ID 不能为空");
            }
            if (!ids.add(s.getId())) {
                throw new BizException("DAG_VALIDATION_ERROR", "发现重复的步骤 ID: " + s.getId());
            }
            if (context != null && context.getAgents() != null && !context.getAgents().containsKey(s.getAgent())) {
                throw BizErrorFactory.getInstance().agentNotFound(s.getAgent());
            }
        }

        for (FlowDagStep s : plan.getSteps()) {
            for (String dep : s.allDeps()) {
                if (!ids.contains(dep)) {
                    throw new BizException("DAG_VALIDATION_ERROR",
                            "步骤 [" + s.getId() + "] 依赖了不存在的步骤: [" + dep + "]");
                }
            }
        }

        // Kahn 算法入度环检测
        Map<String, Integer> inDegree = new HashMap<>();
        for (FlowDagStep s : plan.getSteps()) {
            inDegree.put(s.getId(), s.allDeps().size());
        }
        Map<String, List<String>> down = downstream(plan);
        Deque<String> queue = new ArrayDeque<>();
        inDegree.forEach((id, deg) -> {
            if (deg == 0) {
                queue.add(id);
            }
        });

        int visited = 0;
        while (!queue.isEmpty()) {
            String curr = queue.poll();
            visited++;
            for (String next : down.getOrDefault(curr, Collections.emptyList())) {
                if (inDegree.merge(next, -1, Integer::sum) == 0) {
                    queue.add(next);
                }
            }
        }

        if (visited != plan.getSteps().size()) {
            throw new BizException("DAG_CYCLE_DETECTED", "任务规划 Plan 中存在循环依赖，无法构建有效 DAG");
        }
    }

    /**
     * 依赖反向索引 (前序步骤 -> 依赖该步骤的后序步骤列表)
     */
    public static Map<String, List<String>> downstream(PlanResult plan) {
        Map<String, List<String>> down = new HashMap<>();
        for (FlowDagStep s : plan.getSteps()) {
            for (String dep : s.allDeps()) {
                down.computeIfAbsent(dep, k -> new ArrayList<>()).add(s.getId());
            }
        }
        return down;
    }

    /**
     * 计算 roots 节点及其所有传递下游依赖的闭包集合 (用于局部自愈重跑)
     */
    public static Set<String> closure(PlanResult plan, Collection<String> roots) {
        Map<String, List<String>> down = downstream(plan);
        Set<String> seen = new LinkedHashSet<>(roots);
        Deque<String> queue = new ArrayDeque<>(roots);
        while (!queue.isEmpty()) {
            String cur = queue.poll();
            for (String next : down.getOrDefault(cur, Collections.emptyList())) {
                if (seen.add(next)) {
                    queue.add(next);
                }
            }
        }
        return seen;
    }

    // ==========================================
    // 表达式机械解析与辅助工具
    // ==========================================

    /**
     * 引用表达式机械解析 ($global.x / $s1.output / $s1.output.y)
     */
    public static Object resolveExpr(String expr, DagExecutionState st, StrategyContext context) {
        if (StringUtils.isBlank(expr) || !expr.startsWith("$")) {
            return expr;
        }

        String[] parts = expr.substring(1).split("\\.");
        if (parts.length == 0) {
            return expr;
        }

        Object cur;
        int idx = 1;
        if ("global".equalsIgnoreCase(parts[0])) {
            if (parts.length == 1) {
                return context.getProperties();
            }
            String key = parts[1];
            cur = resolveGlobalProperty(key, context);
            idx = 2;
        } else {
            String stepId = parts[0];
            // 评测规范: 若上游步骤被 SKIP (如非必要偏好分析失败)，下游输入占位符解析为 [MISSING: <stepId>]，使下游显式感知偏好缺失并按无偏好兜底处理
            if (st.getStatus().get(stepId) == DagStepStatus.SKIPPED) {
                return "[MISSING: " + stepId + "]";
            }
            cur = st.getOutputs().get(stepId);
            if (cur == null) {
                cur = st.getRawOutputs().get(stepId);
            }
            if (parts.length > 1 && "output".equalsIgnoreCase(parts[1])) {
                idx = 2;
            }
        }

        for (int i = idx; i < parts.length && cur != null; i++) {
            String part = parts[i];
            if (cur instanceof Map<?, ?> m) {
                cur = getValueIgnoreCase(m, part);
            } else if (cur instanceof String s && s.trim().startsWith("{")) {
                Map<String, Object> map = tryParseMap(s);
                cur = map != null ? getValueIgnoreCase(map, part) : null;
            } else {
                cur = null;
            }
        }
        return cur;
    }

    private static Object resolveGlobalProperty(String key, StrategyContext context) {
        Object val = context.getProperty(key);
        if (val != null) {
            return val;
        }
        if (context.getRequest() != null && context.getRequest().getBizData() != null) {
            val = context.getRequest().getBizData().get(key);
            if (val != null) {
                return val;
            }
        }
        if ("userId".equalsIgnoreCase(key) || "user_id".equalsIgnoreCase(key)) {
            return context.getUserId();
        }
        // 兼容同义别名读取 (遵循真实数据，严禁隐式伪造假数据)
        if ("city".equalsIgnoreCase(key) || "arrivalCity".equalsIgnoreCase(key) || "arrival_city".equalsIgnoreCase(key)) {
            val = context.getProperty("arrivalCity");
            return val != null ? val : context.getProperty("city");
        }
        if ("airportCode".equalsIgnoreCase(key) || "arrivalAirport".equalsIgnoreCase(key) || "arrival_airport".equalsIgnoreCase(key)) {
            val = context.getProperty("arrivalAirport");
            return val != null ? val : context.getProperty("airportCode");
        }
        if ("flightNo".equalsIgnoreCase(key) || "flight_no".equalsIgnoreCase(key)) {
            val = context.getProperty("flightNo");
            if (val != null) {
                return val;
            }
            String target = context.getStrategyTarget();
            if (target != null) {
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("([A-Za-z]{2}\\d{3,4})").matcher(target);
                if (m.find()) {
                    return m.group(1);
                }
            }
        }
        return null;
    }

    private static boolean containsKeyIgnoreCase(Map<?, ?> map, String expectedKey) {
        if (map == null || expectedKey == null) return false;
        if (map.containsKey(expectedKey)) return true;
        for (Object keyObj : map.keySet()) {
            if (keyObj != null) {
                String k = keyObj.toString();
                if (k.equalsIgnoreCase(expectedKey)) return true;
                String normalizedK = k.replace("_", "").toLowerCase();
                String normalizedExpected = expectedKey.replace("_", "").toLowerCase();
                if (normalizedK.equals(normalizedExpected)) return true;
            }
        }
        return false;
    }

    private static Object getValueIgnoreCase(Map<?, ?> map, String expectedKey) {
        if (map == null || expectedKey == null) return null;
        if (map.containsKey(expectedKey)) return map.get(expectedKey);
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() != null) {
                String k = entry.getKey().toString();
                if (k.equalsIgnoreCase(expectedKey)) return entry.getValue();
                String normalizedK = k.replace("_", "").toLowerCase();
                String normalizedExpected = expectedKey.replace("_", "").toLowerCase();
                if (normalizedK.equals(normalizedExpected)) return entry.getValue();
            }
        }
        return null;
    }

    private static Object tryParseJson(String text) {
        if (StringUtils.isBlank(text)) return text;
        String trimmed = text.trim();
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            try {
                return JsonUtils.parseMap(trimmed);
            } catch (Exception ignored) {
            }
        }
        return text;
    }

    private static Map<String, Object> tryParseMap(String text) {
        try {
            return JsonUtils.parseMap(text);
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }

    private boolean isStopped(StrategyContext context) {
        if (context == null) return false;
        Object stoppedProp = context.getProperty("stopped");
        return Boolean.TRUE.equals(stoppedProp);
    }

    private void cancelFutures(Map<String, Future<?>> futures) {
        cancelFutures(futures, null);
    }

    private void cancelFutures(Map<String, Future<?>> futures, TraceCollector traceCollector) {
        long cancelStart = System.currentTimeMillis();
        for (Map.Entry<String, Future<?>> entry : futures.entrySet()) {
            String stepId = entry.getKey();
            Future<?> f = entry.getValue();
            if (f != null && !f.isDone()) {
                f.cancel(true);
                if (traceCollector != null) {
                    long ackLatency = System.currentTimeMillis() - cancelStart;
                    traceCollector.recordCancelAck(stepId, ackLatency);
                    traceCollector.recordSchedEvent(stepId, "unknown", "cancel", "cancelled", "已收到中断取消信号");
                }
            }
        }
        futures.clear();
    }

    private static void recordStepStartEvents(TraceCollector traceCollector, FlowDagStep s) {
        if (traceCollector != null) {
            traceCollector.recordSchedEvent(s.getId(), s.getAgent(), "ready", "running", "依赖就绪");
            traceCollector.recordSchedEvent(s.getId(), s.getAgent(), "start", "running", "提交线程池并发执行");
        }
    }

    private static void recordStepSuccessTrace(TraceCollector traceCollector, FlowDagStep completedStep,
                                               String agentName, DagOutcome o, StrategyContext context) {
        if (traceCollector == null) {
            return;
        }
        traceCollector.recordSchedEvent(o.getStepId(), agentName, "end", "success", "执行成功");

        Number lat = (Number) context.getProperty("_lastLatencyMs_" + agentName);
        Number tIn = (Number) context.getProperty("_lastTokensIn_" + agentName);
        Number tOut = (Number) context.getProperty("_lastTokensOut_" + agentName);

        ExecutionTraceDTO.NodeTrace nodeTrace = ExecutionTraceDTO.NodeTrace.builder()
                .nodeId(o.getStepId())
                .agent(agentName)
                .status("success")
                .required(completedStep != null && completedStep.getOnFailure() != OnFailureAction.SKIP)
                .attempt(o.getAttempts())
                .loopRound(context.getEvaluationRetryCount())
                .stitchedInput(o.getOutput() != null ? String.valueOf(o.getOutput()) : "")
                .output(o.getRawOutput())
                .latencyMs(lat != null ? lat.longValue() : 0L)
                .tokensIn(tIn != null ? tIn.intValue() : 0)
                .tokensOut(tOut != null ? tOut.intValue() : 0)
                .build();
        traceCollector.recordNodeExecution(nodeTrace);
    }

    private static void recordStepSkippedTrace(TraceCollector traceCollector, FlowDagStep step,
                                               DagOutcome o, StrategyContext context) {
        if (traceCollector == null) {
            return;
        }
        String agentName = step != null ? step.getAgent() : "";
        traceCollector.recordSchedEvent(o.getStepId(), agentName, "skip", "skipped", "非必要失败跳过: " + o.getError());

        ExecutionTraceDTO.NodeTrace nodeTrace = ExecutionTraceDTO.NodeTrace.builder()
                .nodeId(o.getStepId())
                .agent(agentName)
                .status("skipped")
                .required(false)
                .attempt(o.getAttempts())
                .loopRound(context.getEvaluationRetryCount())
                .output(o.getError())
                .latencyMs(0L)
                .tokensIn(0)
                .tokensOut(0)
                .build();
        traceCollector.recordNodeExecution(nodeTrace);
    }

    private static void recordStepFailedTrace(TraceCollector traceCollector, FlowDagStep step,
                                              DagOutcome o, StrategyContext context) {
        if (traceCollector == null) {
            return;
        }
        String agentName = step != null ? step.getAgent() : "";
        traceCollector.recordSchedEvent(o.getStepId(), agentName, "fail", "failed", o.getError());

        ExecutionTraceDTO.NodeTrace nodeTrace = ExecutionTraceDTO.NodeTrace.builder()
                .nodeId(o.getStepId())
                .agent(agentName)
                .status("failed")
                .required(true)
                .attempt(o.getAttempts())
                .loopRound(context.getEvaluationRetryCount())
                .output(o.getError())
                .latencyMs(0L)
                .tokensIn(0)
                .tokensOut(0)
                .build();
        traceCollector.recordNodeExecution(nodeTrace);
    }

    public void shutdown() {
        if (internalPool && !executorService.isShutdown()) {
            executorService.shutdownNow();
        }
    }
}
