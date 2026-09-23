package com.shane.orchestragent.biz.manager.impl;

import com.shane.orchestragent.biz.context.StrategyContext;
import com.shane.orchestragent.biz.context.impl.DefaultStrategyContext;
import com.shane.orchestragent.biz.flow.StrategyFlow;
import com.shane.orchestragent.biz.flow.StrategyFlowFactory;
import com.shane.orchestragent.biz.flow.store.FlowStore;
import com.shane.orchestragent.biz.manager.AgentManager;
import com.shane.orchestragent.biz.model.flow.EvaluatorResult;
import com.shane.orchestragent.biz.model.flow.FlowProcessText;
import com.shane.orchestragent.biz.model.request.RecommendRequestVO;
import com.shane.orchestragent.biz.model.vo.AgentChatRequestVO;
import com.shane.orchestragent.biz.model.vo.AgentInvokeRequestVO;
import com.shane.orchestragent.biz.model.vo.AgentInvokeResponseVO;
import com.shane.orchestragent.biz.service.FlowService;
import com.shane.orchestragent.biz.service.SseService;
import com.shane.orchestragent.biz.sse.SseEmitterUTF8;
import com.shane.orchestragent.biz.tool.VirtualThreadExecutors;
import com.shane.orchestragent.common.constant.PropertyKeys;
import com.shane.orchestragent.common.enums.FlowStateEnum;
import com.shane.orchestragent.common.exception.BizErrorFactory;
import com.shane.orchestragent.common.exception.BizException;
import com.shane.orchestragent.memory.context.MemoryContext;
import com.shane.orchestragent.repository.StrategyConfigRepository;
import com.shane.orchestragent.repository.model.StrategyConfigDO;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 智能体策略协同编排中枢核心实现
 * 统一管理智能体工作流装配、上下文构建、虚拟线程异步调度、流式事件推送与会话记忆归档
 *
 * @author Shane
 */
@Service
public class AgentManagerImpl implements AgentManager {

    private static final Logger log = LoggerFactory.getLogger(AgentManagerImpl.class);

    /** 策略工作流工程工厂，负责根据拓扑结构组装各智能体节点与监听器 */
    private final StrategyFlowFactory strategyFlowFactory;

    /** 策略配置仓储，提供策略元数据、Worker专精节点定义与执行上限配置 */
    private final StrategyConfigRepository strategyConfigRepository;

    /** 流程生命周期与结果管理服务 */
    private final FlowService flowService;

    /** 会话记忆上下文存储，用于历史记录维护与上下文隔离 */
    @Autowired(required = false)
    private MemoryContext memoryContext;

    /** SSE 流式事件推送服务，维护客户端连接并实现事件分发 */
    @Autowired(required = false)
    private SseService sseService;

    public AgentManagerImpl(StrategyFlowFactory strategyFlowFactory,
                            StrategyConfigRepository strategyConfigRepository,
                            FlowService flowService) {
        this.strategyFlowFactory = strategyFlowFactory;
        this.strategyConfigRepository = strategyConfigRepository;
        this.flowService = flowService;
    }

    @Autowired(required = false)
    public void setMemoryContext(MemoryContext memoryContext) {
        this.memoryContext = memoryContext;
    }

    @Autowired(required = false)
    public void setSseService(SseService sseService) {
        this.sseService = sseService;
    }

