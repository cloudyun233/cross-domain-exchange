package com.cde.service.impl;

import com.cde.mqtt.MqttClientService;
import com.cde.service.AuditService;
import com.cde.service.TopicService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 主题服务实现 (论文4.5.3: TopicServiceImpl)
 * 核心职责: 与 EMQX Broker 交互
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TopicServiceImpl implements TopicService {

    private final MqttClientService mqttClientService;
    private final AuditService auditService;

    @Override
    public void publishMsg(String topic, String payload, int qos, String username, String token) {
        try {
            mqttClientService.connectForUser(username, token);
            mqttClientService.publishForUser(username, topic, payload, qos);
            auditService.log(username, "publish",
                    String.format("发布消息到主题 %s, QoS=%d, 内容长度=%d", topic, qos, payload.length()),
                    "backend");
            log.info("消息发布成功: topic={}, qos={}, user={}", topic, qos, username);
        } catch (Exception e) {
            auditService.log(username, "publish_fail",
                    String.format("发布失败: topic=%s, 原因=%s", topic, e.getMessage()),
                    "backend");
            throw new RuntimeException("消息发布失败: " + e.getMessage(), e);
        }
    }

    @Override
    public List<Map<String, Object>> buildDomainTopicTree() {
        // 构建跨域主题层级树 (论文4.1.2)
        List<Map<String, Object>> tree = new ArrayList<>();

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("key", "/cross_domain");
        root.put("title", "跨域交换根节点");
        root.put("children", List.of(
            buildBranch("/cross_domain/gov", "政务域", List.of(
                buildLeaf("/cross_domain/gov/minzheng/population", "民政-人口数据"),
                buildLeaf("/cross_domain/gov/rensha/social_sec", "人社-社保数据")
            )),
            buildBranch("/cross_domain/medical", "医疗域", List.of(
                buildLeaf("/cross_domain/medical/hosp_swu/patient/update", "西南医院-患者更新"),
                buildLeaf("/cross_domain/medical/hosp_swu/record", "西南医院-脱敏病历")
            )),
            buildBranch("/cross_domain/enterprise", "企业域", List.of(
                buildLeaf("/cross_domain/enterprise/supply/order", "供应链-订单数据")
            ))
        ));
        tree.add(root);
        return tree;
    }

    private Map<String, Object> buildBranch(String key, String title, List<Map<String, Object>> children) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("key", key);
        node.put("title", title);
        node.put("children", children);
        return node;
    }

    private Map<String, Object> buildLeaf(String key, String title) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("key", key);
        node.put("title", title);
        node.put("isLeaf", true);
        return node;
    }
}
