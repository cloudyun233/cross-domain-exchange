package com.cde.mqtt;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cde.entity.SysUser;
import com.cde.exception.BusinessException;
import com.cde.mapper.SysUserMapper;
import com.cde.util.MqttTopicUtil;
import com.hivemq.client.mqtt.MqttClient;
import com.hivemq.client.mqtt.MqttGlobalPublishFilter;
import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient;
import com.hivemq.client.mqtt.mqtt5.exceptions.Mqtt5PubAckException;
import com.hivemq.client.mqtt.mqtt5.exceptions.Mqtt5SubAckException;
import com.hivemq.client.mqtt.mqtt5.message.auth.Mqtt5SimpleAuth;
import com.hivemq.client.mqtt.mqtt5.message.connect.connack.Mqtt5ConnAck;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5PublishResult;
import com.hivemq.client.mqtt.mqtt5.message.publish.puback.Mqtt5PubAck;
import com.hivemq.client.mqtt.mqtt5.message.subscribe.suback.Mqtt5SubAck;
import com.hivemq.client.mqtt.mqtt5.message.subscribe.suback.Mqtt5SubAckReasonCode;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;

@Slf4j
@Component
@RequiredArgsConstructor
public class MqttClientService {

    private final SysUserMapper sysUserMapper;

    @Value("${mqtt.broker.host:localhost}")
    private String brokerHost;

    @Value("${mqtt.broker.tls-port:8883}")
    private int tlsPort;

    @Value("${mqtt.broker.tcp-port:1883}")
    private int tcpPort;

    @Value("${mqtt.broker.insecure-trust-all:false}")
    private boolean insecureTrustAll;

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
        final Object lock = new Object();
        volatile boolean connected;

