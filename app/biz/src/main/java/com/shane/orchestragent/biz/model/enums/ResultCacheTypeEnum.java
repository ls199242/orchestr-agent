package com.shane.orchestragent.biz.model.enums;

/**
 * 结果缓存命中类型
 *
 * @author Shane
 */
public enum ResultCacheTypeEnum {

    /** 未命中缓存 */
    NONE,

    /** 流程 ID 缓存 */
    FLOW_ID_CACHE,

    /** 入参请求精确缓存 */
    REQUEST_PARAMETER_CACHE,

    /** 相似度分类缓存 */
    SIMILARITY_CACHE
}
