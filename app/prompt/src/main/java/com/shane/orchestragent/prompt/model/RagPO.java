package com.shane.orchestragent.prompt.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 知识库检索 PO
 *
 * @author Shane
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RagPO implements Serializable {

    private String name;
    private String knowledgeBaseId;
    private String description;
    private int topK;
}
