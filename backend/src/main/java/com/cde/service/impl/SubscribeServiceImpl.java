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

    // ─────────────────────────────────────────────────────────────────────────
    // 仅建立 SSE 长连接，不做任何 MQTT 操作
    // 前端进入订阅页面时立即调用
    // ─────────────────────────────────────────────────────────────────────────
    @Override
    public SseEmitter openSse(String username) {
        SseEmitter emitter = createOrReplaceEmitter(username);
        log.info("SSE channel opened for user: {}", username);
        return emitter;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 订阅接口（兼容旧流程：一次性建立 SSE + MQTT + 订阅主题）
    // 若 SSE 已存在则复用，不会断开旧 SSE
    // ─────────────────────────────────────────────────────────────────────────
    @Override
    public SseEmitter subscribe(String username, String token, String topic, int qos) {
        try {
            // 若已有 SSE emitter 则复用，避免断开正在使用的 SSE
            SseEmitter emitter = userEmitters.containsKey(username)
                    ? userEmitters.get(username)
                    : createOrReplaceEmitter(username);

            ConcurrentHashMap<String, Integer> subscriptions =
                    userSubscriptions.computeIfAbsent(username, key -> new ConcurrentHashMap<>());
            subscriptions.put(topic, qos);

            BiConsumer<String, String> callback = createPushCallback(username);
            // connectForUser: 已连接则 early-return（callback 通过 seedSubscriptions 更新），未连接则建立连接
            mqttClientService.connectForUser(username, token, new HashMap<>(subscriptions), callback);
            // 向 EMQX 发送 SUBSCRIBE 报文
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
            // 注意：取消单个主题不再自动关闭整个会话，保持 SSE 和 MQTT 连接
        }

        auditService.log(username, "unsubscribe", "取消订阅: " + topic, "backend");
    }

    /**
     * 在已有 MQTT 连接的情况下新增订阅主题。
     * 要求 SSE emitter 和 MQTT 连接均已建立，否则抛出异常。
     */
    @Override
    public void subscribeTopic(String username, String topic, int qos) {
        if (!userEmitters.containsKey(username)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "SSE 未建立，请先建立 SSE 连接");
        }
        if (!mqttClientService.isUserConnected(username)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "MQTT 未连接");
        }

        BiConsumer<String, String> callback = createPushCallback(username);
        ConcurrentHashMap<String, Integer> subscriptions =
                userSubscriptions.computeIfAbsent(username, key -> new ConcurrentHashMap<>());
        subscriptions.put(topic, qos);

        // 向 EMQX 发送 SUBSCRIBE 报文，并更新本地 callback
        mqttClientService.subscribeForUser(username, topic, qos, callback);
        auditService.log(username, "subscribe", "订阅主题: " + topic + ", QoS=" + qos, "backend");
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

    // ─────────────────────────────────────────────────────────────────────────
    // 连接 MQTT（前端点"连接 MQTT"按钮）
    // 1. 确保 SSE emitter 存在（若无则静默创建，但通常前端会先调 openSse）
    // 2. 建立 MQTT 连接（cleanStart=false，持久会话）
    // 3. 重新订阅所有已记忆主题（EMQX 持久会话会自动补发 offline 消息）
    // ─────────────────────────────────────────────────────────────────────────
    @Override
    public void connectSession(String username, String token) {
        // SSE emitter 必须事先存在，否则 offline 消息回来时无处推送
        if (!userEmitters.containsKey(username)) {
            log.warn("connectSession called but no SSE emitter for user {}, messages may be lost", username);
        }

        ConcurrentHashMap<String, Integer> subscriptions = userSubscriptions.get(username);
        Map<String, Integer> remembered = subscriptions == null ? Map.of() : new HashMap<>(subscriptions);
        BiConsumer<String, String> callback = createPushCallback(username);

        // 建立 MQTT 连接（cleanStart=false 触发持久会话，EMQX 自动推送 offline 消息）
        mqttClientService.connectForUser(username, token, remembered, callback);

        // 重新向 EMQX 发送 SUBSCRIBE 报文（持久会话已有订阅可省略，但显式订阅更安全）
        remembered.forEach((topic, qos) -> mqttClientService.subscribeForUser(username, topic, qos, callback));

        auditService.log(username, "mqtt_connect", "手动连接 MQTT 会话, 记忆订阅数=" + remembered.size(), "backend");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 断开 MQTT（前端点"断开 MQTT"按钮）
    // 只断 MQTT，SSE 保持，EMQX 开始缓存该 clientId 的离线消息
    // ─────────────────────────────────────────────────────────────────────────
    @Override
    public void disconnectSession(String username) {
        mqttClientService.disconnectForUser(username);
        // SSE 保持不断！userEmitters 不清空，userSubscriptions 不清空
        auditService.log(username, "mqtt_disconnect", "手动断开 MQTT 会话（SSE 保持）", "backend");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 完全关闭会话（退出登录 / 关闭浏览器时调用）
    // 取消所有订阅、断开 MQTT、关闭 SSE
    // ─────────────────────────────────────────────────────────────────────────
    @Override
    public void closeSession(String username) {
        userSubscriptions.remove(username);
        mqttClientService.disconnectForUser(username);
        completeEmitter(username);
        auditService.log(username, "subscribe_close", "关闭订阅会话（SSE+MQTT全部断开）", "backend");
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

    // ─────────────────────────────────────────────────────────────────────────
    // 内部工具方法
    // ─────────────────────────────────────────────────────────────────────────

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
            log.error("Failed to send SSE connected event for user {}", username, e);
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
            log.debug("No SSE emitter for user {}, dropping message on topic {}", username, topic);
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
            }
        }
        // 失败时不断开 MQTT（可能其他主题仍在订阅），也不断开 SSE
    }
}
