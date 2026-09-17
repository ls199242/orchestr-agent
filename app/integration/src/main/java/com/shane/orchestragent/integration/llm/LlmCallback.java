package com.shane.orchestragent.integration.llm;

import com.shane.orchestragent.integration.llm.model.ChatResponseVO;

/**
 * 大模型流式/异步回调接口
 *
 * @author Shane
 */
public interface LlmCallback {

    /** 收到数据片段 */
    void onData(ChatResponseVO data);

    /** 交互完成 */
    void onComplete(ChatResponseVO data);

    /** 异常失败 */
    void onError(Throwable e);
}
