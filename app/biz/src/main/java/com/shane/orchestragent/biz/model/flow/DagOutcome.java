package com.shane.orchestragent.biz.model.flow;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 单个 DAG 步骤执行产出物
 *
 * @author Shane
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DagOutcome implements Serializable {

    /** 步骤 ID */
    private String stepId;

    /** 结构化解析后的产出 (Map 或原始字符串) */
    private Object output;

    /** 原始输出字符串 */
    private String rawOutput;

    /** 错误信息 (若执行失败) */
    private String error;

    /** 执行尝试次数 */
    private int attempts;
}
