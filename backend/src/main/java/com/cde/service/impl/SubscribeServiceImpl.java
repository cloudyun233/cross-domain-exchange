package com.cde.service.impl;

import com.cde.mqtt.MqttClientService;
import com.cde.service.AuditService;
import com.cde.service.SubscribeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * SSE订阅推送服务实现
 * 后端代理订阅EMQX → 通过SSE推送到前端
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubscribeServiceImpl implements SubscribeService {

    private final MqttClientService mqttClientService;
    private final AuditService auditService;

    // clientId → connectionId → SseEmitter (支持同一客户端多SSE连接)
    private final Map<String, Map<String, SseEmitter>> emitters = new ConcurrentHashMap<>();
    // clientId → topic → List<connectionId> (跟踪哪些客户端连接订阅了哪些主题)
    private final Map<String, Map<String, CopyOnWriteArrayList<String>>> topicSubscribers = new ConcurrentHashMap<>();

    @Override
    public SseEmitter subscribe(String clientId, String connectionId, String topic, int qos) {
        // 创建SSE连接 (超时30分钟)
        SseEmitter emitter = new SseEmitter(30 * 60 * 1000L);

        // 存储emitter: clientId -> connectionId -> emitter
        emitters.computeIfAbsent(clientId, k -> new ConcurrentHashMap<>()).put(connectionId, emitter);

        // 记录订阅关系: clientId -> topic -> List<connectionId>
        topicSubscribers.computeIfAbsent(clientId, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(topic, k -> new CopyOnWriteArrayList<>())
                .add(connectionId);

        // 后端代理订阅EMQX
        mqttClientService.subscribe(topic, qos, (t, payload) -> {
            pushMessage(t, payload);
        });

        // 清理回调
        emitter.onCompletion(() -> cleanup(clientId, connectionId, topic));
        emitter.onTimeout(() -> cleanup(clientId, connectionId, topic));
        emitter.onError(e -> cleanup(clientId, connectionId, topic));

        auditService.log(clientId, "subscribe", "订阅主题: " + topic + ", QoS=" + qos, "backend");

        // 发送连接确认
        try {
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data(Map.of("message", "SSE连接已建立, 订阅主题: " + topic)));
        } catch (IOException e) {
            log.error("SSE发送连接确认失败", e);
        }

        return emitter;
    }

    @Override
    public void unsubscribe(String clientId, String connectionId, String topic) {
        cleanup(clientId, connectionId, topic);
        auditService.log(clientId, "unsubscribe", "取消订阅: " + topic, "backend");
    }

    @Override
    public void pushMessage(String topic, String payload) {
        // 遍历所有用户，推送消息
        topicSubscribers.forEach((clientId, userTopics) -> {
            userTopics.forEach((subscribedTopic, connectionIds) -> {
                if (matchesTopic(subscribedTopic, topic)) {
                    for (String connectionId : connectionIds) {
                        Map<String, SseEmitter> userEmitters = emitters.get(clientId);
                        if (userEmitters != null) {
                            SseEmitter emitter = userEmitters.get(connectionId);
                            if (emitter != null) {
                                try {
                                    emitter.send(SseEmitter.event()
                                            .name("message")
                                            .data(Map.of(
                                                    "topic", topic,
                                                    "payload", payload,
                                                    "timestamp", System.currentTimeMillis()
                                            )));
                                } catch (IOException e) {
                                    log.warn("SSE推送失败, 清理连接: clientId={}, connectionId={}", clientId, connectionId);
                                    cleanup(clientId, connectionId, subscribedTopic);
                                }
                            }
                        }
                    }
                }
            });
        });
    }

    private void cleanup(String clientId, String connectionId, String topic) {
        // 使用computeIfPresent确保原子性操作
        emitters.computeIfPresent(clientId, (k, v) -> {
            v.remove(connectionId);
            return v.isEmpty() ? null : v;
        });
        
        topicSubscribers.computeIfPresent(clientId, (k, userTopics) -> {
            userTopics.computeIfPresent(topic, (t, connIds) -> {
                connIds.remove(connectionId);
                return connIds.isEmpty() ? null : connIds;
            });
            return userTopics.isEmpty() ? null : userTopics;
        });
    }

    private boolean matchesTopic(String filter, String topic) {
        if (filter.equals(topic)) return true;
        if ("#".equals(filter)) return true;
        String[] fp = filter.split("/");
        String[] tp = topic.split("/");
        for (int i = 0; i < fp.length; i++) {
            if ("#".equals(fp[i])) return true;
            if (i >= tp.length) return false;
            if (!"+".equals(fp[i]) && !fp[i].equals(tp[i])) return false;
        }
        return fp.length == tp.length;
    }
}
