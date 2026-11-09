package com.shane.orchestragent.biz.eval.mock;

import com.shane.orchestragent.biz.eval.model.CaseMockBundleDTO;
import com.shane.orchestragent.biz.eval.trace.TraceCollector;
import com.shane.orchestragent.common.exception.BizException;
import com.shane.orchestragent.common.utils.JsonUtils;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * 请求级隔离的 Mock 匹配引擎
 * <p>
 * 职责：
 * <ul>
 *   <li>基于工具入参 JSON 字典，进行参数子集或多字段精确匹配</li>
 *   <li>为每个独立请求实例维护私有的次序返回队列 (sequence Queue)，支持回环用例多轮不同返回</li>
 *   <li>严格模式控制：若开启 strictMock 且未命中规则，直接抛出 MOCK_MISMATCH 业务异常并记录缺口，严禁穿透</li>
 * </ul>
 *
 * @author Shane
 */
public class MockEngine {

    private static final Logger log = LoggerFactory.getLogger(MockEngine.class);

    private final boolean strictMock;
    private final TraceCollector traceCollector;

    /** 工具规则及其对应的请求独立次序队列 */
    private final Map<String, List<ToolRuleHolder>> toolRules = new ConcurrentHashMap<>();

    /** RAG 规则列表 */
    private final Map<String, List<CaseMockBundleDTO.RagMockRuleDTO>> ragRules = new ConcurrentHashMap<>();

    /**
     * 单个工具 Mock 规则的状态持有者 (包含当前请求独占的次序出队游标)
     */
    private static class ToolRuleHolder {
        final CaseMockBundleDTO.ToolMockRuleDTO rule;
        final Queue<String> sequenceQueue;
        String lastSequenceItem;

        ToolRuleHolder(CaseMockBundleDTO.ToolMockRuleDTO rule) {
            this.rule = rule;
            if (CollectionUtils.isNotEmpty(rule.getSequence())) {
                this.sequenceQueue = new ConcurrentLinkedQueue<>(rule.getSequence());
                this.lastSequenceItem = rule.getSequence().get(rule.getSequence().size() - 1);
            } else {
                this.sequenceQueue = null;
            }
        }
    }

    /**
     * 构造 Mock 匹配引擎
     *
     * @param rawToolMocks   原始工具 Mock 规则表
     * @param rawRagMocks    原始 RAG Mock 规则表
     * @param strictMock     是否开启严格模式 (未命中抛出异常)
     * @param traceCollector Trace 收集器 (记录 Mock 缺口)
     */
    public MockEngine(Map<String, List<CaseMockBundleDTO.ToolMockRuleDTO>> rawToolMocks,
                      Map<String, List<CaseMockBundleDTO.RagMockRuleDTO>> rawRagMocks,
                      boolean strictMock,
                      TraceCollector traceCollector) {
        this.strictMock = strictMock;
        this.traceCollector = traceCollector;

        if (MapUtils.isNotEmpty(rawToolMocks)) {
            rawToolMocks.forEach((toolCode, rules) -> {
                if (CollectionUtils.isNotEmpty(rules)) {
                    List<ToolRuleHolder> holders = new ArrayList<>();
                    for (CaseMockBundleDTO.ToolMockRuleDTO r : rules) {
                        holders.add(new ToolRuleHolder(r));
                    }
                    this.toolRules.put(toolCode, holders);
                }
            });
        }

        if (MapUtils.isNotEmpty(rawRagMocks)) {
            this.ragRules.putAll(rawRagMocks);
        }
    }

