package com.shane.orchestragent.biz.model.agent;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

/**
 * 智能体执行结果基类
 *
 * @author Shane
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AgentResult implements Serializable {

    /** 输出结果 */
    private String output;

    /** 思考过程 / 模型决策原因 */
    private String reasoningContent;
}
