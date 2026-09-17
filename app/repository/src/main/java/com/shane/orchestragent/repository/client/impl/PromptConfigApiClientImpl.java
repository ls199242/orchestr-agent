package com.shane.orchestragent.repository.client.impl;

import com.shane.orchestragent.common.utils.JsonUtils;
import com.shane.orchestragent.repository.client.PromptConfigApiClient;
import com.shane.orchestragent.repository.model.PromptConfigDO;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 提示词模板配置远程接口客户端实现
 *
 * @author Shane
 */
@Service("promptConfigApiClient")
public class PromptConfigApiClientImpl extends AbstractApiClient<PromptConfigDO> implements PromptConfigApiClient {

    @Override
    protected void initialize() {
        this.initUri("/api/prompt");
    }

    @Override
    public List<PromptConfigDO> list() {
        try {
            String url = getUrl();
            String resp = this.mngApiClient.callApi(url, null);
            PromptConfigRespDTO respDTO = JsonUtils.parseObject(resp, PromptConfigRespDTO.class);
            return respDTO != null ? respDTO.getData() : null;
        } catch (Exception e) {
            logger.warn("[PromptConfigApiClient] 请求远程提示词模板失败或离线: url={}, error={}", getUrl(), e.getMessage());
            return null;
        }
    }

    public static class PromptConfigRespDTO extends MngRespDTO<List<PromptConfigDO>> {
    }
}
