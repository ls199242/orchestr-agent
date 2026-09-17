package com.shane.orchestragent.biz.stream.impl;

import com.shane.orchestragent.biz.stream.ChatStreamResponseBuilder;
import com.shane.orchestragent.common.exception.BizException;
import com.shane.orchestragent.common.utils.JsonUtils;
import com.shane.orchestragent.integration.llm.model.ChatMessageVO;
import com.shane.orchestragent.integration.llm.model.ChatResponseVO;
import org.apache.commons.lang3.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 默认流式大模型响应拼装实现
 *
 * @author Shane
 */
public class DefaultChatStreamResponseBuilder implements ChatStreamResponseBuilder {

    private final StringBuilder contentBuilder = new StringBuilder();
    private final StringBuilder reasoningContentBuilder = new StringBuilder();

    private final AtomicReference<String> id = new AtomicReference<>();
    private final AtomicReference<String> model = new AtomicReference<>();

    @SuppressWarnings("unchecked")
    @Override
    public ChatResponseVO append(String chunk) throws BizException {
        if (StringUtils.isBlank(chunk)) {
            return null;
        }

        String data = chunk.trim();
        if (data.startsWith("data:")) {
            data = data.substring("data:".length()).trim();
        }
        if ("[DONE]".equalsIgnoreCase(data) || data.isEmpty()) {
            return null;
        }

        try {
            Map<String, Object> map = JsonUtils.parseMap(data);
            if (map == null) {
                return null;
            }

            if (map.get("id") != null && id.get() == null) {
                id.set(String.valueOf(map.get("id")));
            }
            if (map.get("model") != null && model.get() == null) {
                model.set(String.valueOf(map.get("model")));
            }

            List<Map<String, Object>> choices = (List<Map<String, Object>>) map.get("choices");
            if (choices != null && !choices.isEmpty()) {
                Map<String, Object> choice = choices.get(0);
                Map<String, Object> delta = (Map<String, Object>) choice.get("delta");
                if (delta != null) {
                    String deltaContent = (String) delta.get("content");
                    String deltaReasoning = (String) delta.get("reasoning_content");

                    if (StringUtils.isNotEmpty(deltaContent)) {
                        contentBuilder.append(deltaContent);
                    }
                    if (StringUtils.isNotEmpty(deltaReasoning)) {
                        reasoningContentBuilder.append(deltaReasoning);
                    }

                    return ChatResponseVO.ofSingle(
                            deltaContent != null ? deltaContent : "",
                            deltaReasoning != null ? deltaReasoning : ""
                    );
                }
            }
        } catch (Exception e) {
            // 非 JSON 格式 chunk (如模拟纯文本)，直接作为 content 追加
            contentBuilder.append(chunk);
            return ChatResponseVO.ofSingle(chunk, null);
        }

        return null;
    }

    @Override
    public ChatResponseVO build() {
        ChatMessageVO messageVO = ChatMessageVO.builder()
                .role("assistant")
                .content(contentBuilder.toString())
                .reasoningContent(reasoningContentBuilder.length() > 0 ? reasoningContentBuilder.toString() : null)
                .build();

        return ChatResponseVO.builder()
                .id(id.get())
                .model(model.get())
                .chatMessages(Collections.singletonList(messageVO))
                .build();
    }
}
