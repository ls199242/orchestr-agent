package com.shane.orchestragent.repository.client.impl;

import com.shane.orchestragent.common.utils.JsonUtils;
import com.shane.orchestragent.repository.client.ToolConfigApiClient;
import com.shane.orchestragent.repository.model.ToolConfigDO;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 工具元数据配置远程接口客户端实现
 *
 * @author Shane
 */
@Service("toolConfigApiClient")
public class ToolConfigApiClientImpl extends AbstractApiClient<ToolConfigDO> implements ToolConfigApiClient {

    @Override
    protected void initialize() {
        this.initUri("/api/tool");
    }

    @Override
    public List<ToolConfigDO> list() {
        try {
            String url = getUrl();
            String resp = this.mngApiClient.callApi(url, null);
            ToolConfigRespDTO respDTO = JsonUtils.parseObject(resp, ToolConfigRespDTO.class);
            return respDTO != null ? respDTO.getData() : null;
        } catch (Exception e) {
            logger.warn("[ToolConfigApiClient] 请求远程配置失败或离线: url={}, error={}", getUrl(), e.getMessage());
            return null;
        }
    }

    public static class ToolConfigRespDTO extends MngRespDTO<List<ToolConfigDO>> {
    }
}
