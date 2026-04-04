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
        SseEmitter emitter = null;
        try {
            mqttClientService.connectForUser(username, token);

            emitter = new SseEmitter(30 * 60 * 1000L);
            userEmitters.put(username, emitter);

            userTopics.computeIfAbsent(username, k -> new CopyOnWriteArrayList<>()).add(topic);

            mqttClientService.subscribeForUser(username, topic, qos, (t, payload) -> {
                pushMessageToUser(username, t, payload);
            });

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

            auditService.log(username, "subscribe", "订阅主题: " + topic + ", QoS=" + qos, "backend");

            try {
                emitter.send(SseEmitter.event()
                        .name("connected")
                        .data(Map.of("message", "SSE连接已建立, 订阅主题: " + topic)));
            } catch (IOException e) {
                log.error("SSE发送连接确认失败", e);
            }

            return emitter;
        } catch (Exception e) {
            log.error("MQTT订阅失败, user={}, topic={}: {}", username, topic, e.getMessage());
            
            if (emitter != null) {
                emitter.completeWithError(e);
                userEmitters.remove(username);
            }
            
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

    @Override
    public void pushMessage(String topic, String payload) {
        userEmitters.forEach((username, emitter) -> {
            CopyOnWriteArrayList<String> topics = userTopics.get(username);
            if (topics != null && topics.stream().anyMatch(t -> matchesTopic(t, topic))) {
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
        });
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

    private boolean matchesTopic(String filter, String topic) {
        if (filter.equals(topic) || "#".equals(filter)) {
            return true;
        }
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
