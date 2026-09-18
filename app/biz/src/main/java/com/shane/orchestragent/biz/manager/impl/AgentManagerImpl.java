package com.shane.orchestragent.biz.manager.impl;

import com.shane.orchestragent.biz.context.StrategyContext;
import com.shane.orchestragent.biz.context.impl.DefaultStrategyContext;
import com.shane.orchestragent.biz.flow.StrategyFlow;
import com.shane.orchestragent.biz.flow.StrategyFlowFactory;
import com.shane.orchestragent.biz.manager.AgentManager;
import com.shane.orchestragent.biz.model.enums.FlowTopologyTypeEnum;
import com.shane.orchestragent.biz.model.enums.ResultCacheTypeEnum;
import com.shane.orchestragent.biz.model.flow.FlowProcessText;
import com.shane.orchestragent.biz.model.request.RecommendRequestVO;
import com.shane.orchestragent.biz.model.response.RecommendResponseVO;
import com.shane.orchestragent.biz.model.vo.AgentChatRequestVO;
import com.shane.orchestragent.biz.model.vo.AgentInvokeRequestVO;
import com.shane.orchestragent.biz.model.vo.AgentInvokeResponseVO;
import com.shane.orchestragent.biz.service.FlowCacheService;
import com.shane.orchestragent.biz.service.FlowService;
import com.shane.orchestragent.biz.service.SseService;
import com.shane.orchestragent.biz.sse.SseEmitterUTF8;
import com.shane.orchestragent.biz.tool.VirtualThreadExecutors;
import com.shane.orchestragent.common.constant.PropertyKeys;
import com.shane.orchestragent.common.enums.FlowStateEnum;
import com.shane.orchestragent.common.exception.BizErrorFactory;
import com.shane.orchestragent.common.exception.BizException;
import com.shane.orchestragent.memory.context.MemoryContext;
import com.shane.orchestragent.memory.model.ConversationCacheUnit;
import com.shane.orchestragent.repository.StrategyConfigRepository;
import com.shane.orchestragent.repository.model.StrategyConfigDO;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Date;
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

    /** 流程缓存管理服务 */
    private final FlowCacheService flowCacheService;

    /** 会话记忆上下文存储，用于历史记录维护与上下文隔离 */
    @Autowired(required = false)
    private MemoryContext memoryContext;

    /** SSE 流式事件推送服务，维护客户端连接并实现事件分发 */
    @Autowired(required = false)
    private SseService sseService;

    public AgentManagerImpl(StrategyFlowFactory strategyFlowFactory,
                            StrategyConfigRepository strategyConfigRepository,
                            FlowService flowService,
                            FlowCacheService flowCacheService) {
        this.strategyFlowFactory = strategyFlowFactory;
        this.strategyConfigRepository = strategyConfigRepository;
        this.flowService = flowService;
        this.flowCacheService = flowCacheService;
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

        // 1. 获取策略元数据配置
        StrategyConfigDO strategyConfig = getStrategyConfigById(request.getStrategyId());

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
        VirtualThreadExecutors.get().execute(() -> {
            try {
                String reply = strategyFlow.execute();
                persistMemory(context, context.getStrategyTarget(), reply);
                log.info("[FLOW: {}][FINISH] 异步工作流执行完成，最终产出字数: {}", flowId, reply != null ? reply.length() : 0);
            } catch (Exception e) {
                log.error("[AgentManager] 异步 invoke 流程执行异常: flowId={}", flowId, e);
            }
        });

        // 7. 立即返回异步执行标识
        return AgentInvokeResponseVO.builder()
                .flowId(flowId)
                .sessionId(sessionId)
                .state(strategyFlow.getState().name())
                .build();
    }

    /**
     * SSE 流式会话方法实现
     * 组装带 Router 智能体的多轮会话流，首轮进行意图识别与分流，通过 SseEmitter 实时流式推送节点思考片段
     */
    @Override
    public SseEmitterUTF8 chat(AgentChatRequestVO request) throws BizException {
        if (request == null) {
            throw new BizException("REQUEST_NULL", "请求参数不能为空");
        }

        // 1. 获取策略配置
        StrategyConfigDO strategyConfig = getStrategyConfigById(request.getStrategyId());

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

        // 5. 创建带 UTF-8 编码支持的 SSE 发射客户端
        SseEmitterUTF8 emitter = (sseService != null)
                ? sseService.createSseClient(flowId)
                : new SseEmitterUTF8(60000L);

        // 6. 注册流程流式增量监听器，分发智能体思考片段事件
        strategyFlow.addStreamListener((agent, chunk) -> {
            try {
                if (chunk != null && chunk.getFirstMessage() != null) {
                    emitter.send(SseEmitter.event()
                            .name("agent_chunk")
                            .data("[" + agent.getName() + "]: " + chunk.getFirstMessage().getContent()));
                }
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        });

        // 7. 虚拟线程池异步启动流程并发送完成事件
        log.info("[FLOW: {}][START] 启动 MultipleChat 意图分流与会话流水线...", flowId);
        VirtualThreadExecutors.get().execute(() -> {
            try {
                String reply = strategyFlow.execute();
                persistMemory(context, context.getStrategyTarget(), reply);
                emitter.send(SseEmitter.event().name("finish").data(reply));
                emitter.complete();
                log.info("[FLOW: {}][FINISH] 流式会话全流程执行完成，最终交付产出字数: {}", flowId, reply != null ? reply.length() : 0);
            } catch (Exception e) {
                log.error("[FLOW: {}] 流式会话流程执行异常", flowId, e);
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    /**
     * 同步测试调用方法实现
     * 同步阻塞执行从 Plan 启动的 ReAct 循环，等待完整产出与自愈质检后返回结果
     */
    @Override
    public AgentInvokeResponseVO testInvoke(AgentInvokeRequestVO request) throws BizException {
        if (request == null) {
            throw new BizException("REQUEST_NULL", "请求参数不能为空");
        }

        long startTime = System.currentTimeMillis();

        // 1. 获取策略配置
        StrategyConfigDO strategyConfig = getStrategyConfigById(request.getStrategyId());

        // 2. 准备流程 ID 与会话 ID
        String flowId = StringUtils.defaultIfBlank(request.getFlowId(), UUID.randomUUID().toString());
        String sessionId = StringUtils.defaultIfBlank(request.getSessionId(), UUID.randomUUID().toString());

        // 3. 构建策略运行上下文
        StrategyContext context = buildContext(strategyConfig, flowId, sessionId, request.getMessage(),
                request.getBizData(), request.getProperties(), request.getUserId(), request.getTraceId());
        log.info("[FLOW: {}][INPUT] 同步测试流程上下文初始化完成: sessionId={}, userId={}, target='{}'",
                flowId, sessionId, request.getUserId(), context.getStrategyTarget());

        // 4. 构建 ReAct 策略流程
        StrategyFlow strategyFlow = strategyFlowFactory.createReActFlow(strategyConfig, context);

        // 5. 同步阻塞执行全流程
        log.info("[FLOW: {}][START] 启动 ReAct 同步全流程协同执行流水线...", flowId);
        String reply = strategyFlow.execute();

        // 6. 归档会话记忆
        persistMemory(context, context.getStrategyTarget(), reply);

        long costMs = System.currentTimeMillis() - startTime;
        log.info("[FLOW: {}][FINISH] 同步测试全流程执行完毕: 总耗时={}ms, 状态={}, 质检报告={}",
                flowId, costMs, strategyFlow.getState(),
                context.getLastEvaluatorResult() != null ? "得分=" + context.getLastEvaluatorResult().getScore() + ", pass=" + context.getLastEvaluatorResult().isPass() : "无质检");

        // 7. 返回完整执行结果
        return AgentInvokeResponseVO.builder()
                .flowId(flowId)
                .sessionId(sessionId)
                .reply(reply)
                .state(strategyFlow.getState().name())
                .evaluation(context.getLastEvaluatorResult())
                .costMs(costMs)
                .build();
    }

    @Override
    public RecommendResponseVO process(RecommendRequestVO request) throws BizException {
        if (request == null) {
            throw new BizException("REQUEST_NULL", "请求参数不能为空");
        }

        StrategyConfigDO strategyConfig = getStrategyConfig(request);
        String flowId = flowService.createFlowId(request, strategyConfig);
        StrategyContext context = buildContext(request, flowId, strategyConfig);

        if (StringUtils.isBlank(request.getFlowId()) && FlowTopologyTypeEnum.of(strategyConfig.getFlowTopologyType()).isUseResultCache()) {
            String cachedResult = flowService.getResult(flowId);
            if (StringUtils.isNotEmpty(cachedResult)) {
                return RecommendResponseVO.buildResponse(flowId, context.getSessionId(), FlowStateEnum.FINISHED, cachedResult, null, ResultCacheTypeEnum.FLOW_ID_CACHE);
            }
        }

        FlowStateEnum currentState = flowService.getState(flowId);
        if (currentState != null && currentState != FlowStateEnum.NONE) {
            String result = flowService.getResult(flowId);
            List<FlowProcessText> processTexts = flowService.getProcessTexts(flowId);
            return RecommendResponseVO.buildResponse(flowId, context.getSessionId(), currentState, result, processTexts, ResultCacheTypeEnum.FLOW_ID_CACHE);
        }

        StrategyFlow strategyFlow = strategyFlowFactory.create(context);
        VirtualThreadExecutors.get().execute(strategyFlow::start);

        return RecommendResponseVO.buildResponse(flowId, context.getSessionId(), strategyFlow.getState(), null, null, ResultCacheTypeEnum.NONE);
    }

    private StrategyConfigDO getStrategyConfig(RecommendRequestVO request) throws BizException {
        return getStrategyConfigById(request != null ? request.getCode() : null);
    }

    /**
     * 根据策略编码获取策略实体，若未获取到则直接抛出业务异常中断流程（严禁使用默认兜底）
     *
     * @param strategyId 策略唯一标识
     * @return 策略配置实体
     * @throws BizException 若策略不存在直接抛出异常中断
     */
    private StrategyConfigDO getStrategyConfigById(String strategyId) throws BizException {
        if (StringUtils.isBlank(strategyId)) {
            throw BizErrorFactory.getInstance().strategyNotFound("null");
        }
        StrategyConfigDO config = strategyConfigRepository.getById(strategyId);
        if (config == null) {
            config = strategyConfigRepository.findStrategyByCode(strategyId);
        }
        if (config == null) {
            throw BizErrorFactory.getInstance().strategyNotFound(strategyId);
        }
        log.info("[CONFIG][GET] 成功获取策略配置: strategyId={}, name={}, topology={}, workersCount={}",
                config.getStrategyId(), config.getName(), config.getFlowTopologyType(),
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
                .code(strategyConfig != null ? strategyConfig.getStrategyId() : null)
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

    private StrategyContext buildContext(RecommendRequestVO request, String flowId, StrategyConfigDO strategyConfig) {
        String sessionId = StringUtils.defaultIfBlank(request.getSessionId(), UUID.randomUUID().toString());
        DefaultStrategyContext context = new DefaultStrategyContext(strategyConfig, flowId, sessionId, request);

        if (request.getBizData() != null) {
            String query = request.getBizData().get("query");
            if (StringUtils.isNotBlank(query)) {
                context.setProperties(PropertyKeys.KEY_STRATEGY_TARGET, query);
            }
        }

        MemoryContext memCtx = flowCacheService.createMemoryContext(request, strategyConfig);
        context.setMemoryContext(memCtx);

        return context;
    }

    /**
     * 会话历史记忆异步持久化归档
     *
     * @param context 策略运行上下文
     * @param userMsg 用户输入文本
     * @param assistantMsg 智能体输出产物
     */
    private void persistMemory(StrategyContext context, String userMsg, String assistantMsg) {
        if (memoryContext != null && StringUtils.isNotBlank(context.getSessionId())) {
            if (StringUtils.isNotBlank(userMsg)) {
                memoryContext.saveCache(ConversationCacheUnit.builder()
                        .sessionId(context.getSessionId())
                        .messageId(UUID.randomUUID().toString())
                        .role("user")
                        .content(userMsg)
                        .createTime(new Date())
                        .build());
            }
            if (StringUtils.isNotBlank(assistantMsg)) {
                memoryContext.saveCache(ConversationCacheUnit.builder()
                        .sessionId(context.getSessionId())
                        .messageId(UUID.randomUUID().toString())
                        .role("assistant")
                        .content(assistantMsg)
                        .createTime(new Date())
                        .build());
            }
        }
    }
}
