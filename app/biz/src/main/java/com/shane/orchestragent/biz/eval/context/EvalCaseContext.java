package com.shane.orchestragent.biz.eval.context;

import com.shane.orchestragent.biz.eval.mock.FaultInjector;
import com.shane.orchestragent.biz.eval.mock.MockEngine;
import com.shane.orchestragent.biz.eval.model.CaseMockBundleDTO;
import com.shane.orchestragent.biz.eval.trace.TraceCollector;
import com.shane.orchestragent.common.time.FixedTimeProvider;
import com.shane.orchestragent.common.time.TimeProvider;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

/**
 * 单次评测请求独享的隔离上下文容器
 * <p>
 * 职责：
 * <ul>
 *   <li>生命周期与单次评测请求（HTTP /eval/run 或角色微运行）强绑定</li>
 *   <li>持有当前请求独享的可注入时钟 (TimeProvider)，隔离并发请求间的基准时间</li>
 *   <li>持有独立的 MockEngine (包含私有的 sequence 游标) 与 FaultInjector 计数器，杜绝并发竞争</li>
 *   <li>持有 TraceCollector，贯穿并收集当前流程产生的所有调度事件与组件指标</li>
 * </ul>
 *
 * @author Shane
 */
@Getter
public class EvalCaseContext {

    /** 关联用例唯一标识 (如 "flight_001") */
    private final String caseId;

    /** 运行批次标识 */
    private final String runId;

    /** 重试序号 */
    private final int repeatNo;

    /** 严格模式开关 (true: 未命中 Mock 阻断报错) */
    private final boolean strictMock;

    /** 注入的时间提供者 (优先冻结时间) */
    private final TimeProvider timeProvider;

    /** 请求独占的 Mock 匹配引擎 */
    private final MockEngine mockEngine;

    /** 请求独占的故障注入器 */
    private final FaultInjector faultInjector;

    /** 请求全链路 Trace 收集器 */
    private final TraceCollector traceCollector;

    /** 是否已触发 Trace 上报防重原子标记 */
    private final java.util.concurrent.atomic.AtomicBoolean reported = new java.util.concurrent.atomic.AtomicBoolean(false);

    /**
     * 构造单次评测隔离上下文
     *
     * @param caseId   用例标识
     * @param runId    运行批次 ID
     * @param repeatNo 重试序号
     * @param mode     运行模式 (FREE, PINNED_PLAN, ISOLATED)
     * @param bundle   从 eval 系统拉取的配置与数据包
     */
    public EvalCaseContext(String caseId, String runId, int repeatNo, String mode, CaseMockBundleDTO bundle) {
        this.caseId = caseId;
        this.runId = runId;
        this.repeatNo = repeatNo;

        boolean strict = true;
        String frozenTime = null;
        if (bundle != null) {
            if (bundle.getOptions() != null) {
                strict = bundle.getOptions().isStrictMock();
            }
            if (bundle.getSession() != null && StringUtils.isNotBlank(bundle.getSession().getFrozenTime())) {
                frozenTime = bundle.getSession().getFrozenTime();
            }
        }
        this.strictMock = strict;

        // 1. 初始化时钟 (优先采用用例指定的固定冻结时间，确保时间相关 Prompt 具有确定性答案)
        if (StringUtils.isNotBlank(frozenTime)) {
            this.timeProvider = new FixedTimeProvider(frozenTime);
        } else {
            this.timeProvider = TimeProvider.system();
        }

        // 2. 初始化 Trace 收集器
        String traceId = "t_" + (caseId != null ? caseId : "eval") + "_" + System.currentTimeMillis();
        this.traceCollector = new TraceCollector(traceId, caseId, runId, repeatNo, mode, this.timeProvider);
        if (bundle != null && bundle.getSession() != null && bundle.getSession().getUserId() != null) {
            this.traceCollector.setUserId(bundle.getSession().getUserId());
        }

        // 3. 初始化请求独占的 Mock 引擎 (包含私有的 sequence 游标)
        this.mockEngine = new MockEngine(
                bundle != null ? bundle.getToolMocks() : null,
                bundle != null ? bundle.getRagMocks() : null,
                this.strictMock,
                this.traceCollector
        );

        // 4. 初始化请求独占的故障注入器 (私有计数器)
        this.faultInjector = new FaultInjector(bundle != null ? bundle.getFaultInjections() : null);
    }

    /**
     * 标记当前上下文的 Trace 已经触发上报（原子 CAS 防重）
     *
     * @return true 若成功首次标记，false 若先前已被标记上报过
     */
    public boolean markReported() {
        return reported.compareAndSet(false, true);
    }

    /**
     * 获取当前上下文是否已完成 Trace 上报
     */
    public boolean isReported() {
        return reported.get();
    }
}
