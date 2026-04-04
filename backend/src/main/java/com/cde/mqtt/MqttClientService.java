package com.cde.mqtt;

import com.cde.security.JwtUtil;
import com.hivemq.client.mqtt.MqttClient;
import com.hivemq.client.mqtt.MqttGlobalPublishFilter;
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient;
import com.hivemq.client.mqtt.mqtt3.message.auth.Mqtt3SimpleAuth;
import com.hivemq.client.mqtt.mqtt3.message.connect.connack.Mqtt3ConnAck;
import com.hivemq.client.mqtt.mqtt3.message.publish.Mqtt3Publish;
import com.hivemq.client.mqtt.mqtt3.message.subscribe.suback.Mqtt3SubAck;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

@Slf4j
@Component
@RequiredArgsConstructor
public class MqttClientService {

    private enum TransportProtocol {
        DISCONNECTED,
        TLS,
        TCP
    }

    private final JwtUtil jwtUtil;

    @Value("${mqtt.broker.host:localhost}")
    private String brokerHost;

    @Value("${mqtt.broker.tls-port:8883}")
    private int tlsPort;

    @Value("${mqtt.broker.tcp-port:1883}")
    private int tcpPort;

    @Value("${mqtt.broker.client-id-prefix:cde-backend}")
    private String clientIdPrefix;

    @Value("${mqtt.broker.username:admin}")
    private String brokerUsername;

    @Value("${mqtt.broker.domain-code:admin}")
    private String brokerDomainCode;

    @Value("${mqtt.broker.role-type:admin}")
    private String brokerRoleType;

    @Value("${mqtt.retry.max-attempts:5}")
    private int maxRetryAttempts;

    @Value("${mqtt.retry.initial-delay-ms:1000}")
    private long initialRetryDelayMs;

    @Value("${mqtt.retry.multiplier:2.0}")
    private double retryMultiplier;

    @Value("${mqtt.retry.max-delay-ms:30000}")
    private long maxRetryDelayMs;

    @Value("${mqtt.connect.timeout-seconds:10}")
    private int connectTimeoutSeconds;

    @Value("${mqtt.publish.timeout-seconds:5}")
    private int publishTimeoutSeconds;

    @Value("${mqtt.subscribe.timeout-seconds:5}")
    private int subscribeTimeoutSeconds;

    @Value("${mqtt.disconnect.timeout-seconds:5}")
    private int disconnectTimeoutSeconds;

    private final Map<String, BiConsumer<String, String>> subscriptionCallbacks = new ConcurrentHashMap<>();
    private final Map<String, Integer> subscriptionQos = new ConcurrentHashMap<>();
    private final Object clientLock = new Object();

    private volatile Mqtt3AsyncClient mqttClient;
    private volatile TransportProtocol activeProtocol = TransportProtocol.DISCONNECTED;
    private volatile boolean shuttingDown;

    @PostConstruct
    public void init() {
        connectWithFallback();
    }

    @PreDestroy
    public void destroy() {
        shuttingDown = true;
        disconnectQuietly();
    }

    public void publish(String topic, String payload, int qos) {
        ensureConnected();
        synchronized (clientLock) {
            if (!isConnected() || mqttClient == null) {
                throw new IllegalStateException("MQTT broker is not connected");
            }
            sendPublish(topic, payload, qos);
            log.info("Published MQTT message over {}: topic={}, qos={}", activeProtocol, topic, qos);
        }
    }

    public void subscribe(String topic, int qos, BiConsumer<String, String> callback) {
        boolean wasConnected = isConnected();
        Integer previousQos = subscriptionQos.put(topic, qos);
        subscriptionCallbacks.put(topic, callback);

        ensureConnected();

        if (wasConnected && (previousQos == null || previousQos != qos)) {
            synchronized (clientLock) {
                sendSubscribe(topic, qos);
            }
        }
    }

    public String getActiveProtocol() {
        return activeProtocol.name();
    }

