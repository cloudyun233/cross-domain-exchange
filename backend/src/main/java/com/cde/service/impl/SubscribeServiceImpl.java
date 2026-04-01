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

    // clientId → SseEmitter
    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();
    // topic → List<clientId> (跟踪哪些客户端订阅了哪些主题)
    private final Map<String, CopyOnWriteArrayList<String>> topicSubscribers = new ConcurrentHashMap<>();

    @Override
    public SseEmitter subscribe(String clientId, String topic, int qos) {
        // 创建SSE连接 (超时30分钟)
        SseEmitter emitter = new SseEmitter(30 * 60 * 1000L);
        emitters.put(clientId, emitter);

        // 记录订阅关系
        topicSubscribers.computeIfAbsent(topic, k -> new CopyOnWriteArrayList<>()).add(clientId);

        // 后端代理订阅EMQX
        mqttClientService.subscribe(topic, qos, (t, payload) -> {
            pushMessage(t, payload);
        });

        // 清理回调
        emitter.onCompletion(() -> cleanup(clientId, topic));
        emitter.onTimeout(() -> cleanup(clientId, topic));
        emitter.onError(e -> cleanup(clientId, topic));

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
    public void unsubscribe(String clientId, String topic) {
        cleanup(clientId, topic);
        auditService.log(clientId, "unsubscribe", "取消订阅: " + topic, "backend");
    }

    @Override
    public void pushMessage(String topic, String payload) {
        // 遍历所有主题订阅者，推送消息
        topicSubscribers.forEach((subscribedTopic, subscribers) -> {
            if (matchesTopic(subscribedTopic, topic)) {
                for (String clientId : subscribers) {
                    SseEmitter emitter = emitters.get(clientId);
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
                            log.warn("SSE推送失败, 清理客户端: {}", clientId);
                            cleanup(clientId, subscribedTopic);
                        }
                    }
                }
            }
        });
    }

    private void cleanup(String clientId, String topic) {
        emitters.remove(clientId);
        CopyOnWriteArrayList<String> list = topicSubscribers.get(topic);
        if (list != null) {
            list.remove(clientId);
            if (list.isEmpty()) topicSubscribers.remove(topic);
        }
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
