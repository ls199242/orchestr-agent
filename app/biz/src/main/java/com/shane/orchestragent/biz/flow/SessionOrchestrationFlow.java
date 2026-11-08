package com.shane.orchestragent.biz.flow;

import com.shane.orchestragent.biz.agent.flow.CoordinatorAgent;
import com.shane.orchestragent.biz.context.StrategyContext;
import com.shane.orchestragent.biz.context.impl.DefaultStrategyContext;
import com.shane.orchestragent.biz.model.enums.CoordinatorActionEnum;
import com.shane.orchestragent.biz.model.enums.FlowTopologyTypeEnum;
import com.shane.orchestragent.biz.model.flow.CoordinatorResult;
import com.shane.orchestragent.common.constant.PropertyKeys;
import com.shane.orchestragent.common.exception.BizErrorFactory;
import com.shane.orchestragent.common.exception.BizException;
import com.shane.orchestragent.memory.model.ConversationCacheUnit;
import com.shane.orchestragent.memory.model.MemoryQueryCriteria;
import com.shane.orchestragent.memory.session.SessionPendingState;
import com.shane.orchestragent.memory.session.SessionStateStore;
import com.shane.orchestragent.repository.StrategyConfigRepository;
import com.shane.orchestragent.repository.model.StrategyConfigDO;
import com.shane.orchestragent.repository.model.StrategyParamDO;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 多轮会话编排流程 (会话级意图识别前置编排)
 * <p>
 * 职责：
 * <ul>
 *   <li>每轮注入会话历史、候选策略目录 (code/name/description/params 裁剪视图) 与跨轮待定收集状态</li>
 *   <li>调度 CoordinatorAgent 进行意图决策：直答 (DIRECT_REPLY) / 追问收参 (COLLECT) / 触发策略 (EXECUTE)</li>
 *   <li>以策略入参定义 (StrategyParamDO) 为单一事实来源做必填参数完整性终审，缺参强制降级为追问</li>
 *   <li>必填齐备后按目标策略拓扑创建委托策略流，透传监听器并内联执行，实现"意图选策略 + 收参后触发"</li>
 * </ul>
 *
 * @author Shane
 */
public class SessionOrchestrationFlow extends BaseStrategyFlow {

    /** 系统级参数名: 用户标识 (由编排层自动注入，严禁向终端用户追问) */
    private static final String SYSTEM_PARAM_USER_ID = "userId";

    private final CoordinatorAgent coordinatorAgent;
    private final StrategyConfigRepository strategyConfigRepository;
    private final SessionStateStore sessionStateStore;
    private final StrategyFlowFactory strategyFlowFactory;

    public SessionOrchestrationFlow(StrategyContext context,
                                    CoordinatorAgent coordinatorAgent,
                                    StrategyConfigRepository strategyConfigRepository,
                                    SessionStateStore sessionStateStore,
                                    StrategyFlowFactory strategyFlowFactory,
                                    int maxStep) {
        super(context, maxStep);
        this.coordinatorAgent = coordinatorAgent;
        this.strategyConfigRepository = strategyConfigRepository;
        this.sessionStateStore = sessionStateStore;
        this.strategyFlowFactory = strategyFlowFactory;
        if (this.coordinatorAgent != null) {
            addAgent(this.coordinatorAgent);
        }
    }

    @Override
    protected String doExecute() throws BizException {
        // 1. 读取并注入历史会话上下文
        loadChatHistory();

        // 2. 注入候选策略目录与跨轮待定收集状态
        injectOrchestrationProperties();

        // 3. 意图识别决策
        CoordinatorResult result = (CoordinatorResult) executeStep(coordinatorAgent, CoordinatorResult.class);
        if (result == null || result.getAction() == null) {
            throw BizErrorFactory.getInstance().coordinatorDecisionEmpty();
        }
        log.info("[Flow: {}][FLOW_COORDINATOR] 意图决策完成: action={}, strategyCode={}, params={}",
                getFlowId(), result.getAction(), result.getStrategyCode(), result.getParams());

        // 4. 分发执行
        return switch (result.getAction()) {
            case DIRECT_REPLY -> directReply(result);
            case COLLECT, EXECUTE -> handleStrategyDecision(result);
        };
    }

