package com.shane.orchestragent.repository.client.impl;

import com.shane.orchestragent.common.utils.JsonUtils;
import com.shane.orchestragent.repository.client.AgentConfigApiClient;
import com.shane.orchestragent.repository.model.AgentConfigDO;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 智能体元数据配置远程接口客户端实现
 *
 * @author Shane
 */
@Service("agentConfigApiClient")
public class AgentConfigApiClientImpl extends AbstractApiClient<AgentConfigDO> implements AgentConfigApiClient {

    @Override
    protected void initialize() {
        this.initUri("/api/agent");
    }

    @Override
    public List<AgentConfigDO> list() {
        try {
            String url = getUrl();
            String resp = this.mngApiClient.callApi(url, null);
            AgentConfigRespDTO respDTO = JsonUtils.parseObject(resp, AgentConfigRespDTO.class);
            return respDTO != null ? respDTO.getData() : null;
        } catch (Exception e) {
            logger.warn("[AgentConfigApiClient] 请求远程配置失败或离线: url={}, error={}", getUrl(), e.getMessage());
            return null;
        }
    }

    public static class AgentConfigRespDTO extends MngRespDTO<List<AgentConfigDO>> {
    }
}
