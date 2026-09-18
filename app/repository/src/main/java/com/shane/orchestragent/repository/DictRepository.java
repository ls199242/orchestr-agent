package com.shane.orchestragent.repository;

import com.shane.orchestragent.repository.model.DictDO;

import java.util.Map;

/**
 * 全局运行字典仓储接口
 * 提供系统动态运行参数（超时、最大步数、缓存 TTL、日志级别等）的高性能读取与类型转换
 *
 * @author Shane
 */
public interface DictRepository extends Repository<DictDO> {

    /**
     * 常用全局字典键常量定义
     */
    interface Keys {
        /** 流程最大执行步长限制 */
        String KEY_FLOW_MAX_STEP = "flow_max_step";
        /** 大模型调用最大等待毫秒数 */
        String KEY_LLM_MAX_WAIT_TIME = "llm_max_wait_time";
        /** 流程执行结果缓存有效时间（秒） */
        String KEY_REQUEST_CACHE_SAVE_TIME = "flow_result_cache_save_time";
        /** 聊天多轮记忆临时缓存保存时间（秒） */
        String KEY_CHAT_HISTORY_SAVE_TIME = "flow_chat_history_save_time";
        /** 触发多轮会话摘要压缩的 Token 门槛阈值 */
        String KEY_CHAT_HISTORY_SUMMARY_TOKEN_SIZE = "flow_chat_history_summary_token_size";
        /** 全局日志详细跟踪输出开关 */
        String KEY_LOG_FLAGS = "log_flag";
        /** Evaluator 验收失败时自愈重试最大次数 */
        String KEY_FLOW_EVAL_MAX_RETRY = "flow_eval_max_retry";
        /** 流程实例本地缓存过期时间（分钟） */
        String KEY_FLOW_INSTANCE_EXPIRE_MINUTES = "flow_instance_expire_minutes";
        /** 流程实例本地缓存最大并发容量 */
        String KEY_FLOW_INSTANCE_MAX_CAPACITY = "flow_instance_max_capacity";
        /** 实时日志流初次读取最大行数 */
        String KEY_LOG_STREAM_MAX_LINES = "log_stream_max_lines";
        /** 实时日志流轮询文件更新时间间隔（毫秒） */
        String KEY_LOG_STREAM_POLL_INTERVAL_MS = "log_stream_poll_interval_ms";
        /** 全局默认大模型编码 */
        String KEY_DEFAULT_LLM_MODEL = "default_llm_model";
        /** 系统智能体默认采样温度 */
        String KEY_DEFAULT_SYSTEM_AGENT_TEMP = "default_system_agent_temp";
        /** 业务 Worker 智能体默认采样温度 */
        String KEY_DEFAULT_WORKER_AGENT_TEMP = "default_worker_agent_temp";
    }

    /**
     * 常用全局字典默认值定义
     */
    interface Defaults {
        /** 默认最大执行步长：20 步 */
        int DEFAULT_FLOW_MAX_STEP = 20;
        /** 默认模型调用超时：30000 毫秒 */
        int DEFAULT_LLM_MAX_WAIT_TIME = 30000;
        /** 默认结果缓存时长：3600 秒（1 小时） */
        int DEFAULT_REQUEST_CACHE_SAVE_TIME = 3600;
        /** 默认会话记忆时长：86400 秒（24 小时） */
        int DEFAULT_CHAT_HISTORY_SAVE_TIME = 86400;
        /** 默认记忆触发摘要阈值：20000 tokens */
        int DEFAULT_CHAT_HISTORY_SUMMARY_TOKEN_SIZE = 20000;
        /** 默认详细日志追踪：false */
        String DEFAULT_LOG_FLAGS = "false";
        /** 默认 Evaluator 验收失败自愈最大重试次数：2 次 */
        int DEFAULT_FLOW_EVAL_MAX_RETRY = 2;
        /** 默认流程实例过期时间：30 分钟 */
        int DEFAULT_FLOW_INSTANCE_EXPIRE_MINUTES = 30;
        /** 默认流程实例最大缓存容量：10000 */
        int DEFAULT_FLOW_INSTANCE_MAX_CAPACITY = 10000;
        /** 默认实时日志流初次读取行数：1000 行 */
        int DEFAULT_LOG_STREAM_MAX_LINES = 1000;
        /** 默认实时日志流轮询时间间隔：200 毫秒 */
        int DEFAULT_LOG_STREAM_POLL_INTERVAL_MS = 200;
        /** 默认模型编码：Deepseek */
        String DEFAULT_LLM_MODEL = "Deepseek";
        /** 默认系统智能体温度：0.7 */
        double DEFAULT_SYSTEM_AGENT_TEMP = 0.7;
        /** 默认业务 Worker 智能体温度：0.3 */
        double DEFAULT_WORKER_AGENT_TEMP = 0.3;
    }

    @Override
    default String name() {
        return "dictRepository";
    }

    /**
     * 获取字符串类型字典值
     *
     * @param key          字典键名
     * @param defaultValue 不存在或解析异常时的保底默认值
     * @return 字典字符串取值
     */
    String getValue(String key, String defaultValue);

    /**
     * 获取布尔类型字典值
     *
     * @param key          字典键名
     * @param defaultValue 不存在或解析异常时的保底默认值
     * @return 字典布尔取值
     */
    boolean getValue(String key, boolean defaultValue);

    /**
     * 获取整型字典值
     *
     * @param key          字典键名
     * @param defaultValue 不存在或解析异常时的保底默认值
     * @return 字典整型取值
     */
    int getValue(String key, int defaultValue);

    /**
     * 获取长整型字典值
     *
     * @param key          字典键名
     * @param defaultValue 不存在或解析异常时的保底默认值
     * @return 字典长整型取值
     */
    long getValue(String key, long defaultValue);

    /**
     * 获取双精度浮点类型字典值
     *
     * @param key          字典键名
     * @param defaultValue 不存在或解析异常时的保底默认值
     * @return 字典浮点取值
     */
    default double getValue(String key, double defaultValue) {
        String val = getValue(key, (String) null);
        if (val == null || val.isBlank()) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(val.trim());
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /**
     * 获取当前字典的原始 Map 视图
     *
     * @return 键值对字典映射
     */
    Map<String, String> getDictMap();
}
