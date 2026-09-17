package com.shane.orchestragent.biz.service;

import com.shane.orchestragent.biz.model.flow.FlowProcessText;
import com.shane.orchestragent.biz.model.request.RecommendRequestVO;
import com.shane.orchestragent.common.enums.FlowStateEnum;
import com.shane.orchestragent.repository.model.StrategyConfigDO;

import java.util.List;

/**
 * 策略流程状态与生命周期服务
 *
 * @author Shane
 */
public interface FlowService {

    /**
     * 生成或获取流程 ID
     */
    String createFlowId(RecommendRequestVO request, StrategyConfigDO strategyConfig);

    /**
     * 获取流程运行状态
     */
    FlowStateEnum getState(String flowId);

    /**
     * 标记流程运行状态
     */
    void markState(String flowId, FlowStateEnum state);

    /**
     * 保存步骤文案
     */
    void saveProcessText(String flowId, FlowProcessText processText);

    /**
     * 获取步骤文案列表
     */
    List<FlowProcessText> getProcessTexts(String flowId);

    /**
     * 获取流程最终结果
     */
    String getResult(String flowId);

    /**
     * 保存流程最终结果
     */
    void setResult(String flowId, String result);

    /**
     * 清理流程数据
     */
    void clear(String flowId);
}
