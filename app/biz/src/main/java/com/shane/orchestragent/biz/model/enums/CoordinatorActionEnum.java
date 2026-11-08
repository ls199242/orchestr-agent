package com.shane.orchestragent.biz.model.enums;

/**
 * 多轮会话意图识别决策动作枚举
 * CoordinatorAgent 依据策略目录与会话状态做出的本轮决策动作
 *
 * @author Shane
 */
public enum CoordinatorActionEnum {

    /**
     * 日常闲聊或单轮知识问答，直接答复，不进入任何策略
     */
    DIRECT_REPLY,

    /**
     * 已选定策略但必填参数尚未收集齐备，reply 承载对用户的追问话术
     */
    COLLECT,

    /**
     * 已选定策略且必填参数齐备，触发目标策略流执行
     */
    EXECUTE
}