    /**
     * 匹配工具 Mock 响应
     *
     * @param toolCode    工具唯一编码
     * @param requestJson 请求参数 JSON
     * @return 匹配到的 Mock 文本
     * @throws BizException 若严格模式未命中抛出 Mock 缺口异常
     */
    public String matchToolMock(String toolCode, String requestJson) throws BizException {
        List<ToolRuleHolder> holders = toolRules.get(toolCode);
        Map<String, Object> requestMap = parseRequestMap(requestJson);

        if (CollectionUtils.isNotEmpty(holders)) {
            for (ToolRuleHolder holder : holders) {
                if (matchesCondition(holder.rule.getMatch(), requestMap)) {
                    // 命中规则: 若定义了次序返回，出队获取
                    if (holder.sequenceQueue != null) {
                        String polled = holder.sequenceQueue.poll();
                        if (polled != null) {
                            return polled;
                        }
                        // 队列耗尽后复用最后一项静态保底
                        return holder.lastSequenceItem != null ? holder.lastSequenceItem : "{}";
                    }
                    return holder.rule.getReturnBody() != null ? holder.rule.getReturnBody() : "{}";
                }
            }
        }

        // 未命中任何 Mock 规则
        if (traceCollector != null) {
            traceCollector.recordMockGap();
        }

        if (strictMock) {
            log.error("[MockEngine] 严格模式触发: 工具 [{}] 未命中任何 Mock 规则! 入参: {}", toolCode, requestJson);
            throw new BizException("MOCK_MISMATCH", "严格评测模式阻断: 工具 [" + toolCode + "] 未命中任何预置 Mock 规则 (Mock缺口)");
        }

        log.warn("[MockEngine] 工具 [{}] 未命中 Mock 且非严格模式，返回空对象", toolCode);
        return "{}";
    }

    /**
     * 匹配 RAG Mock 响应
     *
     * @param ragName 知识库/伴生节点名称
     * @param query   检索词
     * @return 匹配内容
     * @throws BizException 若严格模式未命中抛出异常
     */
    public String matchRagMock(String ragName, String query) throws BizException {
        List<CaseMockBundleDTO.RagMockRuleDTO> rules = ragRules.get(ragName);
        if (CollectionUtils.isNotEmpty(rules)) {
            for (CaseMockBundleDTO.RagMockRuleDTO r : rules) {
                if (StringUtils.isBlank(r.getQueryMatch()) || (query != null && query.contains(r.getQueryMatch()))) {
                    return r.getContent();
                }
            }
            // 若有配置但未匹配特定 query，默认返回第一条
            return rules.get(0).getContent();
        }

        if (traceCollector != null) {
            traceCollector.recordMockGap();
        }

        if (strictMock) {
            log.error("[MockEngine] 严格模式触发: RAG [{}] 未命中任何 Mock 规则! query: {}", ragName, query);
            throw new BizException("MOCK_MISMATCH", "严格评测模式阻断: RAG [" + ragName + "] 未命中预置知识库 Mock (Mock缺口)");
        }

        return "【Mock知识库默认检索内容】";
    }

    /**
     * 判定请求参数是否满足规则中声明的条件集合 (参数子集匹配)
     *
     * @param condition 规则中要求的键值对
     * @param request   实际运行请求参数字典
     * @return 是否全部满足匹配
     */
    private boolean matchesCondition(Map<String, Object> condition, Map<String, Object> request) {
        if (MapUtils.isEmpty(condition)) {
            return true;
        }
        if (MapUtils.isEmpty(request)) {
            return false;
        }

        for (Map.Entry<String, Object> entry : condition.entrySet()) {
            String expectedKey = entry.getKey();
            Object expectedVal = entry.getValue();

            Object actualVal = findValueIgnoreCase(request, expectedKey);
            if (actualVal == null) {
                return false;
            }

            if (expectedVal instanceof Collection<?> expectedList) {
                boolean contains = false;
                for (Object item : expectedList) {
                    if (item != null && item.toString().equalsIgnoreCase(String.valueOf(actualVal))) {
                        contains = true;
                        break;
                    }
                }
                if (!contains) {
                    return false;
                }
            } else {
                if (!String.valueOf(expectedVal).equalsIgnoreCase(String.valueOf(actualVal))) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * 不区分大小写查找 Map 中的属性值
     */
    private Object findValueIgnoreCase(Map<String, Object> map, String targetKey) {
        if (map.containsKey(targetKey)) {
            return map.get(targetKey);
        }
        for (Map.Entry<String, Object> e : map.entrySet()) {
            if (e.getKey() != null && e.getKey().equalsIgnoreCase(targetKey)) {
                return e.getValue();
            }
        }
        return null;
    }

    /**
     * 将请求入参文本解析为 Map
     */
    private Map<String, Object> parseRequestMap(String json) {
        if (StringUtils.isBlank(json)) {
            return Collections.emptyMap();
        }
        try {
            Map<String, Object> map = JsonUtils.parseMap(json);
            return map != null ? map : Collections.emptyMap();
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }
}
