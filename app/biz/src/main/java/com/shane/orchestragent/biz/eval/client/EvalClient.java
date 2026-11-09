package com.shane.orchestragent.biz.eval.client;

import com.shane.orchestragent.biz.eval.model.CaseMockBundleDTO;
import com.shane.orchestragent.biz.eval.model.ExecutionTraceDTO;
import com.shane.orchestragent.common.exception.BizException;

import java.util.Map;

/**
 * 外部评测系统通信客户端接口
 * <p>
 * 负责与独立的 eval 评测服务建立 HTTP 通信，拉取指定用例的运行包契约载荷 (包含 Mock 数据集与故障注入规则)，
 * 以及将运行产生的全链路 Trace 结构化数据上报并持久化至 eval 系统。
 *
 * @author Shane
 */
public interface EvalClient {

    /**
     * 根据用例 ID 向 eval 系统拉取运行包
     *
     * @param caseId   用例唯一业务标识 (如 "flight_001")
     * @param runId    运行批次 ID (可选，透传用于服务端审计与日志关联)
     * @param repeatNo 重试序号 (可选)
     * @return 用例运行包契约载荷 {@link CaseMockBundleDTO}
     * @throws BizException 若用例不存在 (404) 或服务端通信异常 (500/网络超时)
     */
    CaseMockBundleDTO getCaseBundle(String caseId, String runId, Integer repeatNo) throws BizException;

    /**
     * 向 eval 评测服务上报并持久化链路 Trace 数据
     * <p>
     * 对应外部 eval 系统接口：`POST /api/eval/traces`
     *
     * @param trace 汇编完成的完整结构化 Trace 载荷
     * @return 评估服务端存储确认结果字典
     * @throws BizException 若请求失败或服务端返回异常
     */
    Map<String, Object> recordTrace(ExecutionTraceDTO trace) throws BizException;
}
