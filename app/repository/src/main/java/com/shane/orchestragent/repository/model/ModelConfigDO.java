package com.shane.orchestragent.repository.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 大模型元数据与接入配置实体
 * 包含模型标识、上下文窗口限制、调用端点、API Key 与默认采样参数
 *
 * @author Shane
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelConfigDO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 模型唯一编码（如 "gpt-4o", "deepseek-chat"） */
    private String code;

    /** 模型展示名称 */
    private String name;

    /** 上下文窗口最大 Token 数量 */
    private Integer contextMaxTokens;

    /** 接入端点地址（如 "https://api.openai.com/v1/chat/completions"） */
    private String endpoint;

    /** 访问凭证 API Key */
    private String apiKey;

    /** 默认发散采样温度 (0.0 ~ 2.0) */
    private Double temperature;

    /** 是否支持视觉图像多模态输入 */
    private boolean supportImage;

    /** 模型功能标签（如 ["chat", "reasoning", "vision"]） */
    private List<String> tags;
}