    /**
     * 核心异步调用方法实现
     * 直接触发从 Plan 规划开始的 ReAct 循环流水线，使用虚拟线程池后台运行，立即返回 flowId 供调用方追踪
     */
    @Override
    public AgentInvokeResponseVO invoke(AgentInvokeRequestVO request) throws BizException {
        if (request == null) {
            throw new BizException("REQUEST_NULL", "请求参数不能为空");
        }

        String strategyCode = request.getStrategyCode();

        // 1. 获取策略元数据配置 (若未指定策略直接 Fail-Fast 抛出 BizException)
        StrategyConfigDO strategyConfig = getStrategyConfigByCode(strategyCode);

        // 2. 生成流程唯一标识与会话唯一标识
        String flowId = StringUtils.defaultIfBlank(request.getFlowId(), UUID.randomUUID().toString());
        String sessionId = StringUtils.defaultIfBlank(request.getSessionId(), UUID.randomUUID().toString());

        // 3. 组装策略运行上下文环境
        StrategyContext context = buildContext(strategyConfig, flowId, sessionId, request.getMessage(),
                request.getBizData(), request.getProperties(), request.getUserId(), request.getTraceId());
        log.info("[FLOW: {}][INPUT] 异步流程上下文初始化完成: sessionId={}, userId={}, target='{}'",
                flowId, sessionId, request.getUserId(), context.getStrategyTarget());

        // 4. 组装纯 ReAct 策略流程 (直接从 Plan 规划启动协同循环)
        StrategyFlow strategyFlow = strategyFlowFactory.createReActFlow(strategyConfig, context);

        // 5. 记录流程初始状态
        if (flowService != null) {
            flowService.markState(flowId, FlowStateEnum.INITIAL);
        }

        // 6. 提交虚拟线程池异步启动执行
        log.info("[FLOW: {}][START] 提交异步线程启动 ReAct 工作流...", flowId);
        VirtualThreadExecutors.get().execute(strategyFlow::execute);

        // 7. 立即返回异步执行标识
        return AgentInvokeResponseVO.builder()
                .flowId(flowId)
                .sessionId(sessionId)
                .state(strategyFlow.getState().name())
                .build();
    }

    /**
     * SSE 流式会话方法实现
     * 组装带 Router 智能体的多轮会话流，首轮进行意图识别与分流，通过 SseService#attachFlow 统一纳管流式推送与生命周期监听
     */
    @Override
    public SseEmitterUTF8 chat(AgentChatRequestVO request) throws BizException {
        if (request == null) {
            throw new BizException("REQUEST_NULL", "请求参数不能为空");
        }

        // 1. 获取策略配置
        String strategyCode = request.getStrategyCode();
        StrategyConfigDO strategyConfig = getStrategyConfigByCode(strategyCode);

        // 2. 初始化流程 ID 与会话 ID
        String flowId = UUID.randomUUID().toString();
        String sessionId = StringUtils.defaultIfBlank(request.getSessionId(), UUID.randomUUID().toString());

        // 3. 构建多轮会话运行上下文
        StrategyContext context = buildContext(strategyConfig, flowId, sessionId, request.getMessage(),
                null, request.getProperties(), request.getUserId(), request.getTraceId());
        log.info("[FLOW: {}][INPUT] 流式会话上下文初始化完成: sessionId={}, userId={}, target='{}'",
                flowId, sessionId, request.getUserId(), context.getStrategyTarget());

        // 4. 构建包含 Router 意图分流的多轮会话策略流程
        StrategyFlow strategyFlow = strategyFlowFactory.createMultipleChatFlow(strategyConfig, context);

        // 5. 绑定 SSE 流式事件推送与生命周期监听
        SseEmitterUTF8 emitter = (sseService != null)
                ? sseService.attachFlow(strategyFlow)
                : new SseEmitterUTF8(60000L);

        // 6. 虚拟线程池异步启动流程（逻辑完全内化，与 invoke 对齐）
        log.info("[FLOW: {}][START] 启动 MultipleChat 意图分流与会话流水线...", flowId);
        VirtualThreadExecutors.get().execute(strategyFlow::execute);

        return emitter;
    }

    /**
     * 根据流程唯一标识查询异步工作流当前运行状态与执行结果
     */
    @Override
    public AgentInvokeResponseVO getFlow(String flowId) throws BizException {
        if (StringUtils.isBlank(flowId)) {
            throw new BizException("FLOW_ID_BLANK", "流程唯一标识 (flowId) 不能为空");
        }

        FlowStateEnum state = (flowService != null) ? flowService.getState(flowId) : FlowStateEnum.NONE;
        StrategyFlow strategyFlow = FlowStore.get(flowId);

        if ((state == null || state == FlowStateEnum.NONE) && strategyFlow == null) {
            throw new BizException("FLOW_NOT_FOUND", "未找到流程实例: " + flowId);
        }

        if (strategyFlow != null && (state == null || state == FlowStateEnum.NONE)) {
            state = strategyFlow.getState();
        }

        String reply = (flowService != null) ? flowService.getResult(flowId) : null;
        List<FlowProcessText> processTexts = (flowService != null) ? flowService.getProcessTexts(flowId) : null;
        EvaluatorResult evaluation = null;
        String sessionId = null;
        Long costMs = null;

        if (strategyFlow != null && strategyFlow.getContext() != null) {
            StrategyContext context = strategyFlow.getContext();
            sessionId = context.getSessionId();
            if (StringUtils.isBlank(reply)) {
                reply = context.getRecommendResult();
            }
            evaluation = context.getLastEvaluatorResult();
            Object costObj = context.getProperty("costMs");
            if (costObj instanceof Number number) {
                costMs = number.longValue();
            } else {
                Object startObj = context.getProperty("startTime");
                if (startObj instanceof Number startNum) {
                    costMs = System.currentTimeMillis() - startNum.longValue();
                }
            }
        }

        return AgentInvokeResponseVO.builder()
                .flowId(flowId)
                .sessionId(sessionId)
                .reply(reply)
                .state(state != null ? state.name() : FlowStateEnum.NONE.name())
                .evaluation(evaluation)
                .processTexts(processTexts)
                .costMs(costMs)
                .build();
    }

