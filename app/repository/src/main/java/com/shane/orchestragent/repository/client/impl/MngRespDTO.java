package com.shane.orchestragent.repository.client.impl;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 外部配置管理接口标准响应数据传输对象 (DTO)
 *
 * @param <T> 响应业务数据负载类型
 * @author Shane
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MngRespDTO<T> implements Serializable {

    /** 请求处理是否成功 */
    protected boolean result;

    /** 提示或错误文本信息 */
    protected String message;

    /** 错误码（成功时通常为空） */
    protected String errorCode;

    /** 业务负载数据主体 */
    protected T data;
}
