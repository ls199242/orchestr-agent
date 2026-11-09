package com.shane.orchestragent.biz.eval.service;

import com.shane.orchestragent.biz.agent.Agent;
import com.shane.orchestragent.biz.agent.flow.CoordinatorAgent;
import com.shane.orchestragent.biz.agent.flow.EvaluatorAgent;
import com.shane.orchestragent.biz.agent.flow.PlannerAgent;
import com.shane.orchestragent.biz.agent.flow.ReporterAgent;
import com.shane.orchestragent.biz.context.StrategyContext;
import com.shane.orchestragent.biz.context.impl.DefaultStrategyContext;
import com.shane.orchestragent.biz.context.impl.StepStrategyContext;
import com.shane.orchestragent.biz.eval.client.EvalClient;
import com.shane.orchestragent.biz.eval.context.EvalCaseContext;
import com.shane.orchestragent.biz.eval.model.CaseMockBundleDTO;
import com.shane.orchestragent.biz.eval.model.EvalRunRequestDTO;
import com.shane.orchestragent.biz.eval.model.ExecutionTraceDTO;
import com.shane.orchestragent.biz.eval.trace.TraceRecorder;
import com.shane.orchestragent.biz.eval.trace.impl.TraceRecorderImpl;
import com.shane.orchestragent.biz.flow.StrategyFlow;
import com.shane.orchestragent.biz.flow.StrategyFlowFactory;
import com.shane.orchestragent.biz.model.agent.AgentResult;
import com.shane.orchestragent.biz.model.flow.CoordinatorResult;
import com.shane.orchestragent.biz.model.flow.EvaluatorResult;
import com.shane.orchestragent.biz.model.flow.PlanResult;
import com.shane.orchestragent.biz.model.request.RecommendRequestVO;
import com.shane.orchestragent.common.constant.PropertyKeys;
import com.shane.orchestragent.common.exception.BizErrorFactory;
import com.shane.orchestragent.common.exception.BizException;
import com.shane.orchestragent.common.utils.JsonUtils;
import com.shane.orchestragent.repository.AgentConfigRepository;
import com.shane.orchestragent.repository.StrategyConfigRepository;
import com.shane.orchestragent.repository.model.AgentConfigDO;
import com.shane.orchestragent.repository.model.StrategyConfigDO;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 评测运行与微执行核心业务调度服务
 * <p>
 * 职责：
 * <ul>
 *   <li>端到端用例评测调度：远程拉取 MockBundle、装配请求级隔离评测上下文、执行流程并产出强契约 Trace</li>
 *   <li>支持固定 DAG 模式 (PINNED_PLAN)，绕过 Planner 直接驱动 Supervisor/Expert</li>
 *   <li>支撑 M7 角色级微运行：单跑 Coordinator、Planner、Expert、Evaluator、Reporter</li>
 * </ul>
 *
 * @author Shane
 */
@Service
public class EvalRunnerService {

    private static final Logger log = LoggerFactory.getLogger(EvalRunnerService.class);

    private final EvalClient evalClient;
    private final StrategyFlowFactory strategyFlowFactory;
    private final StrategyConfigRepository strategyConfigRepository;
    private final AgentConfigRepository agentConfigRepository;
    private final TraceRecorder traceRecorder;

    public EvalRunnerService(EvalClient evalClient,
                             StrategyFlowFactory strategyFlowFactory,
                             StrategyConfigRepository strategyConfigRepository,
                             AgentConfigRepository agentConfigRepository,
                             TraceRecorder traceRecorder) {
        this.evalClient = evalClient;
        this.strategyFlowFactory = strategyFlowFactory;
        this.strategyConfigRepository = strategyConfigRepository;
        this.agentConfigRepository = agentConfigRepository;
        this.traceRecorder = traceRecorder != null ? traceRecorder : new TraceRecorderImpl(evalClient);
    }

    public EvalRunnerService(EvalClient evalClient,
                             StrategyFlowFactory strategyFlowFactory,
                             StrategyConfigRepository strategyConfigRepository,
                             AgentConfigRepository agentConfigRepository) {
        this(evalClient, strategyFlowFactory, strategyConfigRepository, agentConfigRepository, null);
    }