    @Override
    public boolean stopFlow(String flowId) throws BizException {
        if (StringUtils.isBlank(flowId)) {
            throw new BizException("FLOW_ID_BLANK", "流程唯一标识 (flowId) 不能为空");
        }
        StrategyFlow strategyFlow = FlowStore.get(flowId);
        if (strategyFlow == null) {
            log.warn("[AgentManager] 未在 FlowStore 中找到运行中流程实例: flowId={}", flowId);
            if (flowService != null) {
                flowService.markState(flowId, FlowStateEnum.STOPPED);
            }
            return false;
        }
        log.info("[AgentManager] 正在主动终止流程: flowId={}", flowId);
        strategyFlow.stop();
        if (flowService != null) {
            flowService.markState(flowId, FlowStateEnum.STOPPED);
        }
        return true;
    }

    /**
     * 根据策略业务编码获取策略实体，若未获取到则直接抛出业务异常中断流程（严禁使用默认兜底）
     *
     * @param strategyCode 策略业务编码
     * @return 策略配置实体
     * @throws BizException 若策略不存在直接抛出异常中断
     */
    private StrategyConfigDO getStrategyConfigByCode(String strategyCode) throws BizException {
        if (StringUtils.isBlank(strategyCode)) {
            throw BizErrorFactory.getInstance().strategyNotFound("null");
        }
        StrategyConfigDO config = strategyConfigRepository.getByCode(strategyCode);
        if (config == null) {
            throw BizErrorFactory.getInstance().strategyNotFound(strategyCode);
        }
        log.info("[CONFIG][GET] 成功获取策略配置: code={}, name={}, topology={}, workersCount={}",
                config.getCode(), config.getName(), config.getFlowTopologyType(),
                config.getWorkerCodes() != null ? config.getWorkerCodes().size() : 0);
        return config;
    }

    /**
     * 统一策略上下文构建逻辑
     *
     * @param strategyConfig 策略配置实体
     * @param flowId 流程执行标识
     * @param sessionId 会话标识
     * @param message 用户主输入文本
     * @param bizData 业务键值对数据
     * @param properties 扩展环境参数
     * @param userId 调用用户
     * @param traceId 链路追踪标识
     * @return 策略运行上下文对象
     */
    private StrategyContext buildContext(StrategyConfigDO strategyConfig, String flowId, String sessionId,
                                         String message, Map<String, String> bizData, Map<String, Object> properties,
                                         String userId, String traceId) {
        RecommendRequestVO recommendReq = RecommendRequestVO.builder()
                .code(strategyConfig != null ? strategyConfig.getCode() : null)
                .flowId(flowId)
                .sessionId(sessionId)
                .traceId(traceId)
                .bizData(bizData)
                .build();

        DefaultStrategyContext context = new DefaultStrategyContext(strategyConfig, flowId, sessionId, recommendReq);
        context.setUserId(userId);
        context.setMemoryContext(memoryContext);

        if (properties != null) {
            properties.forEach(context::setProperties);
        }

        // 统一设定用户目标: 优先使用 message，次优从 bizData 中提取 query
        String target = message;
        if (StringUtils.isBlank(target) && bizData != null) {
            target = bizData.get("query");
        }
        if (StringUtils.isNotBlank(target)) {
            context.setProperties(PropertyKeys.KEY_STRATEGY_TARGET, target);
        }

        return context;
    }
}
