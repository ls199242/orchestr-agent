package com.shane.orchestragent.web.converter;

import com.shane.orchestragent.biz.model.vo.AgentChatRequestVO;
import com.shane.orchestragent.biz.model.vo.AgentInvokeRequestVO;
import com.shane.orchestragent.biz.model.vo.AgentInvokeResponseVO;
import com.shane.orchestragent.web.dto.AgentChatRequestDTO;
import com.shane.orchestragent.web.dto.AgentInvokeRequestDTO;
import com.shane.orchestragent.web.dto.AgentInvokeResponseDTO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

/**
 * 表现层与业务层模型转换映射器 (DTO <-> VO)
 * 基于 MapStruct 自动生成编译期映射实现，消除手写属性转换与反射开销
 * 负责 Controller 层的 DTO 与 Biz 层的 VO 之间的高性能自动转换
 *
 * @author Shane
 */
@Mapper(componentModel = "spring")
public interface AgentApiMapping {

    /** 实例常量，便于非 Spring 容器环境或单元测试直接获取使用 */
    AgentApiMapping INSTANCE = Mappers.getMapper(AgentApiMapping.class);

    /**
     * 将外部调用的 AgentInvokeRequestDTO 转换为业务内部使用的 AgentInvokeRequestVO
     *
     * @param dto 调用请求 DTO
     * @return 业务层请求 VO
     */
    AgentInvokeRequestVO dto2v(AgentInvokeRequestDTO dto);

    /**
     * 将业务内部产出的 AgentInvokeResponseVO 转换为向调用方返回的 AgentInvokeResponseDTO
     *
     * @param vo 业务层响应 VO
     * @return 表现层响应 DTO
     */
    AgentInvokeResponseDTO v2dto(AgentInvokeResponseVO vo);

    /**
     * 将外部流式会话请求 AgentChatRequestDTO 转换为业务内部使用的 AgentChatRequestVO
     *
     * @param dto 流式会话请求 DTO
     * @return 业务层会话请求 VO
     */
    AgentChatRequestVO dto2ChatVo(AgentChatRequestDTO dto);
}
