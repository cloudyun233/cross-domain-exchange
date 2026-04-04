package com.cde.mqtt;

import com.hivemq.client.mqtt.MqttClient;
import com.hivemq.client.mqtt.MqttGlobalPublishFilter;
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient;
import com.hivemq.client.mqtt.mqtt3.message.auth.Mqtt3SimpleAuth;
import com.hivemq.client.mqtt.mqtt3.message.connect.connack.Mqtt3ConnAck;
import com.hivemq.client.mqtt.mqtt3.message.publish.Mqtt3Publish;
import com.hivemq.client.mqtt.mqtt3.message.subscribe.suback.Mqtt3SubAck;
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
        final Mqtt3AsyncClient client;
        final Map<String, BiConsumer<String, String>> callbacks = new ConcurrentHashMap<>();
        final Map<String, Integer> qosMap = new ConcurrentHashMap<>();
        volatile boolean connected;
        final Object lock = new Object();

        UserMqttContext(Mqtt3AsyncClient client) {
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

            Mqtt3AsyncClient mqttClient = MqttClient.builder()
                    .useMqttVersion3()
                    .identifier(clientId)
                    .serverHost(brokerHost)
                    .serverPort(tlsPort)
                    .sslWithDefaultConfig()
                    .automaticReconnectWithDefaultConfig()
                    .buildAsync();

            Mqtt3SimpleAuth simpleAuth = Mqtt3SimpleAuth.builder()
                    .username(username)
                    .password(jwtToken.getBytes(StandardCharsets.UTF_8))
                    .build();

            CompletableFuture<Mqtt3ConnAck> connectFuture = mqttClient.toAsync()
                    .connectWith()
                    .simpleAuth(simpleAuth)
                    .cleanSession(false)
                    .keepAlive(60)
                    .send();

            Mqtt3ConnAck connAck = connectFuture.get(connectTimeoutSeconds, TimeUnit.SECONDS);

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

            Mqtt3AsyncClient mqttClient = MqttClient.builder()
                    .useMqttVersion3()
                    .identifier(clientId)
                    .serverHost(brokerHost)
                    .serverPort(tcpPort)
                    .automaticReconnectWithDefaultConfig()
                    .buildAsync();

            Mqtt3SimpleAuth simpleAuth = Mqtt3SimpleAuth.builder()
                    .username(username)
                    .password(jwtToken.getBytes(StandardCharsets.UTF_8))
                    .build();

            CompletableFuture<Mqtt3ConnAck> connectFuture = mqttClient.toAsync()
                    .connectWith()
                    .simpleAuth(simpleAuth)
                    .cleanSession(false)
                    .keepAlive(60)
                    .send();

            Mqtt3ConnAck connAck = connectFuture.get(connectTimeoutSeconds, TimeUnit.SECONDS);

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
            if (!ctx.connected) {
                throw new IllegalStateException("User " + username + " is not connected to MQTT broker");
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
            if (!ctx.connected) {
                throw new IllegalStateException("User " + username + " is not connected to MQTT broker");
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

    public boolean isUserConnected(String username) {
        UserMqttContext ctx = userContexts.get(username);
        if (ctx == null) {
            return false;
        }
        synchronized (ctx.lock) {
            return ctx.connected && ctx.client.getState().isConnected();
        }
    }

    private void sendPublish(Mqtt3AsyncClient client, String topic, String payload, int qos) {
        if (client == null || !client.getState().isConnected()) {
            throw new IllegalStateException("MQTT client not connected");
        }
        try {
            CompletableFuture<Mqtt3Publish> future = client.toAsync()
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

    private void sendSubscribe(Mqtt3AsyncClient client, String topic, int qos) {
        try {
            if (client == null) {
                throw new IllegalStateException("MQTT broker is not connected");
            }

            CompletableFuture<Mqtt3SubAck> future = client.toAsync()
                    .subscribeWith()
                    .topicFilter(topic)
                    .qos(com.hivemq.client.mqtt.datatypes.MqttQos.fromCode(qos))
                    .send();

            future.get(subscribeTimeoutSeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new RuntimeException("Failed to subscribe topic " + topic + ": " + e.getMessage(), e);
        }
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
