package com.shane.orchestragent.prompt.service.impl;

import com.shane.orchestragent.common.exception.BizException;
import com.shane.orchestragent.prompt.render.PromptRender;
import com.shane.orchestragent.prompt.service.PromptService;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 提示词服务实现
 *
 * @author Shane
 */
@Service
public class PromptServiceImpl implements PromptService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(PromptServiceImpl.class);

    private final PromptRender promptRender;
    private final Map<String, String> templateCache = new java.util.concurrent.ConcurrentHashMap<>();

    public PromptServiceImpl(PromptRender promptRender) {
        this.promptRender = promptRender;
    }

    @Override
    public String renderPrompt(String template, Map<String, Object> model) throws BizException {
        return promptRender.render(template, model);
    }

    @Override
    public String loadClasspathTemplate(String templateName) {
        if (org.apache.commons.lang3.StringUtils.isBlank(templateName)) {
            return "";
        }
        return templateCache.computeIfAbsent(templateName, name -> {
            String cleanName = name.toLowerCase().replace(".md", "");
            String path = "templates/" + cleanName + ".md";
            try (java.io.InputStream is = getClass().getClassLoader().getResourceAsStream(path)) {
                if (is != null) {
                    String content = new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                    log.info("[CONFIG][PROMPT] 成功加载提示词模板: {} (字符数: {})", path, content.length());
                    return content;
                }
            } catch (Exception e) {
                log.warn("[CONFIG][PROMPT] 加载类路径提示词模板失败: {}", path, e);
            }
            return "";
        });
    }
}
