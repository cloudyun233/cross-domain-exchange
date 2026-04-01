package com.cde.mqtt;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * MQTT客户端服务 (所有MQTT连接由后端统一管控)
 * QUIC→TLS→TCP降级策略 (需求文档1.2)
 */
@Slf4j
@Component
public class MqttClientService {

    @Value("${mqtt.broker.host:localhost}")
    private String brokerHost;
    @Value("${mqtt.broker.tcp-port:1883}")
    private int tcpPort;
    @Value("${mqtt.broker.tls-port:8883}")
    private int tlsPort;
    @Value("${mqtt.broker.quic-port:14567}")
    private int quicPort;
    @Value("${mqtt.broker.client-id-prefix:cde-backend}")
    private String clientIdPrefix;

    private MqttClient mqttClient;
    private String activeProtocol = "未连接";
    private final Map<String, BiConsumer<String, String>> subscriptionCallbacks = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        connectWithFallback();
    }

    /**
     * QUIC→TLS→TCP降级连接策略
     */
    private void connectWithFallback() {
        // 注: Paho客户端不原生支持QUIC，QUIC由EMQX侧处理
        // 这里实现 TLS→TCP 降级
        String clientId = clientIdPrefix + "-" + System.currentTimeMillis();

        // 尝试TLS
        try {
            String tlsUrl = "ssl://" + brokerHost + ":" + tlsPort;
            mqttClient = new MqttClient(tlsUrl, clientId, new MemoryPersistence());
            MqttConnectOptions opts = createOptions();
            mqttClient.connect(opts);
            activeProtocol = "TLS";
            setupCallbackHandler();
            log.info("MQTT已通过TLS连接: {}", tlsUrl);
            return;
        } catch (Exception e) {
            log.warn("TLS连接失败，尝试TCP回退: {}", e.getMessage());
        }

        // 回退到TCP
        try {
            String tcpUrl = "tcp://" + brokerHost + ":" + tcpPort;
            mqttClient = new MqttClient(tcpUrl, clientId, new MemoryPersistence());
            MqttConnectOptions opts = createOptions();
            mqttClient.connect(opts);
            activeProtocol = "TCP";
            setupCallbackHandler();
            log.info("MQTT已通过TCP连接(TLS不可用已回退): {}", tcpUrl);
        } catch (Exception e) {
            activeProtocol = "未连接";
            log.warn("MQTT连接失败(Broker可能未启动): {}", e.getMessage());
        }
    }

    private MqttConnectOptions createOptions() {
        MqttConnectOptions opts = new MqttConnectOptions();
        opts.setCleanSession(false);
        opts.setAutomaticReconnect(true);
        opts.setConnectionTimeout(10);
        opts.setKeepAliveInterval(60);
        return opts;
    }

    private void setupCallbackHandler() {
        mqttClient.setCallback(new MqttCallbackExtended() {
            @Override
            public void connectComplete(boolean reconnect, String serverURI) {
                log.info("MQTT{}连接: {}", reconnect ? "重新" : "", serverURI);
            }
            @Override
            public void connectionLost(Throwable cause) {
                log.warn("MQTT连接丢失: {}", cause.getMessage());
            }
            @Override
            public void messageArrived(String topic, MqttMessage message) {
                String payload = new String(message.getPayload());
                log.debug("收到消息: topic={}, payload={}", topic, payload);
                // 通知所有匹配的订阅回调
                subscriptionCallbacks.forEach((filter, callback) -> {
                    if (matchesTopic(filter, topic)) {
                        callback.accept(topic, payload);
                    }
                });
            }
            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {}
        });
    }

    public void publish(String topic, String payload, int qos) {
        if (mqttClient == null || !mqttClient.isConnected()) {
            log.warn("MQTT未连接，尝试重连...");
            connectWithFallback();
            if (mqttClient == null || !mqttClient.isConnected()) {
                throw new RuntimeException("MQTT Broker未连接");
            }
        }
        try {
            MqttMessage msg = new MqttMessage(payload.getBytes());
            msg.setQos(qos);
            mqttClient.publish(topic, msg);
        } catch (MqttException e) {
            throw new RuntimeException("发布消息失败: " + e.getMessage(), e);
        }
    }

    public void subscribe(String topic, int qos, BiConsumer<String, String> callback) {
        subscriptionCallbacks.put(topic, callback);
        if (mqttClient != null && mqttClient.isConnected()) {
            try {
                mqttClient.subscribe(topic, qos);
                log.info("已订阅主题: {}, QoS={}", topic, qos);
            } catch (MqttException e) {
                log.error("订阅失败: {}", e.getMessage());
            }
        }
    }

    public String getActiveProtocol() { return activeProtocol; }

    public boolean isConnected() {
        return mqttClient != null && mqttClient.isConnected();
    }

    @PreDestroy
    public void destroy() {
        if (mqttClient != null && mqttClient.isConnected()) {
            try { mqttClient.disconnect(); } catch (MqttException e) { /* ignore */ }
        }
    }

    private boolean matchesTopic(String filter, String topic) {
        if (filter.equals(topic) || "#".equals(filter)) return true;
        String[] fp = filter.split("/"), tp = topic.split("/");
        for (int i = 0; i < fp.length; i++) {
            if ("#".equals(fp[i])) return true;
            if (i >= tp.length) return false;
            if (!"+".equals(fp[i]) && !fp[i].equals(tp[i])) return false;
        }
        return fp.length == tp.length;
    }
}
