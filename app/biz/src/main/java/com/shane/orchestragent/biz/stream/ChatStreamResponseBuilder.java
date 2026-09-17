package com.shane.orchestragent.biz.stream;

import com.shane.orchestragent.common.exception.BizException;
import com.shane.orchestragent.integration.llm.model.ChatResponseVO;

/**
 * 流式大模型响应拼装器
 *
 * @author Shane
 */
public interface ChatStreamResponseBuilder {

    /**
     * 追加流式 chunk 并返回当前增量解析
     *
     * @param chunk 文本 chunk
     * @return 解析后的 ChatResponseVO
     * @throws BizException 异常
     */
    ChatResponseVO append(String chunk) throws BizException;

    /**
     * 构建最终汇总 ChatResponseVO
     *
     * @return 汇总响应
     */
    ChatResponseVO build();
}