    /**
     * 执行完整用例端到端评测并同步产出结构化 Trace
     *
     * @param request 评测请求对象，包含 caseId、mode、覆盖选项与固定 DAG
     * @return 包含全链路调度事件、Token、耗时与原始报文的 ExecutionTraceDTO
     * @throws BizException 若用例不存在或请求非法
     */
    public ExecutionTraceDTO runCase(EvalRunRequestDTO request) throws BizException {
        if (request == null || StringUtils.isBlank(request.getCaseId())) {
            throw new BizException("REQUEST_INVALID", "评测请求 caseId 不能为空");
        }

        String caseId = request.getCaseId();
        String runId = StringUtils.defaultIfBlank(request.getRunId(), "run_" + System.currentTimeMillis());
        int repeatNo = request.getRepeatNo() > 0 ? request.getRepeatNo() : 1;
        String mode = StringUtils.defaultIfBlank(request.getMode(), "FREE");

        log.info("[EvalRunner] 开始执行评测用例: caseId={}, runId={}, repeatNo={}, mode={}", caseId, runId, repeatNo, mode);

        // 1. 从外部 eval 系统远程拉取用例运行包 (Mock 与故障配置)
        CaseMockBundleDTO bundle = evalClient.getCaseBundle(caseId, runId, repeatNo);

        // 2. 组装请求级隔离评测上下文 (含冻结时钟、次序游标、故障注入器与 TraceCollector)
        EvalCaseContext evalContext = new EvalCaseContext(caseId, runId, repeatNo, mode, bundle);

        // 3. 构建流程上下文
        StrategyConfigDO strategyConfig = null;
        if (StringUtils.isNotBlank(request.getStrategyCode())) {
            strategyConfig = strategyConfigRepository.getByCode(request.getStrategyCode());
            if (strategyConfig == null) {
                throw BizErrorFactory.getInstance().strategyNotFound(request.getStrategyCode());
            }
        }

        String flowId = "eval_flow_" + caseId + "_" + System.currentTimeMillis();
        String sessionId = (request.getSession() != null && StringUtils.isNotBlank(request.getSession().getSessionId()))
                ? request.getSession().getSessionId()
                : "eval_session_" + caseId;
        String userId = (request.getSession() != null && StringUtils.isNotBlank(request.getSession().getUserId()))
                ? request.getSession().getUserId()
                : (bundle.getSession() != null ? bundle.getSession().getUserId() : "U001");

        DefaultStrategyContext context = new DefaultStrategyContext(
                strategyConfig,
                flowId,
                sessionId,
                RecommendRequestVO.builder().flowId(flowId).sessionId(sessionId).code(strategyConfig != null ? strategyConfig.getCode() : null).build()
        );
        context.setUserId(userId);
        context.setEvalCaseContext(evalContext);
        context.setTimeProvider(evalContext.getTimeProvider());

        String target = StringUtils.isNotBlank(request.getMessage()) ? request.getMessage() : "航班推荐出行诉求";
        context.setProperties(PropertyKeys.KEY_STRATEGY_TARGET, target);

        // 注入固定 DAG (如为 PINNED_PLAN 模式，绕过 Planner 直接驱动 Supervisor 执行)
        if ("PINNED_PLAN".equalsIgnoreCase(mode) && request.getPinnedDag() != null) {
            context.setProperties("pinnedDag", request.getPinnedDag());
        }

        evalContext.getTraceCollector().setStrategyCode(strategyConfig != null ? strategyConfig.getCode() : "orchestration");

        // 4. 装配并执行工作流
        StrategyFlow flow;
        if (strategyConfig != null) {
            flow = strategyFlowFactory.createReActFlow(strategyConfig, context);
        } else {
            flow = strategyFlowFactory.createSessionOrchestrationFlow(context);
        }

        try {
            log.info("[EvalRunner] 启动流程实例执行: flowId={}", flowId);
            flow.execute();
            evalContext.getTraceCollector().setFinalStatus("completed");
        } catch (Exception e) {
            log.error("[EvalRunner] 评测流程执行异常中断: flowId={}, error={}", flowId, e.getMessage());
            evalContext.getTraceCollector().setFinalStatus(e.getMessage() != null && e.getMessage().contains("超时") ? "timeout" : "failed");
        }

        // 5. 产出强契约结构化 Trace
        ExecutionTraceDTO trace = evalContext.getTraceCollector().buildTrace();
        log.info("[EvalRunner] 评测执行完毕: caseId={}, status={}, latency={}ms, totalTokens={}",
                caseId, trace.getFinalStatus(), trace.getTotals().getTotalLatencyMs(), trace.getTotals().getTotalTokens());

        // 6. 异步触发接口调用插入 Trace 记录
        if (evalContext.markReported()) {
            traceRecorder.recordTraceAsync(trace);
        }

        return trace;
    }

