package com.shane.orchestragent.prompt.service.impl;

import com.shane.orchestragent.common.exception.BizException;
import com.shane.orchestragent.prompt.render.PromptRender;
import com.shane.orchestragent.prompt.service.PromptService;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 提示词服务实现
 * 专职负责运行期基于数据模型的纯内存插值渲染
 *
 * @author Shane
 */
@Service("promptService")
public class PromptServiceImpl implements PromptService {

    private final PromptRender promptRender;

    public PromptServiceImpl(PromptRender promptRender) {
        this.promptRender = promptRender;
    }

    @Override
    public String renderPrompt(String template, Map<String, Object> model) throws BizException {
        return promptRender.render(template, model);
    }
}