        UserMqttContext(Mqtt5AsyncClient client) {
            this.client = client;
        }
    }

    public void connectForUser(String username, String jwtToken) {
        connectForUser(username, jwtToken, Map.of(), null);
    }

    public void connectForUser(
            String username,
            String jwtToken,
            Map<String, Integer> initialSubscriptions,
            BiConsumer<String, String> callback
    ) {
        UserMqttContext existingCtx = userContexts.get(username);
        if (existingCtx != null) {
            synchronized (existingCtx.lock) {
                seedSubscriptions(existingCtx, initialSubscriptions, callback);
                if (existingCtx.connected && existingCtx.client.getState().isConnected()) {
                    log.debug("User {} already connected, skip reconnect", username);
                    return;
                }
            }
        }

        log.info("Connecting MQTT for user: {}", username);
        disconnectForUser(username);

        try {
            String clientId = resolveClientId(username);
            Mqtt5AsyncClient mqttClient = buildTlsClient(clientId);
            UserMqttContext ctx = createContext(mqttClient, initialSubscriptions, callback);
            connectClient(mqttClient, username, jwtToken);
            markConnected(ctx);
            userContexts.put(username, ctx);
            log.info("MQTT connected for user {} over TLS: clientId={}, insecureTrustAll={}",
                    username, clientId, insecureTrustAll);
        } catch (TimeoutException e) {
            log.warn("MQTT TLS connection timeout for user {} after {} seconds, trying TCP",
                    username, connectTimeoutSeconds);
            if (!tryConnectTcpForUser(username, jwtToken, initialSubscriptions, callback)) {
                throw new BusinessException(HttpStatus.BAD_GATEWAY, "MQTT Broker 连接失败");
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("MQTT TLS connection failed for user {}, trying TCP: {}", username, e.getMessage());
            if (!tryConnectTcpForUser(username, jwtToken, initialSubscriptions, callback)) {
                throw new BusinessException(HttpStatus.BAD_GATEWAY, "MQTT Broker 连接失败");
            }
        }
    }

    private boolean tryConnectTcpForUser(
            String username,
            String jwtToken,
            Map<String, Integer> initialSubscriptions,
            BiConsumer<String, String> callback
    ) {
        try {
            String clientId = resolveClientId(username);
            Mqtt5AsyncClient mqttClient = MqttClient.builder()
                    .useMqttVersion5()
                    .identifier(clientId)
                    .serverHost(brokerHost)
                    .serverPort(tcpPort)
                    .automaticReconnectWithDefaultConfig()
                    .buildAsync();

            UserMqttContext ctx = createContext(mqttClient, initialSubscriptions, callback);
            connectClient(mqttClient, username, jwtToken);
            markConnected(ctx);
            userContexts.put(username, ctx);

            log.info("MQTT connected for user {} over TCP: clientId={}", username, clientId);
            return true;
        } catch (Exception e) {
            log.error("MQTT TCP connection failed for user {}: {}", username, e.getMessage());
            return false;
        }
    }

    public void subscribeForUser(String username, String topic, int qos, BiConsumer<String, String> callback) {
        UserMqttContext ctx = requireConnectedContext(username);
        synchronized (ctx.lock) {
            sendSubscribe(ctx.client, topic, qos);
            ctx.callbacks.put(topic, callback);
            ctx.qosMap.put(topic, qos);
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
        log.info("User {} unsubscribed locally from topic {}", username, topic);
    }

    public void publishForUser(String username, String topic, String payload, int qos) {
        UserMqttContext ctx = requireConnectedContext(username);
        synchronized (ctx.lock) {
            sendPublish(ctx.client, topic, payload, qos);
        }
        log.info("User {} published message: topic={}, qos={}", username, topic, qos);
    }

    public void disconnectForUser(String username) {
        UserMqttContext ctx = userContexts.remove(username);
        if (ctx == null || ctx.client == null) {
            return;
        }

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

    public boolean isUserConnected(String username) {
        UserMqttContext ctx = userContexts.get(username);
        if (ctx == null) {
            return false;
        }
        synchronized (ctx.lock) {
            return ctx.connected && ctx.client.getState().isConnected();
        }
    }

    public String getUserProtocol(String username) {
        UserMqttContext ctx = userContexts.get(username);
        if (ctx == null) {
            return "未连接";
        }
        synchronized (ctx.lock) {
            if (!ctx.connected || !ctx.client.getState().isConnected()) {
                return "未连接";
            }
            return resolveProtocol(ctx);
        }
    }

    public boolean isConnected() {
        return userContexts.values().stream().anyMatch(ctx -> ctx.connected && ctx.client.getState().isConnected());
    }

    public String getActiveProtocol() {
        for (UserMqttContext ctx : userContexts.values()) {
            if (ctx.connected && ctx.client.getState().isConnected()) {
                return resolveProtocol(ctx);
            }
        }
        return "未连接";
    }

    @PreDestroy
    public void destroy() {
        userContexts.keySet().forEach(this::disconnectForUser);
    }

    private UserMqttContext requireConnectedContext(String username) {
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
            return ctx;
        }
    }

    private UserMqttContext createContext(
            Mqtt5AsyncClient client,
            Map<String, Integer> initialSubscriptions,
            BiConsumer<String, String> callback
    ) {
        UserMqttContext ctx = new UserMqttContext(client);
        seedSubscriptions(ctx, initialSubscriptions, callback);
        setupPublishCallback(ctx);
        return ctx;
    }

    private void seedSubscriptions(
            UserMqttContext ctx,
            Map<String, Integer> initialSubscriptions,
            BiConsumer<String, String> callback
    ) {
        if (callback == null || initialSubscriptions == null || initialSubscriptions.isEmpty()) {
            return;
        }
        initialSubscriptions.forEach((topic, qos) -> {
            ctx.callbacks.put(topic, callback);
            ctx.qosMap.put(topic, qos);
        });
    }

    private void connectClient(Mqtt5AsyncClient client, String username, String jwtToken) throws Exception {
        Mqtt5SimpleAuth simpleAuth = Mqtt5SimpleAuth.builder()
                .username(username)
                .password(jwtToken.getBytes(StandardCharsets.UTF_8))
                .build();

        CompletableFuture<Mqtt5ConnAck> connectFuture = client.toAsync()
                .connectWith()
                .simpleAuth(simpleAuth)
                .cleanStart(false)
                .sessionExpiryInterval(3600)
                .keepAlive(60)
                .send();

        connectFuture.get(connectTimeoutSeconds, TimeUnit.SECONDS);
    }

    private String resolveClientId(String username) {
        SysUser user = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username));
        return user != null ? user.getClientId() : username;
    }

    private Mqtt5AsyncClient buildTlsClient(String clientId) {
        var builder = MqttClient.builder()
                .useMqttVersion5()
                .identifier(clientId)
                .serverHost(brokerHost)
                .serverPort(tlsPort);

        if (insecureTrustAll) {
            builder = builder.sslConfig()
                    .trustManagerFactory(InsecureTrustManagerFactory.INSTANCE)
                    .hostnameVerifier((hostname, session) -> true)
                    .applySslConfig();
        } else {
            builder = builder.sslWithDefaultConfig();
        }

        return builder
                .automaticReconnectWithDefaultConfig()
                .buildAsync();
    }

    private void setupPublishCallback(UserMqttContext ctx) {
        ctx.client.toAsync().publishes(MqttGlobalPublishFilter.ALL, publish -> {
            String topic = publish.getTopic().toString();
            String payload = new String(publish.getPayloadAsBytes(), StandardCharsets.UTF_8);
            log.debug("MQTT received publish: topic={}, payloadLength={}", topic, payload.length());

            ctx.callbacks.forEach((filter, callback) -> {
                if (MqttTopicUtil.matchesTopic(filter, topic)) {
                    try {
                        callback.accept(topic, payload);
                    } catch (Exception e) {
                        log.error("Error in subscription callback for topic {}: {}", topic, e.getMessage());
                    }
                }
            });
        });
    }

    private void markConnected(UserMqttContext ctx) {
        synchronized (ctx.lock) {
            ctx.connected = true;
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
            throw mapPublishException(topic, e);
        }
    }

    private void sendSubscribe(Mqtt5AsyncClient client, String topic, int qos) {
        if (client == null || !client.getState().isConnected()) {
            throw new IllegalStateException("MQTT broker is not connected");
        }

        try {
            CompletableFuture<Mqtt5SubAck> future = client.toAsync()
                    .subscribeWith()
                    .topicFilter(topic)
                    .qos(MqttQos.fromCode(qos))
                    .send();
            future.get(subscribeTimeoutSeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw mapSubscribeException(topic, e);
        }
    }

    private RuntimeException mapPublishException(String topic, Exception e) {
        Mqtt5PubAckException pubAckException = findCause(e, Mqtt5PubAckException.class);
        if (pubAckException != null) {
            Mqtt5PubAck ack = pubAckException.getMqttMessage();
            return switch (ack.getReasonCode()) {
                case NOT_AUTHORIZED -> new BusinessException(HttpStatus.FORBIDDEN, "无权发布该主题");
                case TOPIC_NAME_INVALID -> new BusinessException(HttpStatus.BAD_REQUEST, "发布主题不合法: " + topic);
                case PAYLOAD_FORMAT_INVALID -> new BusinessException(HttpStatus.BAD_REQUEST, "消息格式不合法");
                default -> new BusinessException(HttpStatus.BAD_REQUEST, "消息发布失败: " + describePublishReason(ack));
            };
        }

        Throwable root = unwrap(e);
        return new BusinessException(HttpStatus.BAD_GATEWAY, "消息发布失败: " + root.getMessage());
    }

    private RuntimeException mapSubscribeException(String topic, Exception e) {
        Mqtt5SubAckException subAckException = findCause(e, Mqtt5SubAckException.class);
        if (subAckException != null) {
            Mqtt5SubAck ack = subAckException.getMqttMessage();
            List<Mqtt5SubAckReasonCode> reasonCodes = ack.getReasonCodes();
            if (reasonCodes.contains(Mqtt5SubAckReasonCode.NOT_AUTHORIZED)) {
                return new BusinessException(HttpStatus.FORBIDDEN, "无权订阅该主题");
            }
            if (reasonCodes.contains(Mqtt5SubAckReasonCode.TOPIC_FILTER_INVALID)) {
                return new BusinessException(HttpStatus.BAD_REQUEST, "订阅主题不合法: " + topic);
            }
            return new BusinessException(HttpStatus.BAD_REQUEST, "订阅失败: " + describeSubscribeReason(ack));
        }

        Throwable root = unwrap(e);
        return new BusinessException(HttpStatus.BAD_GATEWAY, "订阅失败: " + root.getMessage());
    }

    private String describePublishReason(Mqtt5PubAck ack) {
        Optional<String> reasonString = ack.getReasonString().map(Object::toString);
        return reasonString.orElse(ack.getReasonCode().name());
    }

    private String describeSubscribeReason(Mqtt5SubAck ack) {
        Optional<String> reasonString = ack.getReasonString().map(Object::toString);
        if (reasonString.isPresent()) {
            return reasonString.get();
        }
        return ack.getReasonCodes().stream().map(Enum::name).reduce((left, right) -> left + "," + right).orElse("UNKNOWN");
    }

    private Throwable unwrap(Throwable throwable) {
        Throwable current = throwable;
        while ((current instanceof ExecutionException || current instanceof java.util.concurrent.CompletionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private <T extends Throwable> T findCause(Throwable throwable, Class<T> targetType) {
        Throwable current = throwable;
        while (current != null) {
            if (targetType.isInstance(current)) {
                return targetType.cast(current);
            }
            current = current.getCause();
        }
        return null;
    }

    private String resolveProtocol(UserMqttContext ctx) {
        return ctx.client.getConfig().getSslConfig().isPresent() ? "TLS" : "TCP";
    }
}
