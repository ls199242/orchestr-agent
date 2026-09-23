package com.shane.orchestragent.biz.manager;

import com.shane.orchestragent.biz.model.vo.AgentChatRequestVO;
import com.shane.orchestragent.biz.model.vo.AgentInvokeRequestVO;
import com.shane.orchestragent.biz.model.vo.AgentInvokeResponseVO;
import com.shane.orchestragent.biz.sse.SseEmitterUTF8;
import com.shane.orchestragent.common.exception.BizException;

/**
 * 智能体策略协同核心编排中枢接口
 * 负责智能体工作流的生命周期调度、上下文组装、线程池分发与状态流转
 *
 * @author Shane
 */
public interface AgentManager {

    /**
     * 核心异步调用入口
     * 直接触发从 Plan 规划开始的 ReAct 循环协同流程，虚拟线程后台异步执行，立即返回 flowId 供调用方后续查询状态与获取结果
     *
     * @param request 异步调用请求业务对象
     * @return 初始异步响应业务对象 (包含 flowId、sessionId 以及初始状态)
     * @throws BizException 业务异常
     */
    AgentInvokeResponseVO invoke(AgentInvokeRequestVO request) throws BizException;

    /**
     * SSE 流式多轮会话交互入口
     * 调用 Router 智能体进行前置意图识别和分流决策:
     * - 轻量会话意图: 由 Router 智能体直接给出解答并通过 SSE 流式推送完毕
     * - 复杂任务意图: 移交由 Plan 规划器与 ReAct 自愈循环执行，并全程流式推送各智能体步骤思考与最终交付结果
     *
     * @param request 流式会话请求业务对象
     * @return SSE 事件发射器 (SseEmitterUTF8)
     * @throws BizException 业务异常
     */
    SseEmitterUTF8 chat(AgentChatRequestVO request) throws BizException;

    /**
     * 根据流程唯一标识查询异步工作流当前运行状态与执行结果
     *
     * @param flowId 流程唯一标识
     * @return 包含当前状态、各节点处理文案明细、Evaluator质检自愈报告及最终产出成果的响应业务对象
     * @throws BizException 业务异常 (如流程不存在)
     */
    AgentInvokeResponseVO getFlow(String flowId) throws BizException;

    /**
     * 主动终止/取消指定流程
     *
     * @param flowId 流程唯一标识
     * @return 是否成功触发终止
     * @throws BizException 业务异常
     */
    boolean stopFlow(String flowId) throws BizException;
}
