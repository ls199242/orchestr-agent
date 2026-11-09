package com.shane.orchestragent.biz.eval.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.*;

/**
 * 策略配置全景元数据导出契约模型 (供外部 eval 系统只读查询并自动派生硬规则)
 * <p>
 * 导出信息包括：
 * <ul>
 *   <li>代码 Commit 与导出时间戳</li>
 *   <li>策略与 Expert、Tool、RAG 的拓扑绑定关系</li>
 *   <li>各 Expert 节点的必选属性 (required) 与超时/重试配置</li>
 *   <li>系统智能体当前提示词版本快照</li>
 * </ul>
 *
 * @author Shane
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StrategyMetadataDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 当前运行代码库的 Git Commit Hash (如 "c8a1f4e") */
    private String gitCommit;

    /** 元数据导出时刻 ISO 字符串 */
    private String exportedAt;

    /** 全量策略定义拓扑列表 */
    @Builder.Default
    private List<StrategyMetaItem> strategies = new ArrayList<>();

    /** 系统各角色提示词快照映射表 (角色编码 -> 版本/模型) */
    @Builder.Default
    private Map<String, String> promptsSnapshot = new HashMap<>();

    /**
     * 单个策略的完整定义元数据
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StrategyMetaItem implements Serializable {
        /** 策略业务编码 (如 "flight_nearby_recommend_strategy") */
        private String strategyCode;

        /** 策略中文名称 */
        private String name;

        /** 策略功能详细说明 */
        private String description;

        /** 流程拓扑类型 (REACT / MULTIPLE_CHAT) */
        private String topology;

        /** 全局最大执行步数限制 */
        private Integer maxStep;

        /** 战略验收失败最大自愈重试次数 */
        private Integer evalMaxRetry;

        /** 策略全局可用工具编码列表 */
        @Builder.Default
        private List<String> tools = new ArrayList<>();

        /** 策略纳管挂载的专精 Expert 列表 */
        @Builder.Default
        private List<ExpertMetaItem> experts = new ArrayList<>();

        /** 策略入参规范定义列表 */
        @Builder.Default
        private List<ParamMetaItem> params = new ArrayList<>();
    }

    /**
     * 单个专精 Expert 的元数据说明
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExpertMetaItem implements Serializable {
        /** Expert 业务编码 (如 "flight_recommend_expert") */
        private String code;

        /** Expert 中文名称 */
        private String name;

        /** 职责描述 */
        private String description;

        /** 是否为必要节点 (true: 失败中断流程并触发取消; false: 失败跳过并注入 missing 标记) */
        private boolean required;

        /** 单节点执行超时等待毫秒数 */
        private Long timeoutMs;

        /** 节点失败重试上限次数 */
        private Integer maxRetries;

        /** 该 Expert 允许调用的特定工具列表 (池合规校验源) */
        @Builder.Default
        private List<String> allowedTools = new ArrayList<>();

        /** 该 Expert 绑定的知识库/数据集列表 (触发伴生 RAG 节点) */
        @Builder.Default
        private List<String> ragDatasets = new ArrayList<>();
    }

    /**
     * 策略参数规范定义
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ParamMetaItem implements Serializable {
        /** 参数名 (如 "departureDate") */
        private String name;

        /** 数据类型 (string, number, boolean) */
        private String type;

        /** 业务语义说明 */
        private String description;

        /** 是否必填 */
        private boolean required;
    }
}
