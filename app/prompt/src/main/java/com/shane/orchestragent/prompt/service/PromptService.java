package com.shane.orchestragent.prompt.service;

import com.shane.orchestragent.common.exception.BizException;

import java.util.Map;

/**
 * 提示词服务接口
 * 负责智能体提示词运行期纯内存插值渲染
 *
 * @author Shane
 */
public interface PromptService {

    /**
     * 渲染提示词模板
     *
     * @param template 提示词模板正文 (支持 ${var} 与 {{var}} 动态插槽)
     * @param model    数据模型上下文
     * @return 渲染后的纯文本提示词
     * @throws BizException 渲染异常
     */
    String renderPrompt(String template, Map<String, Object> model) throws BizException;
}
