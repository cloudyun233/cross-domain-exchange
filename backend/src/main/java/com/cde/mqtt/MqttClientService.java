package com.cde.mqtt;

import com.cde.security.JwtUtil;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * Centralized MQTT client used by the backend to proxy publish/subscribe
 * operations to EMQX.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MqttClientService {

    private final JwtUtil jwtUtil;

    @Value("${mqtt.broker.host:localhost}")
    private String brokerHost;

    @Value("${mqtt.broker.tcp-port:1883}")
    private int tcpPort;

    @Value("${mqtt.broker.tls-port:8883}")
    private int tlsPort;

    @Value("${mqtt.broker.client-id-prefix:cde-backend}")
    private String clientIdPrefix;

    @Value("${mqtt.broker.username:admin}")
    private String brokerUsername;

    @Value("${mqtt.broker.domain-code:admin}")
    private String brokerDomainCode;

    @Value("${mqtt.broker.role-type:admin}")
    private String brokerRoleType;

    private MqttClient mqttClient;
    private String activeProtocol = "DISCONNECTED";
    private final Map<String, BiConsumer<String, String>> subscriptionCallbacks = new ConcurrentHashMap<>();
    private final Map<String, Integer> subscriptionQos = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        connectWithFallback();
    }

    private void connectWithFallback() {
        disconnectQuietly();

        String clientId = clientIdPrefix + "-" + System.currentTimeMillis();
        if (connect("ssl://" + brokerHost + ":" + tlsPort, clientId, "TLS")) {
            return;
        }
        if (connect("tcp://" + brokerHost + ":" + tcpPort, clientId, "TCP")) {
            return;
        }

        activeProtocol = "DISCONNECTED";
        log.warn("MQTT connection failed for all fallback protocols");
    }

    private boolean connect(String brokerUrl, String clientId, String protocol) {
        try {
            MqttClient client = new MqttClient(brokerUrl, clientId, new MemoryPersistence());
            client.setCallback(createCallback());
            client.connect(createOptions());
            mqttClient = client;
            activeProtocol = protocol;
            resubscribeAll();
            log.info("MQTT connected over {}: {}", protocol, brokerUrl);
            return true;
        } catch (Exception e) {
            log.warn("MQTT {} connection failed: {}", protocol, e.getMessage());
            return false;
        }
    }

    private MqttConnectOptions createOptions() {
        MqttConnectOptions options = new MqttConnectOptions();
        options.setCleanSession(false);
        options.setAutomaticReconnect(true);
        options.setConnectionTimeout(10);
        options.setKeepAliveInterval(60);
        options.setUserName(brokerUsername);
        options.setPassword(generateBrokerToken().toCharArray());
        return options;
    }

    private String generateBrokerToken() {
        return jwtUtil.generateToken(brokerUsername, brokerDomainCode, brokerRoleType);
    }

    private MqttCallbackExtended createCallback() {
        return new MqttCallbackExtended() {
            @Override
            public void connectComplete(boolean reconnect, String serverURI) {
                log.info("MQTT {}connect complete: {}", reconnect ? "re" : "", serverURI);
                resubscribeAll();
            }

            @Override
            public void connectionLost(Throwable cause) {
                String message = cause == null ? "unknown" : cause.getMessage();
                log.warn("MQTT connection lost: {}", message);
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) {
                String payload = new String(message.getPayload(), StandardCharsets.UTF_8);
                subscriptionCallbacks.forEach((filter, callback) -> {
                    if (matchesTopic(filter, topic)) {
                        callback.accept(topic, payload);
                    }
                });
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {
                // no-op
            }
        };
    }

    public void publish(String topic, String payload, int qos) {
        ensureConnected();
        try {
            MqttMessage message = new MqttMessage(payload.getBytes(StandardCharsets.UTF_8));
            message.setQos(qos);
            mqttClient.publish(topic, message);
        } catch (MqttException e) {
            throw new RuntimeException("Failed to publish MQTT message: " + e.getMessage(), e);
        }
    }

    public void subscribe(String topic, int qos, BiConsumer<String, String> callback) {
        subscriptionCallbacks.put(topic, callback);
        subscriptionQos.put(topic, qos);
        ensureConnected();
        try {
            mqttClient.subscribe(topic, qos);
            log.info("Subscribed to topic {}, qos={}", topic, qos);
        } catch (MqttException e) {
            throw new RuntimeException("Failed to subscribe topic " + topic + ": " + e.getMessage(), e);
        }
    }

    public String getActiveProtocol() {
        return activeProtocol;
    }

    public boolean isConnected() {
        return mqttClient != null && mqttClient.isConnected();
    }

    @PreDestroy
    public void destroy() {
        disconnectQuietly();
    }

    private void ensureConnected() {
        if (mqttClient != null && mqttClient.isConnected()) {
            return;
        }
        log.warn("MQTT client disconnected, reconnecting");
        connectWithFallback();
        if (mqttClient == null || !mqttClient.isConnected()) {
            throw new RuntimeException("MQTT broker is not connected");
        }
    }

    private void resubscribeAll() {
        if (mqttClient == null || !mqttClient.isConnected()) {
            return;
        }
        subscriptionCallbacks.forEach((topic, callback) -> {
            try {
                mqttClient.subscribe(topic, subscriptionQos.getOrDefault(topic, 1));
            } catch (MqttException e) {
                log.warn("Failed to resubscribe topic {}: {}", topic, e.getMessage());
            }
        });
    }

    private void disconnectQuietly() {
        if (mqttClient == null) {
            return;
        }
        try {
            if (mqttClient.isConnected()) {
                mqttClient.disconnect();
            }
            mqttClient.close();
        } catch (MqttException e) {
            log.debug("Ignoring MQTT disconnect error: {}", e.getMessage());
        }
    }

    private boolean matchesTopic(String filter, String topic) {
        if (filter.equals(topic) || "#".equals(filter)) {
            return true;
        }

        String[] filterParts = filter.split("/");
        String[] topicParts = topic.split("/");
        for (int i = 0; i < filterParts.length; i++) {
            if ("#".equals(filterParts[i])) {
                return true;
            }
            if (i >= topicParts.length) {
                return false;
            }
            if (!"+".equals(filterParts[i]) && !filterParts[i].equals(topicParts[i])) {
                return false;
            }
        }
        return filterParts.length == topicParts.length;
    }
}