    /**
     * 直答分支: 日常闲聊或单轮知识问答
     */
    private String directReply(CoordinatorResult result) throws BizException {
        String reply = result.getReply();
        if (StringUtils.isBlank(reply)) {
            throw BizErrorFactory.getInstance().coordinatorReplyEmpty();
        }
        getContext().setRecommendResult(reply);
        getContext().setProperties(PropertyKeys.KEY_RECOMMEND_RESULT, reply);
        log.info("[Flow: {}][FLOW_COORDINATOR] 判定为日常闲聊或简单查询，执行直接答复 (待定收集状态保留)", getFlowId());
        return reply;
    }

    /**
     * 策略决策统一处理: 参数合并 -> 系统级参数自动注入 -> 必填完整性终审
     * 齐备则触发策略执行，缺参则落盘待定状态并追问 (即使 LLM 误判 EXECUTE 也强制降级)
     */
    private String handleStrategyDecision(CoordinatorResult result) throws BizException {
        StrategyConfigDO strategyConfig = resolveTargetStrategy(result);
        Map<String, Object> mergedParams = mergeParams(strategyConfig, result);
        autoFillSystemParams(strategyConfig, mergedParams);
        List<StrategyParamDO> missingParams = resolveMissingRequired(strategyConfig, mergedParams);

        if (missingParams.isEmpty()) {
            // 完整性终审以代码为准: 参数齐备时无论 LLM 判定 COLLECT 还是 EXECUTE，一律触发策略执行
            return executeStrategy(strategyConfig, mergedParams);
        }
        if (result.getAction() == CoordinatorActionEnum.EXECUTE) {
            log.warn("[Flow: {}][FLOW_COORDINATOR] 意图判定 EXECUTE 但必填参数未齐备 ({})，强制降级为参数收集",
                    getFlowId(), missingParams.stream().map(StrategyParamDO::getName).collect(Collectors.joining(",")));
        }
        return collectParams(strategyConfig, result, mergedParams, missingParams);
    }

    /**
     * 追问收参分支: 落盘跨轮待定状态并生成追问话术
     */
    private String collectParams(StrategyConfigDO strategyConfig, CoordinatorResult result,
                                 Map<String, Object> mergedParams, List<StrategyParamDO> missingParams) throws BizException {
        if (sessionStateStore != null) {
            sessionStateStore.save(getContext().getSessionId(), SessionPendingState.builder()
                    .strategyCode(strategyConfig.getCode())
                    .collectedParams(mergedParams)
                    .updatedAt(System.currentTimeMillis())
                    .build());
        }
        String reply = StringUtils.isNotBlank(result.getReply())
                ? result.getReply()
                : buildCollectReply(strategyConfig, missingParams);
        getContext().setRecommendResult(reply);
        getContext().setProperties(PropertyKeys.KEY_RECOMMEND_RESULT, reply);
        log.info("[Flow: {}][FLOW_COORDINATOR] 目标策略 [{}] 参数收集中: 已收集={}, 缺失={}",
                getFlowId(), strategyConfig.getCode(), mergedParams.keySet(),
                missingParams.stream().map(StrategyParamDO::getName).collect(Collectors.joining(",")));
        return reply;
    }

