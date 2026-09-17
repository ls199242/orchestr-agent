package com.shane.orchestragent.repository.client.impl;

import com.shane.orchestragent.common.utils.JsonUtils;
import com.shane.orchestragent.repository.client.DictApiClient;
import com.shane.orchestragent.repository.model.DictDO;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 全局运行字典远程接口客户端实现
 *
 * @author Shane
 */
@Service("dictApiClient")
public class DictApiClientImpl extends AbstractApiClient<DictDO> implements DictApiClient {

    @Override
    protected void initialize() {
        this.initUri("/api/dict");
    }

    @Override
    public List<DictDO> list() {
        try {
            String url = getUrl();
            String resp = this.mngApiClient.callApi(url, null);
            DictRespDTO respDTO = JsonUtils.parseObject(resp, DictRespDTO.class);
            return respDTO != null ? respDTO.getData() : null;
        } catch (Exception e) {
            logger.warn("[DictApiClient] 请求远程字典失败或离线: url={}, error={}", getUrl(), e.getMessage());
            return null;
        }
    }

    public static class DictRespDTO extends MngRespDTO<List<DictDO>> {
    }
}
