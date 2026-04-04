package com.cde.service;

import java.util.List;
import java.util.Map;

/**
 * 主题服务接口 (论文4.5.3类图: TopicService)
 */
public interface TopicService {

    /** 发布消息到MQTT Broker (使用用户级连接) */
    void publishMsg(String topic, String payload, int qos, String username, String token);

    /** 构建安全域主题树 */
    List<Map<String, Object>> buildDomainTopicTree();
}
