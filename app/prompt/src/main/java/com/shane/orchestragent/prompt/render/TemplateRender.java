package com.shane.orchestragent.prompt.render;

import com.shane.orchestragent.common.exception.BizException;
import com.shane.orchestragent.common.utils.JsonUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 模板渲染引擎实现 (支持 ${var} 与 {{var}} 动态插值与对象 JSON 展开)
 *
 * @author Shane
 */
@Component
public class TemplateRender implements PromptRender {

    private static final Pattern PATTERN = Pattern.compile("(\\$\\{([^}]+)}|\\{\\{([^}]+)}})");

    @Override
    public String render(String template, Map<String, Object> model) throws BizException {
        if (StringUtils.isEmpty(template)) {
            return StringUtils.EMPTY;
        }
        if (model == null || model.isEmpty()) {
            return template;
        }

        Matcher matcher = PATTERN.matcher(template);
        StringBuilder sb = new StringBuilder();

        while (matcher.find()) {
            String key = matcher.group(2) != null ? matcher.group(2).trim() : matcher.group(3).trim();
            Object val = model.get(key);
            String replacement = "";
            if (val != null) {
                if (val instanceof String || val instanceof Number || val instanceof Boolean) {
                    replacement = String.valueOf(val);
                } else {
                    replacement = JsonUtils.toJsonString(val);
                }
            }
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement != null ? replacement : ""));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
}
