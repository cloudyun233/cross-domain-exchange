package com.cde.service.impl;

import com.cde.dto.DomainTreeNode;
import com.cde.exception.BusinessException;
import com.cde.mqtt.MqttClientService;
import com.cde.service.AuditService;
import com.cde.service.DomainService;
import com.cde.service.TopicService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TopicServiceImpl implements TopicService {

    private final MqttClientService mqttClientService;
    private final AuditService auditService;
    private final DomainService domainService;

    @Override
    public void publishMsg(String topic, String payload, int qos, boolean retain, String username, String token) {
        try {
            mqttClientService.connectForUser(username, token);
            mqttClientService.publishForUser(username, topic, payload, qos, retain);
            auditService.log(username, "publish",
                    String.format("发布消息到主题 %s, QoS=%d, Retain=%b, 内容长度=%d", topic, qos, retain, payload.length()),
                    "backend");
            log.info("消息发布成功: topic={}, qos={}, retain={}, user={}", topic, qos, retain, username);
        } catch (BusinessException e) {
            if (e.getStatus() == HttpStatus.FORBIDDEN) {
                auditService.log(username, "acl_deny",
                        String.format("非法操作拦截：用户 %s 无publish权限, 主题=%s", username, topic),
                        "backend");
            } else {
                auditService.log(username, "publish_fail",
                        String.format("发布失败: topic=%s, 原因=%s", topic, e.getMessage()),
                        "backend");
            }
            throw e;
        } catch (Exception e) {
            auditService.log(username, "publish_fail",
                    String.format("发布失败: topic=%s, 原因=%s", topic, e.getMessage()),
                    "backend");
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "消息发布失败");
        }
    }

    @Override
    public List<DomainTreeNode> buildDomainTopicTree() {
        return domainService.buildDomainTree();
    }
}
