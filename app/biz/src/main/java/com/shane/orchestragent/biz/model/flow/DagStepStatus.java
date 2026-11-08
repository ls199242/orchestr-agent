package com.shane.orchestragent.biz.model.flow;

/**
 * DAG 步骤执行状态枚举
 *
 * @author Shane
 */
public enum DagStepStatus {

    /** 待调度就绪 */
    PENDING,

    /** 正在并发执行中 */
    RUNNING,

    /** 执行成功达标 */
    SUCCEEDED,

    /** 执行失败 */
    FAILED,

    /** 已跳过 */
    SKIPPED,

    /** 已取消 (流程中止时受波及的节点) */
    CANCELLED
}
