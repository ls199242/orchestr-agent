package com.shane.orchestragent.biz.model.flow;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;

import java.io.Serializable;
import java.util.*;

/**
 * DAG 任务步骤定义实体
 *
 * @author Shane
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class FlowDagStep implements Serializable {

    /** 步骤唯一标识 (如 "s1", "s2") */
    private String id;

    /** 目标执行智能体或工具名称 */
    private String agent;

    /** 该步骤任务执行目标或说明 */
    private String goal;

    /** 输入参数声明映射 (支持字面量、"$global.x"、"$s1.output"、"$s1.output.y") */
    @Builder.Default
    private Map<String, String> inputs = new LinkedHashMap<>();

    /** 显式依赖的前序步骤 ID 列表 */
    @Builder.Default
    private List<String> dependsOn = new ArrayList<>();

    /** 期望输出契约包含的 Key 列表 (若非空则执行强校验) */
    @Builder.Default
    private Set<String> outputKeys = new LinkedHashSet<>();

    /** 最大重试次数 (默认 0) */
    @Builder.Default
    private int maxRetries = 0;

    /** 单步执行超时时间 (毫秒，默认 30000ms) */
    @Builder.Default
    private long timeoutMs = 30000L;

    /** 失败策略: ABORT 中止或 SKIP 跳过 (默认 ABORT) */
    @Builder.Default
    private OnFailureAction onFailure = OnFailureAction.ABORT;

    // 兼容历史字段 (适配 {"step": 1, "description": "..."})
    @JsonAlias({"step"})
    private Integer step;

    @JsonAlias({"description"})
    private String description;

    public String getId() {
        if (StringUtils.isNotBlank(id)) {
            return id;
        }
        if (step != null) {
            return "s" + step;
        }
        return "step_" + (agent != null ? agent : UUID.randomUUID().toString().substring(0, 8));
    }

    public String getGoal() {
        if (StringUtils.isNotBlank(goal)) {
            return goal;
        }
        return StringUtils.defaultString(description);
    }

    /**
     * 获取显式依赖与从引用表达式中推导出的全部依赖合集
     */
    public Set<String> allDeps() {
        Set<String> deps = new LinkedHashSet<>();
        if (dependsOn != null) {
            deps.addAll(dependsOn);
        }
        if (inputs != null) {
            for (String expr : inputs.values()) {
                String ref = extractRefStep(expr);
                if (ref != null) {
                    deps.add(ref);
                }
            }
        }
        return deps;
    }

    /**
     * 抽取表达式中的前序依赖步骤 ID (例如 "$s1.output.weather" -> "s1", "$global.x" -> null)
     */
    public static String extractRefStep(String expr) {
        if (StringUtils.isBlank(expr) || !expr.startsWith("$")) {
            return null;
        }
        String head = expr.substring(1).split("\\.")[0];
        return "global".equalsIgnoreCase(head) ? null : head;
    }
}
