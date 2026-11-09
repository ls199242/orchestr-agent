package com.shane.orchestragent.biz.eval.mock;

import com.shane.orchestragent.biz.eval.model.CaseMockBundleDTO;
import com.shane.orchestragent.common.exception.BizException;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 故障注入执行器 (Fault Injector)
 * <p>
 * 职责：
 * <ul>
 *   <li>在目标智能体或工具调用前拦截，判断是否命中声明的故障注入规则</li>
 *   <li>支持精确匹配第 N 次调用轮次 (onAttempt)，实现“首次失败、重试成功”或“首次超时中断”</li>
 *   <li>支持模拟 timeout (超时挂起触发中断看门狗)、error (抛出指定业务异常)、slow (模拟长延时)、empty (空响应)</li>
 * </ul>
 *
 * @author Shane
 */
public class FaultInjector {

    private static final Logger log = LoggerFactory.getLogger(FaultInjector.class);

    private final List<CaseMockBundleDTO.FaultInjectionRuleDTO> rules;
    private final Map<String, AtomicInteger> targetAttempts = new ConcurrentHashMap<>();

    /**
     * 构造故障注入执行器
     *
     * @param rules 故障注入规则列表
     */
    public FaultInjector(List<CaseMockBundleDTO.FaultInjectionRuleDTO> rules) {
        this.rules = rules != null ? rules : Collections.emptyList();
    }

    /**
     * 检查并触发故障
     *
     * @param target 目标标识（Agent 名称、步骤 ID、或 Tool 编码，如 "flight_preference_expert"）
     * @throws BizException 若注入了报错或超时异常
     */
    public void checkAndTriggerFault(String target) throws BizException {
        if (CollectionUtils.isEmpty(rules) || StringUtils.isBlank(target)) {
            return;
        }

        int currentAttempt = targetAttempts.computeIfAbsent(target.toLowerCase(), k -> new AtomicInteger(0)).incrementAndGet();

        for (CaseMockBundleDTO.FaultInjectionRuleDTO rule : rules) {
            if (rule.getTarget() != null && rule.getTarget().equalsIgnoreCase(target)) {
                int onAttempt = rule.getOnAttempt() > 0 ? rule.getOnAttempt() : 1;
                if (currentAttempt == onAttempt) {
                    executeFault(target, rule, currentAttempt);
                    return;
                }
            }
        }
    }

    /**
     * 实际执行指定故障类型的行为
     *
     * @param target  目标标识
     * @param rule    匹配中的故障规则
     * @param attempt 当前是第几次调用
     * @throws BizException 若为超时或报错故障
     */
    private void executeFault(String target, CaseMockBundleDTO.FaultInjectionRuleDTO rule, int attempt) throws BizException {
        String type = rule.getType() != null ? rule.getType().toLowerCase() : "error";
        log.warn("[FaultInjector] 命中故障注入规则: target={}, attempt={}, type={}", target, attempt, type);

        switch (type) {
            case "timeout" -> {
                long delay = rule.getDelayMs() != null && rule.getDelayMs() > 0 ? rule.getDelayMs() : 60000L;
                log.info("[FaultInjector] 模拟超时，正在挂起线程 {} ms...", delay);
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new BizException("INJECTED_TIMEOUT", "节点 [" + target + "] 执行超时打断 (故障注入)");
                }
                throw new BizException("INJECTED_TIMEOUT", "节点 [" + target + "] 执行超时 (故障注入)");
            }
            case "error" -> {
                String errorMsg = StringUtils.isNotBlank(rule.getErrorMessage())
                        ? rule.getErrorMessage()
                        : "节点 [" + target + "] 触发模拟异常错误 (故障注入)";
                throw new BizException("INJECTED_FAULT", errorMsg);
            }
            case "slow" -> {
                long delay = rule.getDelayMs() != null && rule.getDelayMs() > 0 ? rule.getDelayMs() : 3000L;
                log.info("[FaultInjector] 模拟慢响应，延迟 {} ms...", delay);
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            case "empty" -> {
                log.info("[FaultInjector] 模拟空响应");
            }
            default -> log.warn("[FaultInjector] 未知故障类型: {}", type);
        }
    }
}