    /**
     * 触发策略执行分支: 构建委托策略流 (同一 flowId) 并内联执行
     */
    private String executeStrategy(StrategyConfigDO strategyConfig, Map<String, Object> mergedParams) throws BizException {
        // 参数齐备即触发，清除跨轮待定状态
        if (sessionStateStore != null) {
            sessionStateStore.clear(getContext().getSessionId());
        }

        // 构建委托上下文: 沿用 flowId/sessionId/request 与用户目标，策略入参逐键平铺 (占位符自然渲染)
        DefaultStrategyContext delegateContext = new DefaultStrategyContext(strategyConfig, getContext().getFlowId(),
                getContext().getSessionId(), getContext().getRequest());
        delegateContext.setUserId(getContext().getUserId());
        delegateContext.setMemoryContext(getContext().getMemoryContext());
        delegateContext.setProperties(PropertyKeys.KEY_STRATEGY_TARGET, getContext().getStrategyTarget());
        mergedParams.forEach(delegateContext::setProperties);
        delegateContext.setProperties(PropertyKeys.KEY_COLLECTED_PARAMS, mergedParams);

        // 按目标策略拓扑创建委托流程并透传全部监听器 (SSE 事件与生命周期延续同一条链路)
        StrategyFlow delegate = FlowTopologyTypeEnum.of(strategyConfig.getFlowTopologyType()) == FlowTopologyTypeEnum.MULTIPLE_CHAT
                ? strategyFlowFactory.createMultipleChatFlow(strategyConfig, delegateContext)
                : strategyFlowFactory.createReActFlow(strategyConfig, delegateContext);
        transferListeners(delegate);
        log.info("[Flow: {}][FLOW_COORDINATOR] 必填参数齐备，触发目标策略流 [{}] (拓扑: {}, 入参: {})",
                getFlowId(), strategyConfig.getCode(), strategyConfig.getFlowTopologyType(), mergedParams.keySet());

        return ((BaseStrategyFlow) delegate).doExecute();
    }

    /**
     * 解析本轮目标策略: 优先取意图决策结果，次优沿用会话待定策略；不在候选目录即 Fail-Fast (严禁默认兜底)
     */
    private StrategyConfigDO resolveTargetStrategy(CoordinatorResult result) throws BizException {
        String strategyCode = StringUtils.isNotBlank(result.getStrategyCode())
                ? result.getStrategyCode()
                : pendingState() != null ? pendingState().getStrategyCode() : null;
        if (StringUtils.isBlank(strategyCode)) {
            throw BizErrorFactory.getInstance().coordinatorDecisionEmpty();
        }
        StrategyConfigDO config = strategyConfigRepository != null
                ? strategyConfigRepository.getByCode(strategyCode)
                : null;
        if (config == null) {
            throw BizErrorFactory.getInstance().coordinatorStrategyUnknown(strategyCode);
        }
        return config;
    }

    /**
     * 参数合并: 跨轮已收集参数 ⊕ 本轮抽取参数 (本轮新值覆盖旧值)；切换目标策略时以待定状态中同策略的收集为基准
     */
    private Map<String, Object> mergeParams(StrategyConfigDO strategyConfig, CoordinatorResult result) {
        Map<String, Object> merged = new LinkedHashMap<>();
        SessionPendingState pending = pendingState();
        if (pending != null && strategyConfig.getCode().equals(pending.getStrategyCode())
                && pending.getCollectedParams() != null) {
            merged.putAll(pending.getCollectedParams());
        }
        if (result.getParams() != null) {
            result.getParams().forEach((name, value) -> {
                if (StringUtils.isNotBlank(name) && value != null && StringUtils.isNotBlank(String.valueOf(value))) {
                    merged.put(name, value);
                }
            });
        }
        return merged;
    }

    /**
     * 系统级参数自动注入 (如 userId): 从请求上下文获取，取不到且声明必填时 Fail-Fast，严禁向终端用户追问
     */
    private void autoFillSystemParams(StrategyConfigDO strategyConfig, Map<String, Object> mergedParams) throws BizException {
        if (CollectionUtils.isEmpty(strategyConfig.getParams())) {
            return;
        }
        for (StrategyParamDO param : strategyConfig.getParams()) {
            if (!SYSTEM_PARAM_USER_ID.equals(param.getName()) || mergedParams.get(param.getName()) != null) {
                continue;
            }
            if (StringUtils.isNotBlank(getContext().getUserId())) {
                mergedParams.put(param.getName(), getContext().getUserId());
            } else if (param.isRequired()) {
                throw BizErrorFactory.getInstance().strategyParamMissing(
                        SYSTEM_PARAM_USER_ID + " (请求上下文未提供用户标识，系统级参数不得向终端用户追问)");
            }
        }
    }

