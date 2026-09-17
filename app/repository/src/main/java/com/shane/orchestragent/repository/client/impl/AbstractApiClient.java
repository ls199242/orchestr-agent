package com.shane.orchestragent.repository.client.impl;

import com.shane.orchestragent.repository.client.ApiClient;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;

/**
 * 远程配置客户端抽象基类
 * 统一封装管理端服务地址解析、HTTP 通信器初始化以及生命周期管理
 *
 * @param <T> 配置实体模型类型
 * @author Shane
 */
public abstract class AbstractApiClient<T> implements ApiClient<T>, InitializingBean {

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    /** 默认管理后台服务地址（对应 Python Mock 控制台或远程配置中心） */
    @Value("${orchestr.manager.api-url:http://127.0.0.1:8000}")
    protected String baseUrl = "http://127.0.0.1:8000";

    /** 当前客户端绑定的相对 URI 路径 */
    protected String apiUri;

    /** 统一 HTTP 通信执行器 */
    protected MngApiClient mngApiClient;

    /**
     * 子类特定初始化钩子（设置相对路径与特定参数）
     */
    protected abstract void initialize();

    /**
     * 设置 API 接口相对 URI
     *
     * @param apiUri 接口 URI，如 "/api/strategy"
     */
    protected void initUri(String apiUri) {
        this.apiUri = apiUri;
    }

    /**
     * 获取当前接口调用的完整目标 URL 地址
     *
     * @return 完整 URL 字符串
     */
    public String getUrl() {
        String base = StringUtils.removeEnd(baseUrl, "/");
        String uri = StringUtils.prependIfMissing(apiUri, "/");
        return base + uri;
    }

    /**
     * 手动注入或设置 BaseUrl（主要用于测试与多环境切换）
     *
     * @param baseUrl 管理服务 BaseUrl
     */
    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        this.mngApiClient = new MngApiClient();
        this.initialize();
    }
}
