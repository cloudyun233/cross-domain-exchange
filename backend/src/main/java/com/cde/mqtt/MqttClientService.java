package com.cde.mqtt;

import com.hivemq.client.mqtt.MqttClient;
import com.hivemq.client.mqtt.MqttGlobalPublishFilter;
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient;
import com.hivemq.client.mqtt.mqtt5.message.auth.Mqtt5SimpleAuth;
import com.hivemq.client.mqtt.mqtt5.message.connect.connack.Mqtt5ConnAck;
import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5PublishResult;
import com.hivemq.client.mqtt.mqtt5.message.subscribe.suback.Mqtt5SubAck;
import jakarta.annotation.PreDestroy;
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

@Slf4j
@Component
public class MqttClientService {

    @Value("${mqtt.broker.host:localhost}")
    private String brokerHost;

    @Value("${mqtt.broker.tls-port:8883}")
    private int tlsPort;

    @Value("${mqtt.broker.tcp-port:1883}")
    private int tcpPort;

    @Value("${mqtt.connect.timeout-seconds:10}")
    private int connectTimeoutSeconds;

    @Value("${mqtt.publish.timeout-seconds:5}")
    private int publishTimeoutSeconds;

    @Value("${mqtt.subscribe.timeout-seconds:5}")
    private int subscribeTimeoutSeconds;

    @Value("${mqtt.disconnect.timeout-seconds:5}")
    private int disconnectTimeoutSeconds;

    private final Map<String, UserMqttContext> userContexts = new ConcurrentHashMap<>();

    private static class UserMqttContext {
        final Mqtt5AsyncClient client;
        final Map<String, BiConsumer<String, String>> callbacks = new ConcurrentHashMap<>();
        final Map<String, Integer> qosMap = new ConcurrentHashMap<>();
        volatile boolean connected;
        final Object lock = new Object();

        UserMqttContext(Mqtt5AsyncClient client) {
            this.client = client;
        }
    }

    public void connectForUser(String username, String jwtToken) {
        UserMqttContext existingCtx = userContexts.get(username);
        if (existingCtx != null) {
            synchronized (existingCtx.lock) {
                if (existingCtx.connected && existingCtx.client.getState().isConnected()) {
                    log.debug("User {} already connected, skip", username);
                    return;
                }
            }
        }

        log.info("Connecting MQTT for user: {}", username);
        disconnectForUser(username);

        try {
            String clientId = username;

            Mqtt5AsyncClient mqttClient = MqttClient.builder()
                    .useMqttVersion5()
                    .identifier(clientId)
                    .serverHost(brokerHost)
                    .serverPort(tlsPort)
                    .sslWithDefaultConfig()
                    .automaticReconnectWithDefaultConfig()
                    .buildAsync();

            Mqtt5SimpleAuth simpleAuth = Mqtt5SimpleAuth.builder()
                    .username(username)
                    .password(jwtToken.getBytes(StandardCharsets.UTF_8))
                    .build();

            CompletableFuture<Mqtt5ConnAck> connectFuture = mqttClient.toAsync()
                    .connectWith()
                    .simpleAuth(simpleAuth)
                    .cleanStart(false)
                    .sessionExpiryInterval(3600)
                    .keepAlive(60)
                    .send();

            Mqtt5ConnAck connAck = connectFuture.get(connectTimeoutSeconds, TimeUnit.SECONDS);

            UserMqttContext ctx = new UserMqttContext(mqttClient);
            synchronized (ctx.lock) {
                ctx.connected = true;
            }

            setupPublishCallback(ctx);
            userContexts.put(username, ctx);

            log.info("MQTT connected for user {} over TLS: clientId={}", username, clientId);

        } catch (TimeoutException e) {
            log.warn("MQTT connection timeout for user {} after {} seconds", username, connectTimeoutSeconds);
        } catch (Exception e) {
            log.warn("MQTT TLS connection failed for user {}, trying TCP: {}", username, e.getMessage());
            boolean tcpSuccess = tryConnectTcpForUser(username, jwtToken);
            if (!tcpSuccess) {
                log.warn("Both TLS and TCP connection attempts failed for user {}", username);
            }
        }
    }

    private boolean tryConnectTcpForUser(String username, String jwtToken) {
        try {
            String clientId = username;

            Mqtt5AsyncClient mqttClient = MqttClient.builder()
                    .useMqttVersion5()
                    .identifier(clientId)
                    .serverHost(brokerHost)
                    .serverPort(tcpPort)
                    .automaticReconnectWithDefaultConfig()
                    .buildAsync();

            Mqtt5SimpleAuth simpleAuth = Mqtt5SimpleAuth.builder()
                    .username(username)
                    .password(jwtToken.getBytes(StandardCharsets.UTF_8))
                    .build();

            CompletableFuture<Mqtt5ConnAck> connectFuture = mqttClient.toAsync()
                    .connectWith()
                    .simpleAuth(simpleAuth)
                    .cleanStart(false)
                    .sessionExpiryInterval(3600)
                    .keepAlive(60)
                    .send();

            Mqtt5ConnAck connAck = connectFuture.get(connectTimeoutSeconds, TimeUnit.SECONDS);

            UserMqttContext ctx = new UserMqttContext(mqttClient);
            synchronized (ctx.lock) {
                ctx.connected = true;
            }

            setupPublishCallback(ctx);
            userContexts.put(username, ctx);

            log.info("MQTT connected for user {} over TCP: clientId={}", username, clientId);
            return true;

        } catch (Exception e) {
            log.error("MQTT TCP connection failed for user {}: {}", username, e.getMessage());
            return false;
        }
    }

