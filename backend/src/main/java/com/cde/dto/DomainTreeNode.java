package com.cde.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 领域-主题层级树节点，用于前端树形控件渲染领域与主题的层级关系。
 * <p>
 * 关键设计：
 * <ul>
 *   <li>topicPath — 精确的MQTT主题路径，如 "domainA/sensor/temp"</li>
 *   <li>subscribeTopic — 带通配符的订阅主题，叶子节点使用自身topicPath，非叶子节点使用"#+/"后缀通配</li>
 *   <li>isLeaf — 决定订阅策略：叶子节点订阅精确主题，非叶子节点订阅通配主题</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DomainTreeNode {
    /** 节点唯一标识，通常为 "domain-{id}" 或 "topic-{id}" */
    private String key;
    /** 节点显示名称 */
    private String title;
    /** 所属领域ID */
    private Long domainId;
    /** 领域编码 */
    private String domainCode;
    /** 领域名称 */
    private String domainName;
    /** 路径名称，用于展示层级路径 */
    private String pathName;
    /** 精确的MQTT主题路径，如 "domainA/sensor/temp" */
    private String topicPath;
    /** 实际订阅主题，叶子节点为精确路径，非叶子节点带通配符 */
    private String subscribeTopic;
    /** 是否为叶子节点，决定订阅策略 */
    private Boolean isLeaf;
    /** 子节点列表 */
    private List<DomainTreeNode> children;
}
