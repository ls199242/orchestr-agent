package com.shane.orchestragent.repository.client.impl;

import com.shane.orchestragent.common.utils.JsonUtils;
import com.shane.orchestragent.repository.client.PromptGroupConfigApiClient;
import com.shane.orchestragent.repository.model.PromptGroupConfigDO;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 提示词组编排绑定远程接口客户端实现
 *
 * @author Shane
 */
@Service("promptGroupConfigApiClient")
public class PromptGroupConfigApiClientImpl extends AbstractApiClient<PromptGroupConfigDO> implements PromptGroupConfigApiClient {

    @Override
    protected void initialize() {
        this.initUri("/api/prompt-group");
    }

    @Override
    public List<PromptGroupConfigDO> list() {
        try {
            String url = getUrl();
            String resp = this.mngApiClient.callApi(url, null);
            PromptGroupConfigRespDTO respDTO = JsonUtils.parseObject(resp, PromptGroupConfigRespDTO.class);
            return respDTO != null ? respDTO.getData() : null;
        } catch (Exception e) {
            logger.warn("[PromptGroupConfigApiClient] 请求远程提示词组失败或离线: url={}, error={}", getUrl(), e.getMessage());
            return null;
        }
    }

    public static class PromptGroupConfigRespDTO extends MngRespDTO<List<PromptGroupConfigDO>> {
    }
}