    public boolean isConnected() {
        return mqttClient != null && mqttClient.getState().isConnected();
    }

    private void ensureConnected() {
        if (isConnected()) {
            return;
        }
        if (!connectWithFallback()) {
            throw new IllegalStateException("MQTT broker is not connected");
        }
    }

    private boolean connectWithFallback() {
        synchronized (clientLock) {
            disconnectQuietly();

            String clientId = clientIdPrefix + "-" + System.currentTimeMillis();

            if (connectWithRetry(clientId, () -> tryConnectTls(clientId))) {
                return true;
            }

            if (connectWithRetry(clientId, () -> tryConnectTcp(clientId))) {
                return true;
            }

            activeProtocol = TransportProtocol.DISCONNECTED;
            log.warn("MQTT connection failed for all fallback protocols");
            return false;
        }
    }

    private boolean connectWithRetry(String clientId, Supplier<Boolean> connectFunc) {
        for (int attempt = 1; attempt <= maxRetryAttempts && !shuttingDown; attempt++) {
            if (connectFunc.get()) return true;
            long delay = calculateExponentialDelay(attempt);
            log.debug("MQTT connection attempt {}/{} failed, retrying in {}ms", 
                    attempt, maxRetryAttempts, delay);
            try { TimeUnit.MILLISECONDS.sleep(delay); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
        }
        return false;
    }

    private long calculateExponentialDelay(int attempt) {
        long delay = (long) (initialRetryDelayMs * Math.pow(retryMultiplier, attempt - 1));
        return Math.min(delay, maxRetryDelayMs);
    }

    private boolean tryConnectTls(String clientId) {
        try {
            log.info("Attempting MQTT TLS connection to {}:{}", brokerHost, tlsPort);
            
            mqttClient = MqttClient.builder()
                    .useMqttVersion3()
                    .identifier(clientId)
                    .serverHost(brokerHost)
                    .serverPort(tlsPort)
                    .sslWithDefaultConfig()
                    .automaticReconnectWithDefaultConfig()
                    .buildAsync();

            Mqtt3SimpleAuth simpleAuth = Mqtt3SimpleAuth.builder()
                    .username(brokerUsername)
                    .password(generateBrokerToken().getBytes(StandardCharsets.UTF_8))
                    .build();

            CompletableFuture<Mqtt3ConnAck> connectFuture = mqttClient.toAsync()
                    .connectWith()
                    .simpleAuth(simpleAuth)
                    .cleanSession(false)
                    .keepAlive(60)
                    .send();

            Mqtt3ConnAck connAck = connectFuture.get(connectTimeoutSeconds, TimeUnit.SECONDS);
            
            activeProtocol = TransportProtocol.TLS;
            setupPublishCallback();
            resubscribeAll();
            log.info("MQTT connected over TLS: tls://{}:{}", brokerHost, tlsPort);
            return true;

        } catch (TimeoutException e) {
            log.warn("MQTT TLS connection timeout after {} seconds", connectTimeoutSeconds);
            closeQuietly();
            return false;
        } catch (Exception e) {
            log.warn("MQTT TLS connection failed: {}", e.getMessage());
            closeQuietly();
            return false;
        }
    }

    private boolean tryConnectTcp(String clientId) {
        try {
            log.info("Attempting MQTT TCP connection to {}:{}", brokerHost, tcpPort);
            
            mqttClient = MqttClient.builder()
                    .useMqttVersion3()
                    .identifier(clientId)
                    .serverHost(brokerHost)
                    .serverPort(tcpPort)
                    .automaticReconnectWithDefaultConfig()
                    .buildAsync();

            Mqtt3SimpleAuth simpleAuth = Mqtt3SimpleAuth.builder()
                    .username(brokerUsername)
                    .password(generateBrokerToken().getBytes(StandardCharsets.UTF_8))
                    .build();

            CompletableFuture<Mqtt3ConnAck> connectFuture = mqttClient.toAsync()
                    .connectWith()
                    .simpleAuth(simpleAuth)
                    .cleanSession(false)
                    .keepAlive(60)
                    .send();

            Mqtt3ConnAck connAck = connectFuture.get(connectTimeoutSeconds, TimeUnit.SECONDS);
            
            activeProtocol = TransportProtocol.TCP;
            setupPublishCallback();
            resubscribeAll();
            log.info("MQTT connected over TCP: tcp://{}:{}", brokerHost, tcpPort);
            return true;

        } catch (TimeoutException e) {
            log.warn("MQTT TCP connection timeout after {} seconds", connectTimeoutSeconds);
            closeQuietly();
            return false;
        } catch (Exception e) {
            log.warn("MQTT TCP connection failed: {}", e.getMessage());
            closeQuietly();
            return false;
        }
    }

    private void setupPublishCallback() {
        if (mqttClient == null) {
            return;
        }
        
        mqttClient.toAsync().publishes(MqttGlobalPublishFilter.ALL, publish -> {
            String topic = publish.getTopic().toString();
            String payload = new String(publish.getPayloadAsBytes(), StandardCharsets.UTF_8);
            log.debug("MQTT {} received publish: topic={}, payloadLength={}", activeProtocol, topic, payload.length());
            
            subscriptionCallbacks.forEach((filter, callback) -> {
                if (matchesTopic(filter, topic)) {
                    try {
                        callback.accept(topic, payload);
                    } catch (Exception e) {
                        log.error("Error in subscription callback for topic {}: {}", topic, e.getMessage());
                    }
                }
            });
        });
    }

    private void sendPublish(String topic, String payload, int qos) {
        if (mqttClient == null || !mqttClient.getState().isConnected()) {
            throw new IllegalStateException("MQTT client not connected");
        }
        try {
            CompletableFuture<Mqtt3Publish> future = mqttClient.toAsync()
                    .publishWith()
                    .topic(topic)
                    .payload(payload.getBytes(StandardCharsets.UTF_8))
                    .qos(com.hivemq.client.mqtt.datatypes.MqttQos.fromCode(qos))
                    .send();

            future.get(publishTimeoutSeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new RuntimeException("Failed to publish MQTT message: " + e.getMessage(), e);
        }
    }

    private void sendSubscribe(String topic, int qos) {
        try {
            synchronized (clientLock) {
                if (mqttClient == null) {
                    throw new IllegalStateException("MQTT broker is not connected");
                }
                
                CompletableFuture<Mqtt3SubAck> future = mqttClient.toAsync()
                        .subscribeWith()
                        .topicFilter(topic)
                        .qos(com.hivemq.client.mqtt.datatypes.MqttQos.fromCode(qos))
                        .send();

                future.get(subscribeTimeoutSeconds, TimeUnit.SECONDS);
                log.info("Subscribed to topic {}, qos={}", topic, qos);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to subscribe topic " + topic + ": " + e.getMessage(), e);
        }
    }

    private void resubscribeAll() {
        if (!isConnected()) {
            return;
        }
        subscriptionCallbacks.forEach((topic, callback) -> {
            Integer qos = subscriptionQos.getOrDefault(topic, 1);
            try {
                sendSubscribe(topic, qos);
            } catch (Exception e) {
                log.warn("Failed to resubscribe topic {}: {}", topic, e.getMessage());
            }
        });
    }

    private void disconnectQuietly() {
        closeQuietly();
        activeProtocol = TransportProtocol.DISCONNECTED;
    }

    private void closeQuietly() {
        if (mqttClient == null) {
            return;
        }
        try {
            mqttClient.toAsync().disconnect().get(disconnectTimeoutSeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.debug("Ignoring MQTT disconnect error: {}", e.getMessage());
        }
        mqttClient = null;
    }

    private String generateBrokerToken() {
        return jwtUtil.generateToken(brokerUsername, brokerDomainCode, brokerRoleType);
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
