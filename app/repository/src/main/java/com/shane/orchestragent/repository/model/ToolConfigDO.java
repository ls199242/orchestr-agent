package com.shane.orchestragent.repository.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Map;

/**
 * 工具元数据配置实体
 * 描述外部调用工具（API / MCP）的签名、参数结构与端点
 *
 * @author Shane
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolConfigDO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 工具唯一名称（英文标识） */
    private String name;

    /** 工具业务编码 */
    private String code;

    /** 工具中文标题 */
    private String title;

    /** 工具功能详细描述（用于 LLM Tool Use 意图匹配） */
    private String description;

    /** 工具调用目标 HTTP/RPC 端点地址 */
    private String endpoint;

    /** 请求入参 JSON Schema 定义 */
    private String requestJsonSchema;

    /** 响应出参 JSON Schema 定义 */
    private String responseJsonSchema;

    /** 附加静态参数表 */
    private Map<String, Object> parameters;
}
