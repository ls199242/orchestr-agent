package com.shane.orchestragent.common.constant;

/**
 * PropertyKeys
 *
 * @author Shane
 */
public final class PropertyKeys {

    private PropertyKeys() {
    }

    /** 策略目标 */
    public static final String KEY_STRATEGY_TARGET = "strategy_target";

    /** 聊天历史记录 */
    public static final String KEY_CHAT_HISTORY = "chat_history";

    /** 用户标签结果 */
    public static final String KEY_USER_TAG_RESULT = "user_tag_result";

    /** RAG 检索结果 */
    public static final String KEY_RAG_RESULT = "rag_result";

    /** 上一个智能体产出 */
    public static final String KEY_LAST_AGENT_RESULT = "last_agent_result";

    /** 下一个智能体请求入参 */
    public static final String KEY_NEXT_AGENT_REQUEST = "next_agent_request";

    /** Evaluator 评估批评与改进建议反馈 */
    public static final String KEY_EVALUATOR_FEEDBACK = "evaluator_feedback";

    /** 最终推荐/交付报告 */
    public static final String KEY_RECOMMEND_RESULT = "recommend_result";

    /** 策略配置 */
    public static final String KEY_STRATEGY = "strategy";

    /** 业务请求参数对象 */
    public static final String KEY_REQUEST_BIZ_DATA = "requestBizData";

    /** 业务请求参数 JSON 串 */
    public static final String KEY_REQUEST_BIZ_DATA_JSON = "requestBizDataJson";

    /** 智能体消息流水历史 */
    public static final String KEY_AGENT_MESSAGE_HISTORY = "agentHistoryMessages";

    /** 报告输出 Schema 约束 */
    public static final String KEY_REPORTER_TARGET = "reporterTarget";

    /** 规划器规划结果 */
    public static final String KEY_PLANNER_RESULT = "plannerResult";

    /** 规划器规划步骤列表 */
    public static final String KEY_PLAN_STEPS = "planSteps";

    /** 会话ID */
    public static final String KEY_SESSION_ID = "sessionId";

    /** 用户ID */
    public static final String KEY_USER_ID = "userId";

    /** 意图识别候选策略目录 (code/name/description/params 裁剪视图) */
    public static final String KEY_STRATEGY_CATALOG = "strategy_catalog";

    /** 会话待定策略业务编码 (跨轮参数收集中的目标策略) */
    public static final String KEY_PENDING_STRATEGY = "pending_strategy";

    /** 会话已收集的策略入参键值对 (跨轮累积) */
    public static final String KEY_COLLECTED_PARAMS = "collected_params";
}
