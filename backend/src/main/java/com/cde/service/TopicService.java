package com.cde.service;

import com.cde.dto.DomainTreeNode;

import java.util.List;

/**
 * 主题服务接口
 */
public interface TopicService {

    /**
     * 发布消息到MQTT Broker
     *
     * <p>完整发布流程：建立MQTT连接 → 发布消息 → 记录审计日志。
     * 若ACL校验拒绝（HTTP 403），则单独记录acl_deny审计事件。</p>
     */
    void publishMsg(String topic, String payload, int qos, boolean retain, String username, String token);

    /**
     * 构建安全域主题树
     *
     * <p>委托DomainService构建域树，每个节点附带topicPath和subscribeTopic字段，
     * 供前端展示可订阅的主题层级。</p>
     */
    List<DomainTreeNode> buildDomainTopicTree();
}