    private void setupPublishCallback(UserMqttContext ctx) {
        ctx.client.toAsync().publishes(MqttGlobalPublishFilter.ALL, publish -> {
            String topic = publish.getTopic().toString();
            String payload = new String(publish.getPayloadAsBytes(), StandardCharsets.UTF_8);
            log.debug("MQTT received publish for user: topic={}, payloadLength={}", topic, payload.length());

            ctx.callbacks.forEach((filter, callback) -> {
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

    public void subscribeForUser(String username, String topic, int qos, BiConsumer<String, String> callback) {
        UserMqttContext ctx = userContexts.get(username);
        if (ctx == null) {
            throw new IllegalStateException("User " + username + " is not connected to MQTT broker");
        }

        synchronized (ctx.lock) {
            if (!ctx.client.getState().isConnected()) {
                throw new IllegalStateException("User " + username + " is not connected to MQTT broker");
            }
            if (!ctx.connected) {
                log.info("MQTT client auto-reconnected for user {}, updating state", username);
                ctx.connected = true;
            }
            ctx.callbacks.put(topic, callback);
            ctx.qosMap.put(topic, qos);
            sendSubscribe(ctx.client, topic, qos);
        }
        log.info("User {} subscribed to topic {}, qos={}", username, topic, qos);
    }

    public void unsubscribeForUser(String username, String topic) {
        UserMqttContext ctx = userContexts.get(username);
        if (ctx == null) {
            return;
        }

        synchronized (ctx.lock) {
            ctx.callbacks.remove(topic);
            ctx.qosMap.remove(topic);
        }
        log.info("User {} unsubscribed from topic {}", username, topic);
    }

    public void publishForUser(String username, String topic, String payload, int qos) {
        UserMqttContext ctx = userContexts.get(username);
        if (ctx == null) {
            throw new IllegalStateException("User " + username + " is not connected to MQTT broker");
        }

        synchronized (ctx.lock) {
            if (!ctx.client.getState().isConnected()) {
                throw new IllegalStateException("User " + username + " is not connected to MQTT broker");
            }
            if (!ctx.connected) {
                log.info("MQTT client auto-reconnected for user {}, updating state", username);
                ctx.connected = true;
            }
            sendPublish(ctx.client, topic, payload, qos);
        }
        log.info("User {} published message: topic={}, qos={}", username, topic, qos);
    }

    public void disconnectForUser(String username) {
        UserMqttContext ctx = userContexts.remove(username);
        if (ctx != null && ctx.client != null) {
            synchronized (ctx.lock) {
                ctx.connected = false;
                ctx.callbacks.clear();
                ctx.qosMap.clear();
            }
            try {
                ctx.client.toAsync().disconnect().get(disconnectTimeoutSeconds, TimeUnit.SECONDS);
                log.info("Disconnected MQTT for user {}", username);
            } catch (Exception e) {
                log.debug("Ignoring MQTT disconnect error for user {}: {}", username, e.getMessage());
            }
        }
    }

    /**
     * 检查指定用户的 MQTT 连接状态
     *
     * @param username 用户名
     * @return true 如果该用户有活跃的 MQTT 连接
     */
    public boolean isUserConnected(String username) {
        UserMqttContext ctx = userContexts.get(username);
        if (ctx == null) {
            return false;
        }
        synchronized (ctx.lock) {
            return ctx.connected && ctx.client.getState().isConnected();
        }
    }

    private void sendPublish(Mqtt5AsyncClient client, String topic, String payload, int qos) {
        if (client == null || !client.getState().isConnected()) {
            throw new IllegalStateException("MQTT client not connected");
        }
        try {
            CompletableFuture<Mqtt5PublishResult> future = client.toAsync()
                    .publishWith()
                    .topic(topic)
                    .payload(payload.getBytes(StandardCharsets.UTF_8))
                    .qos(MqttQos.fromCode(qos))
                    .send();

            future.get(publishTimeoutSeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new RuntimeException("Failed to publish MQTT message: " + e.getMessage(), e);
        }
    }

    private void sendSubscribe(Mqtt5AsyncClient client, String topic, int qos) {
        try {
            if (client == null) {
                throw new IllegalStateException("MQTT broker is not connected");
            }

            CompletableFuture<Mqtt5SubAck> future = client.toAsync()
                    .subscribeWith()
                    .topicFilter(topic)
                    .qos(MqttQos.fromCode(qos))
                    .send();

            future.get(subscribeTimeoutSeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new RuntimeException("Failed to subscribe topic " + topic + ": " + e.getMessage(), e);
        }
    }

    /**
     * 检查后端是否有活跃的 MQTT 连接（用于运维监控）
     *
     * @return true 如果至少有一个用户存在活跃的 MQTT 连接
     */
    public boolean isConnected() {
        return userContexts.values().stream().anyMatch(ctx -> ctx.connected && ctx.client.getState().isConnected());
    }

    /**
     * 获取当前活跃连接的协议类型（用于运维监控）
     * 注意：当多个用户使用不同协议连接时，只返回第一个活跃连接的协议
     *
     * @return "TLS"、"TCP" 或 "未连接"
     */
    public String getActiveProtocol() {
        for (UserMqttContext ctx : userContexts.values()) {
            if (ctx.connected && ctx.client.getState().isConnected()) {
                return ctx.client.getConfig().getSslConfig().isPresent() ? "TLS" : "TCP";
            }
        }
        return "未连接";
    }

    @PreDestroy
    public void destroy() {
        userContexts.keySet().forEach(this::disconnectForUser);
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