    /**
     * 必填参数完整性终审: 依据策略入参定义计算缺失项
     */
    private List<StrategyParamDO> resolveMissingRequired(StrategyConfigDO strategyConfig, Map<String, Object> mergedParams) {
        if (CollectionUtils.isEmpty(strategyConfig.getParams())) {
            return Collections.emptyList();
        }
        return strategyConfig.getParams().stream()
                .filter(StrategyParamDO::isRequired)
                .filter(param -> mergedParams.get(param.getName()) == null
                        || StringUtils.isBlank(String.valueOf(mergedParams.get(param.getName()))))
                .toList();
    }

    /**
     * 确定性追问话术兜底 (意图决策 reply 为空时使用)
     */
    private String buildCollectReply(StrategyConfigDO strategyConfig, List<StrategyParamDO> missingParams) {
        String items = missingParams.stream()
                .map(param -> param.getName()
                        + (StringUtils.isNotBlank(param.getDescription()) ? "（" + param.getDescription() + "）" : ""))
                .collect(Collectors.joining("、"));
        return "为了执行【" + strategyConfig.getName() + "】，请补充以下信息: " + items;
    }

    private SessionPendingState pendingState() {
        return sessionStateStore != null ? sessionStateStore.get(getContext().getSessionId()) : null;
    }

    /**
     * 注入候选策略目录与跨轮待定收集状态 (TemplateRender 对非字符串变量自动 JSON 序列化)
     */
    private void injectOrchestrationProperties() {
        List<Map<String, Object>> catalog = new ArrayList<>();
        if (strategyConfigRepository != null && strategyConfigRepository.getMap() != null) {
            for (StrategyConfigDO config : strategyConfigRepository.getMap().values()) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("code", config.getCode());
                item.put("name", config.getName());
                item.put("description", config.getDescription());
                item.put("params", buildCatalogParams(config));
                catalog.add(item);
            }
        }
        getContext().setProperties(PropertyKeys.KEY_STRATEGY_CATALOG, catalog);

        SessionPendingState pending = pendingState();
        getContext().setProperties(PropertyKeys.KEY_PENDING_STRATEGY,
                pending != null ? pending.getStrategyCode() : "");
        getContext().setProperties(PropertyKeys.KEY_COLLECTED_PARAMS,
                pending != null && pending.getCollectedParams() != null ? pending.getCollectedParams() : Map.of());
    }

    /**
     * 构建策略入参定义的目录裁剪视图
     */
    private List<Map<String, Object>> buildCatalogParams(StrategyConfigDO config) {
        if (CollectionUtils.isEmpty(config.getParams())) {
            return Collections.emptyList();
        }
        List<Map<String, Object>> items = new ArrayList<>();
        for (StrategyParamDO param : config.getParams()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", param.getName());
            item.put("type", param.getType());
            item.put("description", param.getDescription());
            item.put("required", param.isRequired());
            items.add(item);
        }
        return items;
    }

    /**
     * 透传外层流程已注册的全部监听器至委托流程，保证流式增量与终态事件延续推送
     */
    private void transferListeners(StrategyFlow delegate) {
        streamListeners.forEach(delegate::addStreamListener);
        stateChangeListeners.forEach(delegate::addStateChangeListener);
        agentEventListeners.forEach(delegate::addAgentEventListener);
    }

    /**
     * 读取并注入历史会话上下文
     */
    private void loadChatHistory() {
        String sessionId = getContext().getSessionId();
        if (StringUtils.isNotEmpty(sessionId) && getContext().getMemoryContext() != null) {
            try {
                MemoryQueryCriteria criteria = MemoryQueryCriteria.create()
                        .addCriteria(MemoryQueryCriteria.KEY_SESSION_ID, sessionId)
                        .limit(20);
                List<ConversationCacheUnit> cacheUnits = getContext().getMemoryContext().getCache(criteria);
                if (CollectionUtils.isNotEmpty(cacheUnits)) {
                    getContext().setProperties(PropertyKeys.KEY_CHAT_HISTORY, cacheUnits);
                }
            } catch (Exception e) {
                log.error("[Flow: {}] 加载会话历史异常", getFlowId(), e);
            }
        }
    }
}
