package com.cde.service.impl;

import com.cde.mqtt.MqttClientService;
import com.cde.service.AuditService;
import com.cde.service.SubscribeService;
import com.cde.util.MqttTopicUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscribeServiceImpl implements SubscribeService {

    private final MqttClientService mqttClientService;
    private final AuditService auditService;

    private final Map<String, SseEmitter> userEmitters = new ConcurrentHashMap<>();
    private final Map<String, CopyOnWriteArrayList<String>> userTopics = new ConcurrentHashMap<>();

    @Override
    public SseEmitter subscribe(String username, String token, String topic, int qos) {
        try {
            mqttClientService.connectForUser(username, token);

            SseEmitter emitter = userEmitters.get(username);
            if (emitter == null) {
                emitter = new SseEmitter(30 * 60 * 1000L);
                userEmitters.put(username, emitter);

                emitter.onCompletion(() -> {
                    log.info("SSE connection completed for user: {}", username);
                    userEmitters.remove(username);
                });
                emitter.onTimeout(() -> {
                    log.info("SSE connection timeout for user: {}", username);
                    userEmitters.remove(username);
                });
                emitter.onError(e -> {
                    log.warn("SSE connection error for user {}: {}", username, e.getMessage());
                    userEmitters.remove(username);
                });

                try {
                    emitter.send(SseEmitter.event()
                            .name("connected")
                            .data(Map.of("message", "SSE连接已建立")));
                } catch (IOException e) {
                    log.error("SSE发送连接确认失败", e);
                }
            }

            CopyOnWriteArrayList<String> topics = userTopics.computeIfAbsent(username, k -> new CopyOnWriteArrayList<>());
            if (!topics.contains(topic)) {
                topics.add(topic);
            }

            mqttClientService.subscribeForUser(username, topic, qos, (t, payload) -> {
                pushMessageToUser(username, t, payload);
            });

            auditService.log(username, "subscribe", "订阅主题: " + topic + ", QoS=" + qos, "backend");

            return emitter;
        } catch (Exception e) {
            log.error("MQTT订阅失败, user={}, topic={}: {}", username, topic, e.getMessage());

            CopyOnWriteArrayList<String> topics = userTopics.get(username);
            if (topics != null) {
                topics.remove(topic);
                if (topics.isEmpty()) {
                    userTopics.remove(username);
                    mqttClientService.disconnectForUser(username);
                }
            }

            throw new RuntimeException("订阅失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void unsubscribe(String username, String topic) {
        mqttClientService.unsubscribeForUser(username, topic);

        CopyOnWriteArrayList<String> topics = userTopics.get(username);
        if (topics != null) {
            topics.remove(topic);
            if (topics.isEmpty()) {
                userTopics.remove(username);
                mqttClientService.disconnectForUser(username);
            }
        }

        auditService.log(username, "unsubscribe", "取消订阅: " + topic, "backend");
    }

    private void pushMessageToUser(String username, String topic, String payload) {
        SseEmitter emitter = userEmitters.get(username);
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
                log.warn("SSE推送失败, 清理连接: username={}", username);
                userEmitters.remove(username);
            }
        }
    }


}