    /**
     * M7 角色级微运行：单跑 Coordinator 意图识别
     *
     * @param message 用户输入消息
     * @param caseId  关联用例标识 (用于拉取环境时钟与 Mock)
     * @return 包含决策动作、候选策略、提取参数与耗时的字典
     * @throws BizException 若执行异常
     */
    public Map<String, Object> runCoordinator(String message, String caseId) throws BizException {
        EvalCaseContext evalContext = initIsolatedContext(caseId, "COORDINATOR_ONLY");
        StrategyContext context = new DefaultStrategyContext();
        context.setEvalCaseContext(evalContext);
        context.setTimeProvider(evalContext.getTimeProvider());
        context.setProperties(PropertyKeys.KEY_STRATEGY_TARGET, message);

        CoordinatorAgent agent = (CoordinatorAgent) strategyFlowFactory.createSessionOrchestrationFlow(context).getContext().getAgents().get("COORDINATOR");
        if (agent == null) {
            AgentConfigDO cfg = agentConfigRepository.getByCode("COORDINATOR");
            if (cfg == null) {
                throw new BizException("AGENT_NOT_FOUND", "未找到 COORDINATOR 配置");
            }
        }

        long start = System.currentTimeMillis();
        AgentResult agentResult = agent.execute(context);
        long lat = Math.max(0, System.currentTimeMillis() - start);

        CoordinatorResult result = JsonUtils.parseObject(agentResult.getOutput(), CoordinatorResult.class);
        Map<String, Object> resp = new LinkedHashMap<>();
        if (result != null) {
            resp.put("action", result.getAction());
            resp.put("strategyCode", result.getStrategyCode());
            resp.put("params", result.getParams());
            resp.put("reply", result.getReply());
        } else {
            resp.put("rawOutput", agentResult.getOutput());
        }
        resp.put("latencyMs", lat);
        return resp;
    }

    /**
     * M7 角色级微运行：单跑 Planner 战略任务规划 (支持循环多次测定 DAG 变异度与合法率)
     *
     * @param strategyCode 策略编码
     * @param target       用户目标诉求
     * @param caseId       关联用例标识
     * @return 包含生成的 DAG 结构与耗时的字典
     * @throws BizException 若策略不存在或规划失败
     */
    public Map<String, Object> runPlanner(String strategyCode, String target, String caseId) throws BizException {
        StrategyConfigDO strategyConfig = strategyConfigRepository.getByCode(strategyCode);
        if (strategyConfig == null) {
            throw BizErrorFactory.getInstance().strategyNotFound(strategyCode);
        }
        EvalCaseContext evalContext = initIsolatedContext(caseId, "PLANNER_ONLY");
        DefaultStrategyContext context = new DefaultStrategyContext(strategyConfig, "planner_run", "sess_01", null);
        context.setEvalCaseContext(evalContext);
        context.setTimeProvider(evalContext.getTimeProvider());
        context.setProperties(PropertyKeys.KEY_STRATEGY_TARGET, target);

        // 创建临时流以初始化智能体团队
        StrategyFlow flow = strategyFlowFactory.createReActFlow(strategyConfig, context);
        PlannerAgent planner = (PlannerAgent) flow.getContext().getAgents().get("PLANNER");

        long start = System.currentTimeMillis();
        AgentResult agentResult = planner.execute(context);
        long lat = Math.max(0, System.currentTimeMillis() - start);

        PlanResult plan = JsonUtils.parseObject(agentResult.getOutput(), PlanResult.class);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("plan", plan != null ? plan : agentResult.getOutput());
        resp.put("latencyMs", lat);
        return resp;
    }

    /**
     * M7 角色级微运行：单跑专精 Expert 专家
     *
     * @param expertCode    Expert 业务编码 (如 "flight_recommend_expert")
     * @param stitchedInput 装配完成的上游输入 (JSON 字符串)
     * @param caseId        关联用例标识 (供底层 Tool 消费对应 Mock 数据)
     * @return 包含 Expert 输出与耗时的字典
     * @throws BizException 若 Expert 不存在或执行异常
     */
    public Map<String, Object> runExpert(String expertCode, String stitchedInput, String caseId) throws BizException {
        AgentConfigDO agentConfig = agentConfigRepository.getByCode(expertCode);
        if (agentConfig == null) {
            throw BizErrorFactory.getInstance().agentNotFound(expertCode);
        }
        EvalCaseContext evalContext = initIsolatedContext(caseId, "EXPERT_ONLY");
        DefaultStrategyContext context = new DefaultStrategyContext();
        context.setEvalCaseContext(evalContext);
        context.setTimeProvider(evalContext.getTimeProvider());
        context.setProperties(PropertyKeys.KEY_STRATEGY_TARGET, stitchedInput);

        Map<String, Object> req = JsonUtils.parseMap(stitchedInput);
        StepStrategyContext stepCtx = new StepStrategyContext(context, req != null ? req : Collections.emptyMap(), null);

        Agent expert = context.getAgents().get(expertCode);
        if (expert == null) {
            StrategyConfigDO dummy = StrategyConfigDO.builder().code("dummy").expertCodes(List.of(expertCode)).build();
            StrategyFlow flow = strategyFlowFactory.createReActFlow(dummy, context);
            expert = flow.getContext().getAgents().get(expertCode);
        }
        if (expert == null) {
            throw BizErrorFactory.getInstance().agentNotFound(expertCode);
        }

        long start = System.currentTimeMillis();
        AgentResult result = expert.execute(stepCtx);
        long lat = Math.max(0, System.currentTimeMillis() - start);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("output", result.getOutput());
        resp.put("latencyMs", lat);
        return resp;
    }

