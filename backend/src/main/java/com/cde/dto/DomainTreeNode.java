package com.cde.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DomainTreeNode {
    private String key;
    private String title;
    private Long domainId;
    private String domainCode;
    private String domainName;
    private String pathName;
    private String topicPath;
    private String subscribeTopic;
    private Boolean isLeaf;
    private List<DomainTreeNode> children;
}
