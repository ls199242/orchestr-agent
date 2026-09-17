package com.shane.orchestragent.prompt.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Map;

/**
 * 工具元数据 PO
 *
 * @author Shane
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolPO implements Serializable {

    private String name;
    private String description;
    private Map<String, Object> parameters;
}
