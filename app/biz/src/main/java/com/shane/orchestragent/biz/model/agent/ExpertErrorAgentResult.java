package com.shane.orchestragent.biz.model.agent;

import com.shane.orchestragent.common.utils.JsonUtils;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Map;

/**
 * 专精智能体错误结果解析
 *
 * @author Shane
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExpertErrorAgentResult implements Serializable {

    private String errorCode;
    private String errorMessage;

    public static ExpertErrorAgentResult format(String output) {
        if (output == null || !output.contains("errorCode")) {
            return null;
        }
        try {
            Map<String, Object> map = JsonUtils.parseMap(output);
            if (map.containsKey("errorCode")) {
                String code = String.valueOf(map.get("errorCode"));
                String msg = String.valueOf(map.getOrDefault("errorMessage", "Expert execution failed"));
                return new ExpertErrorAgentResult(code, msg);
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
