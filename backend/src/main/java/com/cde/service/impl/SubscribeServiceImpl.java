package com.cde.service.impl;

import com.cde.exception.BusinessException;
import com.cde.mqtt.MqttClientService;
import com.cde.service.AuditService;
import com.cde.service.SubscribeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscribeServiceImpl implements SubscribeService {

    private static final long SSE_TIMEOUT_MS = 0L;

    private final MqttClientService mqttClientService;
    private final AuditService auditService;

    private final Map<String, SseEmitter> userEmitters = new ConcurrentHashMap<>();
    private final Map<String, ConcurrentHashMap<String, Integer>> userSubscriptions = new ConcurrentHashMap<>();

    @Override
    public SseEmitter subscribe(String username, String token, String topic, int qos) {
        try {
            SseEmitter emitter = createOrReplaceEmitter(username);
            ConcurrentHashMap<String, Integer> subscriptions =
                    userSubscriptions.computeIfAbsent(username, key -> new ConcurrentHashMap<>());
            subscriptions.put(topic, qos);

            BiConsumer<String, String> callback = createPushCallback(username);
            mqttClientService.connectForUser(username, token, new HashMap<>(subscriptions), callback);
            mqttClientService.subscribeForUser(username, topic, qos, callback);

            auditService.log(username, "subscribe", "订阅主题: " + topic + ", QoS=" + qos, "backend");
            return emitter;
        } catch (BusinessException e) {
            log.error("MQTT subscribe failed, user={}, topic={}: {}", username, topic, e.getMessage());
            cleanupFailedSubscription(username, topic);
            throw e;
        } catch (Exception e) {
            log.error("MQTT subscribe failed, user={}, topic={}: {}", username, topic, e.getMessage());
            cleanupFailedSubscription(username, topic);
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "订阅失败");
        }
    }

    @Override
    public void unsubscribe(String username, String topic) {
        mqttClientService.unsubscribeForUser(username, topic);

        ConcurrentHashMap<String, Integer> subscriptions = userSubscriptions.get(username);
        if (subscriptions != null) {
            subscriptions.remove(topic);
            if (subscriptions.isEmpty()) {
                closeSession(username);
            }
        }

        auditService.log(username, "unsubscribe", "取消订阅: " + topic, "backend");
    }

    @Override
    public Map<String, Object> getSessionStatus(String username) {
        return Map.of(
                "mqttConnected", mqttClientService.isUserConnected(username),
                "protocol", mqttClientService.getUserProtocol(username),
                "sseConnected", userEmitters.containsKey(username),
                "subscriptionCount", userSubscriptions.getOrDefault(username, new ConcurrentHashMap<>()).size()
        );
    }

    @Override
    public void connectSession(String username, String token) {
        ConcurrentHashMap<String, Integer> subscriptions = userSubscriptions.get(username);
        Map<String, Integer> remembered = subscriptions == null ? Map.of() : new HashMap<>(subscriptions);
        BiConsumer<String, String> callback = createPushCallback(username);

        mqttClientService.connectForUser(username, token, remembered, callback);
        remembered.forEach((topic, qos) -> mqttClientService.subscribeForUser(username, topic, qos, callback));
        auditService.log(username, "mqtt_connect", "手动连接 MQTT 会话", "backend");
    }

    @Override
    public void disconnectSession(String username) {
        mqttClientService.disconnectForUser(username);
        auditService.log(username, "mqtt_disconnect", "手动断开 MQTT 会话", "backend");
    }

    @Override
    public void closeSession(String username) {
        userSubscriptions.remove(username);
        mqttClientService.disconnectForUser(username);
        completeEmitter(username);
        auditService.log(username, "subscribe_close", "关闭订阅会话", "backend");
    }

    @Scheduled(fixedDelay = 15000L)
    public void heartbeat() {
        userEmitters.forEach((username, emitter) -> {
            try {
                emitter.send(SseEmitter.event().name("heartbeat").data("ping"));
            } catch (IOException e) {
                log.debug("SSE heartbeat failed for user {}, removing emitter", username);
                removeEmitter(username, emitter);
            }
        });
    }

    private SseEmitter createOrReplaceEmitter(String username) {
        completeEmitter(username);

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        userEmitters.put(username, emitter);

        emitter.onCompletion(() -> {
            log.info("SSE connection completed for user: {}", username);
            removeEmitter(username, emitter);
        });
        emitter.onTimeout(() -> {
            log.info("SSE connection timeout for user: {}", username);
            removeEmitter(username, emitter);
        });
        emitter.onError(error -> {
            log.warn("SSE connection error for user {}: {}", username, error.getMessage());
            removeEmitter(username, emitter);
        });

        try {
            emitter.send(SseEmitter.event().name("connected").data(Map.of("message", "SSE连接已建立")));
        } catch (IOException e) {
            log.error("Failed to send SSE connected event", e);
            removeEmitter(username, emitter);
        }
        return emitter;
    }

    private void completeEmitter(String username) {
        SseEmitter existing = userEmitters.remove(username);
        if (existing != null) {
          existing.complete();
        }
    }

    private void removeEmitter(String username, SseEmitter emitter) {
        userEmitters.remove(username, emitter);
    }

    private BiConsumer<String, String> createPushCallback(String username) {
        return (messageTopic, payload) -> pushMessageToUser(username, messageTopic, payload);
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
            log.warn("SSE push failed for user {}, removing stale emitter", username);
            removeEmitter(username, emitter);
        }
    }

    private void cleanupFailedSubscription(String username, String topic) {
        ConcurrentHashMap<String, Integer> subscriptions = userSubscriptions.get(username);
        if (subscriptions != null) {
            subscriptions.remove(topic);
            if (subscriptions.isEmpty()) {
                userSubscriptions.remove(username);
                mqttClientService.disconnectForUser(username);
            }
        } else {
            mqttClientService.disconnectForUser(username);
        }
    }
}
