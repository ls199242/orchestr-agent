package com.shane.orchestragent.biz.model.flow;

/**
 * DAG 步骤执行失败后的应对动作策略
 *
 * @author Shane
 */
public enum OnFailureAction {

    /** 立即中止整体工作流执行 */
    ABORT,

    /** 忽略当前步骤失败，允许下游及其他不依赖该步骤的节点继续执行 */
    SKIP
}
