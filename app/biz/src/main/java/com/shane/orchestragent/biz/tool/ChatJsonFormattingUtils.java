package com.shane.orchestragent.biz.tool;

/**
 * 大模型 JSON 输出格式化与 Markdown 清洗工具
 *
 * @author Shane
 */
public class ChatJsonFormattingUtils {

    private static final String MD_JSON_PREFIX = "```json";
    private static final String MD_PREFIX = "```";
    private static final String MD_SUFFIX = "```";

    public static String format(String raw) {
        if (raw == null || raw.isEmpty()) {
            return raw;
        }

        String code = raw.trim();
        if (code.startsWith(MD_JSON_PREFIX)) {
            code = code.substring(MD_JSON_PREFIX.length());
        } else if (code.startsWith(MD_PREFIX)) {
            code = code.substring(MD_PREFIX.length());
        }

        if (code.endsWith(MD_SUFFIX)) {
            code = code.substring(0, code.length() - MD_SUFFIX.length());
        }

        return code.trim();
    }
}
