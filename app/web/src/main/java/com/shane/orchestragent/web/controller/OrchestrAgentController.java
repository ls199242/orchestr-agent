package com.shane.orchestragent.web.controller;

import com.shane.orchestragent.biz.manager.AgentManager;
import com.shane.orchestragent.biz.model.vo.AgentChatRequestVO;
import com.shane.orchestragent.biz.model.vo.AgentInvokeRequestVO;
import com.shane.orchestragent.biz.model.vo.AgentInvokeResponseVO;
import com.shane.orchestragent.biz.sse.SseEmitterUTF8;
import com.shane.orchestragent.common.annotation.Api;
import com.shane.orchestragent.common.enums.LogModuleEnum;
import com.shane.orchestragent.common.exception.BizException;
import com.shane.orchestragent.common.model.BaseResult;
import com.shane.orchestragent.web.converter.AgentApiMapping;
import com.shane.orchestragent.web.dto.AgentChatRequestDTO;
import com.shane.orchestragent.web.dto.AgentInvokeRequestDTO;
import com.shane.orchestragent.web.dto.AgentInvokeResponseDTO;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 智能体编排系统对外核心交互控制器
 * 作为 Web 表现层入口，统一处理 HTTP 外部请求与 DTO 契约协议
 * 严格保持职责纯粹：仅负责 DTO 与 VO 的转换以及调用底层 Manager 业务中枢
 *
 * @author Shane
 */
@RestController
@RequestMapping("/api/agent")
public class OrchestrAgentController {

    /** 协议模型转换映射器，负责表现层 DTO 与业务层 VO 的相互转换 */
    private final AgentApiMapping apiMapping;

    /** 智能体策略协同核心编排中枢，负责具体工作流拓扑调度与执行 */
    private final AgentManager agentManager;

    public OrchestrAgentController(AgentApiMapping apiMapping, AgentManager agentManager) {
        this.apiMapping = apiMapping;
        this.agentManager = agentManager;
    }

    /**
     * 1. 异步编排调用入口 (invoke)
     * 异步调用方法，直接触发从 Plan 规划开始的 ReAct 循环，虚拟线程后台执行
     * 立即返回 flowId 供后续调用方查询状态和获取最终执行结果
     *
     * @param request 异步调用请求数据传输对象 (DTO)
     * @return 包含 flowId 与初始运行状态的响应统一包装结果 (BaseResult)
     * @throws BizException 业务异常
     */
    @PostMapping("/invoke")
    @Api(logModule = LogModuleEnum.API_INVOKE, desc = "异步编排调用")
    public BaseResult<AgentInvokeResponseDTO> invoke(@RequestBody AgentInvokeRequestDTO request) throws BizException {
        // 1. 将外部传输 DTO 转换为业务内部请求 VO
        AgentInvokeRequestVO requestVO = apiMapping.dto2v(request);

        // 2. 调用业务编排中枢执行异步流程启动
        AgentInvokeResponseVO responseVO = agentManager.invoke(requestVO);

        // 3. 将内部返回 VO 转换为外部统一传输 DTO 并包装返回
        return BaseResult.ok(apiMapping.v2dto(responseVO));
    }

    /**
     * 2. SSE 流式会话交互入口 (chat)
     * SSE 流式调用 Router 智能体，进行意图识别与分流决策:
     * - 简单对话意图直接流式输出解答
     * - 复杂任务意图触发 Plan + ReAct 策略流程并全程流式推送思考片段与交付物
     *
     * @param request 流式会话请求数据传输对象 (DTO)
     * @return SSE 事件发射器 (SseEmitterUTF8)，通过 text/event-stream 持续推送数据分片
     * @throws BizException 业务异常
     */
    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Api(logModule = LogModuleEnum.API_CHAT, desc = "SSE流式会话", recordResponse = false)
    public SseEmitterUTF8 chat(@RequestBody AgentChatRequestDTO request) throws BizException {
        // 1. 将外部会话传输 DTO 转换为业务内部请求 VO
        AgentChatRequestVO requestVO = apiMapping.dto2ChatVo(request);

        // 2. 调用业务中枢组装多轮意图分流流并获取 SSE 发射器
        return agentManager.chat(requestVO);
    }

    /**
     * 3. 同步测试调用入口 (testInvoke)
     * 同步触发 invoke 对应的 ReAct 协同执行流水线，阻塞等待全流程执行完毕
     * 主要用于研发自测、单元测试与控制台即时联调
     *
     * @param request 测试调用请求数据传输对象 (DTO)
     * @return 包含最终生成结果、流程状态、Evaluator质检报告以及总耗时的完整响应
     * @throws BizException 业务异常
     */
    @PostMapping("/testInvoke")
    @Api(logModule = LogModuleEnum.API_TEST_INVOKE, desc = "同步测试调用")
    public BaseResult<AgentInvokeResponseDTO> testInvoke(@RequestBody AgentInvokeRequestDTO request) throws BizException {
        // 1. 将测试传输 DTO 转换为业务内部请求 VO
        AgentInvokeRequestVO requestVO = apiMapping.dto2v(request);

        // 2. 调用业务中枢同步阻塞执行完整流程
        AgentInvokeResponseVO responseVO = agentManager.testInvoke(requestVO);

        // 3. 将结果 VO 转换为传输 DTO 并返回
        return BaseResult.ok(apiMapping.v2dto(responseVO));
    }
}
