package com.cde.service;

import com.cde.dto.DomainTreeNode;

import java.util.List;

/**
 * 主题服务接口
 */
public interface TopicService {

    /** 发布消息到 MQTT Broker */
    void publishMsg(String topic, String payload, int qos, boolean retain, String username, String token);

    /** 构建安全域主题树 */
    List<DomainTreeNode> buildDomainTopicTree();
}
