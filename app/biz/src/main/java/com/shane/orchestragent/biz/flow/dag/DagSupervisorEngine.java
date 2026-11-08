package com.shane.orchestragent.biz.flow.dag;

import com.shane.orchestragent.biz.agent.Agent;
import com.shane.orchestragent.biz.agent.flow.RagAgent;
import com.shane.orchestragent.biz.agent.flow.ToolAgent;
import com.shane.orchestragent.biz.context.StrategyContext;
import com.shane.orchestragent.biz.context.impl.StepStrategyContext;
import com.shane.orchestragent.biz.model.agent.AgentResult;
import com.shane.orchestragent.biz.model.agent.ExpertErrorAgentResult;
import com.shane.orchestragent.biz.model.flow.*;
import com.shane.orchestragent.common.constant.PropertyKeys;
import com.shane.orchestragent.common.enums.AgentTypeEnum;
import com.shane.orchestragent.common.exception.BizErrorFactory;
import com.shane.orchestragent.common.exception.BizException;
import com.shane.orchestragent.common.exception.FlowStoppedException;
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
 *   <li>基于数据依赖与显式依赖的动态并行任务提交</li>
 *   <li>基于表达式 ($global.x / $s1.output.y) 的纯机械参数搬运 (零模型幻觉)</li>
 *   <li>单步超时控制、失败重试与契约校验 (outputKeys)</li>
 *   <li>基于传递闭包 (Closure) 的精准局部重跑与结果复用自愈闭环</li>
 * </ul>
 *
 * @author Shane
 */
public class DagSupervisorEngine {

    private static final Logger log = LoggerFactory.getLogger(DagSupervisorEngine.class);

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
            this.executorService = Executors.newCachedThreadPool(r -> {
                Thread t = new Thread(r, "dag-supervisor-worker-" + UUID.randomUUID().toString().substring(0, 6));
                t.setDaemon(true);
                return t;
            });
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

