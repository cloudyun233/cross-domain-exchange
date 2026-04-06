package com.cde.service.impl;

import com.cde.exception.BusinessException;
import com.cde.mqtt.MqttClientService;
import com.cde.service.AuditService;
import com.cde.service.SubscribeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
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
                    cleanupUserSession(username);
                });
                emitter.onTimeout(() -> {
                    log.info("SSE connection timeout for user: {}", username);
                    cleanupUserSession(username);
                });
                emitter.onError(error -> {
                    log.warn("SSE connection error for user {}: {}", username, error.getMessage());
                    cleanupUserSession(username);
                });

                try {
                    emitter.send(SseEmitter.event()
                            .name("connected")
                            .data(Map.of("message", "SSE连接已建立")));
                } catch (IOException e) {
                    log.error("SSE发送连接确认失败", e);
                }
            }

            CopyOnWriteArrayList<String> topics = userTopics.computeIfAbsent(username, key -> new CopyOnWriteArrayList<>());
            if (!topics.contains(topic)) {
                topics.add(topic);
            }

            mqttClientService.subscribeForUser(username, topic, qos, (messageTopic, payload) ->
                    pushMessageToUser(username, messageTopic, payload));

            auditService.log(username, "subscribe", "订阅主题: " + topic + ", QoS=" + qos, "backend");
            return emitter;
        } catch (BusinessException e) {
            log.error("MQTT订阅失败，user={}, topic={}: {}", username, topic, e.getMessage());
            cleanupFailedSubscription(username, topic);
            throw e;
        } catch (Exception e) {
            log.error("MQTT订阅失败，user={}, topic={}: {}", username, topic, e.getMessage());
            cleanupFailedSubscription(username, topic);
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "订阅失败");
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
        if (emitter == null) {
            return;
        }

        try {
            emitter.send(SseEmitter.event()
                    .name("message")
                    .data(Map.of(
                            "topic", topic,
                            "payload", payload,
                            "timestamp", System.currentTimeMillis()
                    )));
        } catch (IOException e) {
            log.warn("SSE推送失败，清理连接: username={}", username);
            cleanupUserSession(username);
        }
    }

    private void cleanupFailedSubscription(String username, String topic) {
        CopyOnWriteArrayList<String> topics = userTopics.get(username);
        if (topics != null) {
            topics.remove(topic);
            if (topics.isEmpty()) {
                userTopics.remove(username);
                mqttClientService.disconnectForUser(username);
            }
        } else {
            mqttClientService.disconnectForUser(username);
        }
    }

    private void cleanupUserSession(String username) {
        userEmitters.remove(username);
        userTopics.remove(username);
        mqttClientService.disconnectForUser(username);
    }
}
