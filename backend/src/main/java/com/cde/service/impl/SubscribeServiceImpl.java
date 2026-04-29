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
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * 订阅服务实现 —— 精简重写版
 *
 * 核心设计：
 * 1. SSE emitter 独立管理，不跟 MQTT 生命周期耦合
 * 2. 消息推送通过 MqttClientService 的全局 messageListener 回调
 * 3. 本地不再重复管理订阅列表，完全委托给 MqttClientService.subscribedTopics
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubscribeServiceImpl implements SubscribeService {

    /** SSE超时设为0（永不超时），依赖15s心跳维持连接活性 */
    private static final long SSE_TIMEOUT_MS = 0L;

    private final MqttClientService mqttClientService;
    private final AuditService auditService;

    /** 用户 → SSE emitter */
    private final Map<String, SseEmitter> userEmitters = new ConcurrentHashMap<>();

    // ═══════════════════════════════════════════════════════════════════════════
    //  SSE 长连接
    // ═══════════════════════════════════════════════════════════════════════════

    @Override
    public SseEmitter openSse(String username) {
        log.info("[SSE] openSse: username={}", username);

        // 先关闭旧emitter，保证单用户单连接
        completeEmitter(username);

        // 创建新emitter并注册生命周期回调，确保异常时从Map中移除避免内存泄漏
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        userEmitters.put(username, emitter);

        emitter.onCompletion(() -> {
            log.info("[SSE] onCompletion: username={}", username);
            userEmitters.remove(username, emitter);
        });
        emitter.onTimeout(() -> {
            log.info("[SSE] onTimeout: username={}", username);
            userEmitters.remove(username, emitter);
        });
        emitter.onError(err -> {
            log.warn("[SSE] onError: username={}, error={}", username, err.getMessage());
            userEmitters.remove(username, emitter);
        });

        // 发送 connected 事件
        try {
            emitter.send(SseEmitter.event().name("connected").data(Map.of("message", "SSE连接已建立")));
            log.info("[SSE] 已发送 connected 事件: username={}", username);
        } catch (IOException e) {
            log.error("[SSE] 发送 connected 事件失败: username={}", username, e);
            userEmitters.remove(username, emitter);
        }

        // 若 MQTT 已有 context，更新 messageListener 指向当前 emitter
        mqttClientService.setMessageListener(username, createPushCallback(username));

        return emitter;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  MQTT 连接/断开
    // ═══════════════════════════════════════════════════════════════════════════

    @Override
    public void connectMqtt(String username, String token) {
        log.info("[Subscribe] connectMqtt: username={}", username);

        if (!userEmitters.containsKey(username)) {
            log.warn("[Subscribe] connectMqtt 时 SSE 未建立，消息可能丢失: username={}", username);
        }

        BiConsumer<String, String> listener = createPushCallback(username);
        mqttClientService.connectForUser(username, token, listener);

        log.info("[Subscribe] MQTT 已连接: username={}, 记忆订阅={}",
                username, mqttClientService.getSubscribedTopics(username));

        auditService.log(username, "mqtt_connect",
                "连接 MQTT, 记忆订阅=" + mqttClientService.getSubscribedTopics(username), "backend");
    }

    @Override
    public void disconnectMqtt(String username) {
        log.info("[Subscribe] disconnectMqtt: username={}", username);

        mqttClientService.disconnectForUser(username);
        // SSE 保持，订阅记忆保持

        auditService.log(username, "mqtt_disconnect",
                "断开 MQTT（SSE保持，订阅记忆保持）", "backend");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  订阅/取消订阅
    // ═══════════════════════════════════════════════════════════════════════════

    @Override
    public void subscribeTopic(String username, String topic, int qos) {
        log.info("[Subscribe] subscribeTopic: username={}, topic={}, qos={}", username, topic, qos);

        if (!userEmitters.containsKey(username)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "SSE 未建立，请先建立 SSE 连接");
        }
        if (!mqttClientService.isUserConnected(username)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "MQTT 未连接，请先连接 MQTT");
        }

        mqttClientService.subscribeForUser(username, topic, qos);

        auditService.log(username, "subscribe",
                "订阅主题: " + topic + ", QoS=" + qos, "backend");
    }

    @Override
    public void cancelTopic(String username, String topic) {
        log.info("[Subscribe] cancelTopic: username={}, topic={}", username, topic);

        mqttClientService.unsubscribeForUser(username, topic);

        auditService.log(username, "unsubscribe", "取消订阅: " + topic, "backend");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  完全关闭（退出登录）
    // ═══════════════════════════════════════════════════════════════════════════

    @Override
    public void closeAll(String username) {
        log.info("[Subscribe] closeAll: username={}", username);

        mqttClientService.closeAll(username);
        completeEmitter(username);

        auditService.log(username, "subscribe_close",
                "关闭订阅会话（SSE+MQTT全部断开）", "backend");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  状态查询
    // ═══════════════════════════════════════════════════════════════════════════

    @Override
    public Map<String, Object> getSessionStatus(String username) {
        Set<String> topics = mqttClientService.getSubscribedTopics(username);
        return Map.of(
                "mqttConnected", mqttClientService.isUserConnected(username),
                "protocol", mqttClientService.getUserProtocol(username),
                "sseConnected", userEmitters.containsKey(username),
                "subscribedTopics", topics,
                "subscriptionCount", topics.size()
        );
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  心跳
    // ═══════════════════════════════════════════════════════════════════════════

    /** 15秒间隔SSE心跳，防止反向代理/负载均衡器因空闲超时断开连接 */
    @Scheduled(fixedDelay = 15000L)
    public void heartbeat() {
        userEmitters.forEach((username, emitter) -> {
            try {
                emitter.send(SseEmitter.event().name("heartbeat").data("ping"));
            } catch (IOException e) {
                log.debug("[SSE] 心跳失败，移除 emitter: username={}", username);
                userEmitters.remove(username, emitter);
            }
        });
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  内部工具
    // ═══════════════════════════════════════════════════════════════════════════

    private void completeEmitter(String username) {
        SseEmitter existing = userEmitters.remove(username);
        if (existing != null) {
            log.info("[SSE] 关闭旧 emitter: username={}", username);
            existing.complete();
        }
    }

    /**
     * 创建SSE推送回调
     *
     * <p>消息转发流程：MQTT客户端收到消息 → 回调触发 → 通过SseEmitter推送至前端。
     * 回调内获取当前用户的emitter，以SSE事件名"message"发送包含topic、payload、timestamp的JSON。</p>
     */
    private BiConsumer<String, String> createPushCallback(String username) {
        return (topic, payload) -> {
            SseEmitter emitter = userEmitters.get(username);
            if (emitter == null) {
                log.warn("[SSE] 推送消息时无 emitter: username={}, topic={}", username, topic);
                return;
            }

            log.info("[SSE] >>> 推送消息到前端: username={}, topic={}, payloadLen={}, payload={}",
                    username, topic, payload.length(),
                    payload.length() > 200 ? payload.substring(0, 200) + "..." : payload);

            try {
                emitter.send(SseEmitter.event()
                        .name("message")
                        .data(Map.of(
                                "topic", topic,
                                "payload", payload,
                                "timestamp", System.currentTimeMillis()
                        )));
                log.debug("[SSE] 推送成功: username={}, topic={}", username, topic);
            } catch (IOException e) {
                log.warn("[SSE] 推送失败，移除 emitter: username={}, error={}", username, e.getMessage());
                userEmitters.remove(username, emitter);
            }
        };
    }
}