    /**
     * 核心调度主循环
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

        while (true) {
            if (isStopped(context) || executorService.isShutdown()) {
                cancelFutures(activeFutures);
                throw BizErrorFactory.getInstance().strategyFlowStopped();
            }

            // 1. 扫描所有依赖已满足的 PENDING 步骤，全部并发提交
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
                            cancelFutures(activeFutures);
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
                cancelFutures(activeFutures);
                throw BizErrorFactory.getInstance().strategyFlowStopped();
            }

            running--;
            state.getAttempts().merge(o.getStepId(), o.getAttempts(), Integer::sum);

            if (o.getError() == null) {
                if (o.getOutput() != null) {
                    state.getOutputs().put(o.getStepId(), o.getOutput());
                }
                if (o.getRawOutput() != null) {
                    state.getRawOutputs().put(o.getStepId(), o.getRawOutput());
                }
                state.getStatus().put(o.getStepId(), DagStepStatus.SUCCEEDED);
                FlowDagStep completedStep = byId.get(o.getStepId());
                String agentName = completedStep != null ? completedStep.getAgent() : o.getStepId();
                log.info("[Flow: {}][SUPERVISOR] 步骤 [{}] ({}) 执行完成 (尝试次数: {})",
                        flowId, o.getStepId(), agentName, o.getAttempts());
            } else {
                state.getErrors().put(o.getStepId(), o.getError());
                FlowDagStep step = byId.get(o.getStepId());
                if (step != null && step.getOnFailure() == OnFailureAction.SKIP) {
                    state.getStatus().put(o.getStepId(), DagStepStatus.SKIPPED);
                    log.warn("[Flow: {}][SUPERVISOR] 步骤 [{}] ({}) 失败但策略为 SKIP，已跳过: {}",
                            flowId, o.getStepId(), step.getAgent(), o.getError());
                } else {
                    state.getStatus().put(o.getStepId(), DagStepStatus.FAILED);
                    aborted = true;
                    abortReason = o.getError();
                    cancelFutures(activeFutures);
                    log.error("[Flow: {}][SUPERVISOR] 步骤 [{}] ({}) 失败导致 DAG 中止: {}",
                            flowId, o.getStepId(), step != null ? step.getAgent() : "", o.getError());
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

    /**
     * 单个步骤的执行管线：参数绑定 -> 伴生 RAG 前置 -> 超时与重试控制 -> 契约校验
     */
    private DagOutcome runStep(FlowDagStep step, DagExecutionState st, StrategyContext context,
                               String stepFeedback, BiConsumer<Agent, AgentResult> stepCallback) {
        Agent agent = context.getAgents().get(step.getAgent());
        if (agent == null) {
            return new DagOutcome(step.getId(), null, null, "目标 Agent 未注册: " + step.getAgent(), 0);
        }

        // 1. 机械化参数求值装配
        Map<String, Object> resolvedInputs = new LinkedHashMap<>();
        if (step.getInputs() != null) {
            step.getInputs().forEach((k, expr) -> {
                Object val = resolveExpr(expr, st, context);
                if (val != null) {
                    resolvedInputs.put(k, val);
                }
            });
        }

        // 2. 线程隔离的局部上下文
        StepStrategyContext stepContext = new StepStrategyContext(context, resolvedInputs, stepFeedback);

        // 3. 专精 Expert 伴生 RAG 隐式前置加载
        if (agent.getType() == AgentTypeEnum.EXPERT) {
            String companionRagName = RagAgent.buildRagName(step.getAgent());
            Agent companionRag = context.getAgents().get(companionRagName);
            if (companionRag != null) {
                try {
                    log.info("[Flow: {}][DAG_ENGINE][RAG] 正在前置检索 Expert [{}] 的伴生知识库 [{}]...", context.getFlowId(), step.getAgent(), companionRagName);
                    companionRag.execute(stepContext);
                } catch (Exception e) {
                    log.warn("[Flow: {}][DAG_ENGINE][RAG] 伴生 RAG 检索异常 (非致命): {}", context.getFlowId(), e.getMessage());
                }
            }
        }

        int maxRetries = Math.max(0, step.getMaxRetries());
        long timeoutMs = step.getTimeoutMs() > 0 ? step.getTimeoutMs() : 30000L;
        String lastError = null;
        int attempt = 0;

        while (attempt <= maxRetries) {
            attempt++;
            final int curAttempt = attempt;
            final String currentError = lastError;
            if (currentError != null) {
                stepContext.setProperties("lastError", currentError);
            }

            log.info("[Flow: {}][SUPERVISOR][STEP {}] 启动步骤 ({}) 第 {}/{} 次调用，入参装配: {}",
                    context.getFlowId(), step.getId(), step.getAgent(), curAttempt, maxRetries + 1,
                    JsonUtils.toJsonString(resolvedInputs));

            Future<AgentResult> future = executorService.submit(() -> agent.execute(stepContext));
            try {
                AgentResult result = future.get(timeoutMs, TimeUnit.MILLISECONDS);
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

                // 输出契约校验
                if (CollectionUtils.isNotEmpty(step.getOutputKeys())) {
                    if (agent.getType() == AgentTypeEnum.EXPERT) {
                        // Expert 专精节点产出为自然语言/Markdown 推荐内容，放行强校验
                        log.info("[Flow: {}][DAG_ENGINE] 专精 Expert [{}] 产出为自然语言文本，放行结构化契约校验", context.getFlowId(), step.getAgent());
                    } else {
                        if (!(structuredOutput instanceof Map<?, ?> outMap)) {
                            lastError = "产出并非 JSON 对象，无法满足契约校验: " + step.getOutputKeys();
                            continue;
                        }
                        List<String> missingKeys = step.getOutputKeys().stream()
                                .filter(k -> !containsKeyIgnoreCase(outMap, k)).toList();
                        if (!missingKeys.isEmpty()) {
                            lastError = "产出缺少必要契约字段: " + missingKeys;
                            continue;
                        }
                    }
                }

                if (stepCallback != null) {
                    try {
                        String detail;
                        if (agent.getType() == AgentTypeEnum.TOOL) {
                            detail = "【入参 requestBody】:\n" + JsonUtils.toJsonString(resolvedInputs) + "\n\n【响应 responseBody】:\n" + rawOutput;
                        } else {
                            detail = rawOutput;
                        }
                        stepCallback.accept(agent, new AgentResult(detail, null));
                    } catch (Exception e) {
                        log.warn("[Flow: {}][DAG_ENGINE] 步骤完成回调执行异常: {}", context.getFlowId(), e.getMessage());
                    }
                }

                log.info("[Flow: {}][SUPERVISOR][STEP {}] 节点 [{}] 执行完成，产出摘要: {}",
                        context.getFlowId(), step.getId(), step.getAgent(), StringUtils.abbreviate(rawOutput, 200));

                return new DagOutcome(step.getId(), structuredOutput, rawOutput, null, curAttempt);
            } catch (TimeoutException e) {
                future.cancel(true);
                lastError = "执行超时 (" + timeoutMs + "ms)";
            } catch (ExecutionException e) {
                lastError = "执行异常: " + (e.getCause() != null ? e.getCause().getMessage() : e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                future.cancel(true);
                lastError = "被中断";
                break;
            }
        }

        return new DagOutcome(step.getId(), null, null, lastError, attempt);
    }

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
            cur = context.getProperty(key);
            if (cur == null && context.getRequest() != null && context.getRequest().getBizData() != null) {
                cur = context.getRequest().getBizData().get(key);
            }
            if (cur == null && ("userId".equalsIgnoreCase(key) || "user_id".equalsIgnoreCase(key))) {
                cur = context.getUserId();
            }
            // 常用全局参数智能别名与业务目标提取降级
            if (cur == null) {
                if ("city".equalsIgnoreCase(key) || "arrivalCity".equalsIgnoreCase(key) || "arrival_city".equalsIgnoreCase(key)) {
                    cur = context.getProperty("arrivalCity");
                    if (cur == null) cur = context.getProperty("city");
                    if (cur == null) cur = "上海";
                } else if ("airportCode".equalsIgnoreCase(key) || "arrivalAirport".equalsIgnoreCase(key) || "arrival_airport".equalsIgnoreCase(key)) {
                    cur = context.getProperty("arrivalAirport");
                    if (cur == null) cur = context.getProperty("airportCode");
                    if (cur == null) cur = "SHA";
                } else if ("flightNo".equalsIgnoreCase(key) || "flight_no".equalsIgnoreCase(key)) {
                    cur = context.getProperty("flightNo");
                    if (cur == null) {
                        String target = context.getStrategyTarget();
                        if (target != null) {
                            java.util.regex.Matcher m = java.util.regex.Pattern.compile("([A-Za-z]{2}\\d{3,4})").matcher(target);
                            if (m.find()) {
                                cur = m.group(1);
                            }
                        }
                    }
                    if (cur == null) cur = "MU5101";
                }
            }
            idx = 2;
        } else {
            String stepId = parts[0];
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
        for (Future<?> f : futures.values()) {
            if (f != null && !f.isDone()) {
                f.cancel(true);
            }
        }
        futures.clear();
    }

    public void shutdown() {
        if (internalPool) {
            executorService.shutdownNow();
        }
    }
}
