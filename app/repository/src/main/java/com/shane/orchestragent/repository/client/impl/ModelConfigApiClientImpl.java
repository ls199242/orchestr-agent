package com.shane.orchestragent.repository.client.impl;

import com.shane.orchestragent.common.utils.JsonUtils;
import com.shane.orchestragent.repository.client.ModelConfigApiClient;
import com.shane.orchestragent.repository.model.ModelConfigDO;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 大模型元数据与接入配置远程接口客户端实现
 *
 * @author Shane
 */
@Service("modelConfigApiClient")
public class ModelConfigApiClientImpl extends AbstractApiClient<ModelConfigDO> implements ModelConfigApiClient {

    @Override
    protected void initialize() {
        this.initUri("/api/model");
    }

    @Override
    public List<ModelConfigDO> list() {
        try {
            String url = getUrl();
            String resp = this.mngApiClient.callApi(url, null);
            ModelConfigRespDTO respDTO = JsonUtils.parseObject(resp, ModelConfigRespDTO.class);
            return respDTO != null ? respDTO.getData() : null;
        } catch (Exception e) {
            logger.warn("[ModelConfigApiClient] 请求远程大模型配置失败或离线: url={}, error={}", getUrl(), e.getMessage());
            return null;
        }
    }

    public static class ModelConfigRespDTO extends MngRespDTO<List<ModelConfigDO>> {
    }
}
