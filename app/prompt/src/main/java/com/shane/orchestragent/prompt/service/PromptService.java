package com.shane.orchestragent.prompt.service;

import com.shane.orchestragent.common.exception.BizException;

import java.util.Map;

/**
 * 提示词服务接口
 *
 * @author Shane
 */
public interface PromptService {

    /**
     * 渲染提示词
     *
     * @param template 提示词模板
     * @param model    数据模型
     * @return 渲染结果
     * @throws BizException 渲染异常
     */
    String renderPrompt(String template, Map<String, Object> model) throws BizException;

    /**
     * 从类路径 resources/templates/ 加载默认提示词模板
     *
     * @param templateName 模板名称 (如 conductor, planner, evaluator 等)
     * @return 模板文本
     */
    String loadClasspathTemplate(String templateName);
}
