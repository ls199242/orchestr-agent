package com.shane.orchestragent.repository.client.impl;

import com.shane.orchestragent.common.utils.JsonUtils;
import com.shane.orchestragent.repository.client.StrategyConfigApiClient;
import com.shane.orchestragent.repository.model.StrategyConfigDO;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 编排策略配置远程接口客户端实现
 *
 * @author Shane
 */
@Service("strategyConfigApiClient")
public class StrategyConfigApiClientImpl extends AbstractApiClient<StrategyConfigDO> implements StrategyConfigApiClient {

    @Override
    protected void initialize() {
        this.initUri("/api/strategy");
    }

    @Override
    public List<StrategyConfigDO> list() {
        try {
            String url = getUrl();
            String resp = this.mngApiClient.callApi(url, null);
            MngStrategyConfigRespDTO respDTO = JsonUtils.parseObject(resp, MngStrategyConfigRespDTO.class);
            return respDTO != null ? respDTO.getData() : null;
        } catch (Exception e) {
            logger.warn("[StrategyConfigApiClient] 请求远程配置失败或离线: url={}, error={}", getUrl(), e.getMessage());
            return null;
        }
    }

    public static class MngStrategyConfigRespDTO extends MngRespDTO<List<StrategyConfigDO>> {
    }
}