    /**
     * M7 角色级微运行：单跑 Evaluator 质检验收
     *
     * @param strategyCode 策略编码
     * @param summary      前序节点执行成果汇总文案
     * @param caseId       关联用例标识
     * @return 质检结果字典
     * @throws BizException 若执行异常
     */
    public Map<String, Object> runEvaluator(String strategyCode, String summary, String caseId) throws BizException {
        StrategyConfigDO strategyConfig = strategyConfigRepository.getByCode(strategyCode);
        EvalCaseContext evalContext = initIsolatedContext(caseId, "EVALUATOR_ONLY");
        DefaultStrategyContext context = new DefaultStrategyContext(strategyConfig, "eval_run", "sess_01", null);
        context.setEvalCaseContext(evalContext);
        context.setTimeProvider(evalContext.getTimeProvider());
        context.setProperties(PropertyKeys.KEY_STRATEGY_TARGET, summary);

        StrategyFlow flow = strategyFlowFactory.createReActFlow(strategyConfig != null ? strategyConfig : StrategyConfigDO.builder().code("d").build(), context);
        EvaluatorAgent evaluator = (EvaluatorAgent) flow.getContext().getAgents().get("EVALUATOR");
        if (evaluator == null) {
            throw BizErrorFactory.getInstance().agentNotFound("EVALUATOR");
        }

        long start = System.currentTimeMillis();
        AgentResult agentResult = evaluator.execute(context);
        long lat = Math.max(0, System.currentTimeMillis() - start);

        EvaluatorResult result = JsonUtils.parseObject(agentResult.getOutput(), EvaluatorResult.class);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("evaluatorResult", result != null ? result : agentResult.getOutput());
        resp.put("latencyMs", lat);
        return resp;
    }

    /**
     * M7 角色级微运行：单跑 Reporter 交付成果汇报
     *
     * @param strategyCode 策略编码
     * @param summary      全流程节点执行产出与质检说明汇总
     * @param caseId       关联用例标识
     * @return 交付文案与耗时字典
     * @throws BizException 若执行异常
     */
    public Map<String, Object> runReporter(String strategyCode, String summary, String caseId) throws BizException {
        StrategyConfigDO strategyConfig = strategyConfigRepository.getByCode(strategyCode);
        EvalCaseContext evalContext = initIsolatedContext(caseId, "REPORTER_ONLY");
        DefaultStrategyContext context = new DefaultStrategyContext(strategyConfig, "reporter_run", "sess_01", null);
        context.setEvalCaseContext(evalContext);
        context.setTimeProvider(evalContext.getTimeProvider());
        context.setProperties(PropertyKeys.KEY_STRATEGY_TARGET, summary);

        StrategyFlow flow = strategyFlowFactory.createReActFlow(strategyConfig != null ? strategyConfig : StrategyConfigDO.builder().code("d").build(), context);
        ReporterAgent reporter = (ReporterAgent) flow.getContext().getAgents().get("REPORTER");
        if (reporter == null) {
            throw BizErrorFactory.getInstance().agentNotFound("REPORTER");
        }

        long start = System.currentTimeMillis();
        String output = (String) reporter.execute(context).getOutput();
        long lat = Math.max(0, System.currentTimeMillis() - start);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("finalAnswer", output);
        resp.put("latencyMs", lat);
        return resp;
    }

    /**
     * 初始化单节点微运行专用的轻量隔离上下文
     */
    private EvalCaseContext initIsolatedContext(String caseId, String mode) {
        CaseMockBundleDTO bundle = null;
        if (StringUtils.isNotBlank(caseId)) {
            try {
                bundle = evalClient.getCaseBundle(caseId, null, null);
            } catch (Exception e) {
                log.warn("[EvalRunner] 单节点微运行未拉取到用例包: caseId={}, error={}", caseId, e.getMessage());
            }
        }
        return new EvalCaseContext(caseId != null ? caseId : "isolated", "single_run", 1, mode, bundle);
    }
}
