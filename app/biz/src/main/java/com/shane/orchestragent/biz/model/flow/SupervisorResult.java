package com.shane.orchestragent.biz.model.flow;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Map;

/**
 * 步进调度指挥产出结果
 *
 * @author Shane
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SupervisorResult implements Serializable {

    private static final String FINISH_FLAG = "FINISH";
    private static final String ERROR_FLAG = "ERROR";

    /** 下一步派发的智能体名称，或 FINISH / ERROR */
    private String next;

    /** 传递给下一个智能体的入参 */
    private Map<String, Object> request;

    public boolean isFinish() {
        return FINISH_FLAG.equalsIgnoreCase(next);
    }

    public boolean isError() {
        return ERROR_FLAG.equalsIgnoreCase(next);
    }
}
