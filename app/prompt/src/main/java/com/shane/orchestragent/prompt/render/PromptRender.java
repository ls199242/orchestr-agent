package com.shane.orchestragent.prompt.render;

import com.shane.orchestragent.common.exception.BizException;

import java.util.Map;

/**
 * 提示词渲染器接口
 *
 * @author Shane
 */
public interface PromptRender {

    /**
     * 渲染模板
     *
     * @param template 提示词模板
     * @param model    数据模型
     * @return 渲染后的提示词
     * @throws BizException 渲染异常
     */
    String render(String template, Map<String, Object> model) throws BizException;
}
