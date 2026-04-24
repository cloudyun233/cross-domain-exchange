package com.cde.mqtt;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cde.entity.SysUser;
import com.cde.exception.BusinessException;
import com.cde.mapper.SysUserMapper;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;

/**
 * MQTT 客户端封装 —— 精简重写版
 *
 * 核心原则：只使用 publishes(MqttGlobalPublishFilter.ALL, ...) 全局回调，
 *           不再在 subscribe 时注册 per-subscription callback，彻底消除双回调冲突。
 */
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

    /** 每个用户对应一个 MQTT 上下文 */
    private final Map<String, UserMqttContext> userContexts = new ConcurrentHashMap<>();

    // ═══════════════════════════════════════════════════════════════════════════
    //  用户上下文
    // ═══════════════════════════════════════════════════════════════════════════

    private static class UserMqttContext {
        final Mqtt5AsyncClient client;
        /** 全局消息监听器（收到消息后推 SSE） */
        volatile BiConsumer<String, String> messageListener;
        /** 已订阅的主题 → QoS（记忆用，断开后保留） */
        final Map<String, Integer> subscribedTopics = new ConcurrentHashMap<>();
        volatile boolean connected;

        UserMqttContext(Mqtt5AsyncClient client) {
            this.client = client;
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  对外公开方法
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * 连接 MQTT（发布场景用，无 messageListener）。
     * 兼容 TopicServiceImpl 等不需要订阅回调的调用方。
     */
    public void connectForUser(String username, String jwtToken) {
        connectForUser(username, jwtToken, null);
    }

    /**
     * 连接 MQTT（订阅场景用）。
     * - cleanStart=false, sessionExpiryInterval=3600
     * - 注册唯一全局 publishes() 回调
     * - 自动重新订阅所有已记忆的主题
     *
     * @param messageListener 收到消息后的回调（topic, payload），可为 null
     */
    public void connectForUser(String username, String jwtToken, BiConsumer<String, String> messageListener) {
        log.info("[MQTT] connectForUser 开始: username={}", username);

        // 若已有 context 且连接正常，仅更新 listener
        UserMqttContext existing = userContexts.get(username);
        if (existing != null && existing.connected && existing.client.getState().isConnected()) {
            if (messageListener != null) {
                existing.messageListener = messageListener;
                log.info("[MQTT] 用户 {} 已连接，仅更新 messageListener", username);
            }
            log.info("[MQTT] 用户 {} 已连接，跳过重连", username);
            return;
        }

        // 若已有旧 context 但断开了，先清理旧客户端
        if (existing != null) {
            log.info("[MQTT] 用户 {} 旧连接已断开，清理旧客户端", username);
            silentDisconnectClient(existing.client);
        }

        // 尝试 TLS → TCP
        try {
            String clientId = resolveClientId(username);
            log.info("[MQTT] 解析 clientId: username={} -> clientId={}", username, clientId);

            UserMqttContext ctx = doConnect(username, jwtToken, clientId, messageListener, true);
            preserveSubscriptions(existing, ctx);
            userContexts.put(username, ctx);

            log.info("[MQTT] 连接成功 (TLS): username={}, clientId={}", username, clientId);
            resubscribeAll(username, ctx);
            return;
        } catch (TimeoutException e) {
            log.warn("[MQTT] TLS 连接超时 ({}s)，尝试 TCP: username={}", connectTimeoutSeconds, username);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[MQTT] TLS 连接失败，尝试 TCP: username={}, error={}", username, e.getMessage());
        }

        // TCP 降级
        try {
            String clientId = resolveClientId(username);
            UserMqttContext ctx = doConnect(username, jwtToken, clientId, messageListener, false);
            preserveSubscriptions(existing, ctx);
            userContexts.put(username, ctx);

            log.info("[MQTT] 连接成功 (TCP): username={}, clientId={}", username, clientId);
            resubscribeAll(username, ctx);
        } catch (Exception e) {
            log.error("[MQTT] TCP 连接也失败: username={}, error={}", username, e.getMessage());
            throw new BusinessException(HttpStatus.BAD_GATEWAY, "MQTT Broker 连接失败");
        }
    }

    /**
     * 设置/更新消息监听器。
     * 在 SSE 建立后调用，确保消息有推送目标。
     */
    public void setMessageListener(String username, BiConsumer<String, String> listener) {
        UserMqttContext ctx = userContexts.get(username);
        if (ctx != null) {
            ctx.messageListener = listener;
            log.info("[MQTT] 已设置 messageListener: username={}", username);
        } else {
            log.warn("[MQTT] setMessageListener 时无 context: username={}", username);
        }
    }

    /**
     * 订阅主题（仅向 broker 发送 SUBSCRIBE 报文，不注册 per-subscription callback）。
     */
    public void subscribeForUser(String username, String topic, int qos) {
        UserMqttContext ctx = requireConnected(username);

        log.info("[MQTT] 订阅: username={}, topic={}, qos={}", username, topic, qos);

        try {
            CompletableFuture<Mqtt5SubAck> future = ctx.client.subscribeWith()
                    .topicFilter(topic)
                    .qos(MqttQos.fromCode(qos))
                    .send();
            Mqtt5SubAck ack = future.get(subscribeTimeoutSeconds, TimeUnit.SECONDS);

            log.info("[MQTT] 订阅成功: username={}, topic={}, qos={}, ackCodes={}",
                    username, topic, qos, ack.getReasonCodes());

            ctx.subscribedTopics.put(topic, qos);
        } catch (Exception e) {
            log.error("[MQTT] 订阅失败: username={}, topic={}, error={}", username, topic, e.getMessage());
            throw mapSubscribeException(topic, e);
        }
    }

    /**
     * 取消订阅（向 broker 发送 UNSUBSCRIBE + 清除本地记忆）。
     */
    public void unsubscribeForUser(String username, String topic) {
        UserMqttContext ctx = userContexts.get(username);
        if (ctx == null) {
            log.warn("[MQTT] unsubscribe 时无 context: username={}, topic={}", username, topic);
            return;
        }

        ctx.subscribedTopics.remove(topic);
        log.info("[MQTT] 本地已移除订阅记忆: username={}, topic={}", username, topic);

        if (ctx.connected && ctx.client.getState().isConnected()) {
            try {
                ctx.client.unsubscribeWith().topicFilter(topic).send()
                        .get(subscribeTimeoutSeconds, TimeUnit.SECONDS);
                log.info("[MQTT] broker 已取消订阅: username={}, topic={}", username, topic);
            } catch (Exception e) {
                log.warn("[MQTT] broker 取消订阅失败(忽略): username={}, topic={}, error={}",
                        username, topic, e.getMessage());
            }
        }
    }

    /**
     * 发布消息（发布场景用）。
     */
    public void publishForUser(String username, String topic, String payload, int qos, boolean retain) {
        UserMqttContext ctx = requireConnected(username);

        log.info("[MQTT] 发布: username={}, topic={}, qos={}, retain={}, payloadLen={}",
                username, topic, qos, retain, payload.length());

        try {
            CompletableFuture<Mqtt5PublishResult> future = ctx.client.publishWith()
                    .topic(topic)
                    .payload(payload.getBytes(StandardCharsets.UTF_8))
                    .qos(MqttQos.fromCode(qos))
                    .retain(retain)
                    .send();
            future.get(publishTimeoutSeconds, TimeUnit.SECONDS);
            log.info("[MQTT] 发布成功: username={}, topic={}", username, topic);
        } catch (Exception e) {
            log.error("[MQTT] 发布失败: username={}, topic={}, error={}", username, topic, e.getMessage());
            throw mapPublishException(topic, e);
        }
    }

    /**
     * 断开 MQTT（保留 messageListener 和订阅记忆）。
     * 下次 connectForUser 时会自动重新订阅。
     */
    public void disconnectForUser(String username) {
        UserMqttContext ctx = userContexts.get(username);
        if (ctx == null) {
            log.info("[MQTT] disconnectForUser 时无 context: username={}", username);
            return;
        }

        ctx.connected = false;
        log.info("[MQTT] 断开 MQTT: username={}, 保留订阅记忆={}", username,
                ctx.subscribedTopics.keySet());

        silentDisconnectClient(ctx.client);
        // 注意：不清空 subscribedTopics 和 messageListener，不从 userContexts 移除
    }

    /**
     * 完全清理（退出登录时调用）。
     * 取消所有订阅 + 断开 MQTT + 清空所有状态。
     */
    public void closeAll(String username) {
        UserMqttContext ctx = userContexts.remove(username);
        if (ctx == null) {
            log.info("[MQTT] closeAll 时无 context: username={}", username);
            return;
        }

        Set<String> topics = new HashSet<>(ctx.subscribedTopics.keySet());
        log.info("[MQTT] closeAll: username={}, 清理订阅={}", username, topics);

        // 先取消订阅
        if (ctx.connected && ctx.client.getState().isConnected()) {
            for (String topic : topics) {
                try {
                    ctx.client.unsubscribeWith().topicFilter(topic).send()
                            .get(subscribeTimeoutSeconds, TimeUnit.SECONDS);
                } catch (Exception e) {
                    log.warn("[MQTT] closeAll 取消订阅失败(忽略): topic={}, error={}", topic, e.getMessage());
                }
            }
        }

        ctx.subscribedTopics.clear();
        ctx.messageListener = null;
        ctx.connected = false;
        silentDisconnectClient(ctx.client);

        log.info("[MQTT] closeAll 完成: username={}", username);
    }

    /** 返回用户已记忆的订阅主题 */
    public Set<String> getSubscribedTopics(String username) {
        UserMqttContext ctx = userContexts.get(username);
        if (ctx == null) return Set.of();
        return Set.copyOf(ctx.subscribedTopics.keySet());
    }

    public boolean isUserConnected(String username) {
        UserMqttContext ctx = userContexts.get(username);
        return ctx != null && ctx.connected && ctx.client.getState().isConnected();
    }

    public String getUserProtocol(String username) {
        UserMqttContext ctx = userContexts.get(username);
        if (ctx == null || !ctx.connected || !ctx.client.getState().isConnected()) {
            return "未连接";
        }
        return resolveProtocol(ctx);
    }

    public boolean isConnected() {
        return userContexts.values().stream()
                .anyMatch(ctx -> ctx.connected && ctx.client.getState().isConnected());
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
        log.info("[MQTT] 应用关闭，清理所有连接");
        new HashSet<>(userContexts.keySet()).forEach(this::closeAll);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  内部方法
    // ═══════════════════════════════════════════════════════════════════════════

    private UserMqttContext doConnect(
            String username, String jwtToken, String clientId,
            BiConsumer<String, String> messageListener, boolean useTls
    ) throws Exception {
        Mqtt5AsyncClient client = useTls ? buildTlsClient(clientId) : buildTcpClient(clientId);
        UserMqttContext ctx = new UserMqttContext(client);
        ctx.messageListener = messageListener;

        // 注册唯一全局回调 —— 这是收消息的唯一路径
        setupGlobalPublishCallback(username, ctx);

        // 连接 broker
        Mqtt5SimpleAuth auth = Mqtt5SimpleAuth.builder()
                .username(username)
                .password(jwtToken.getBytes(StandardCharsets.UTF_8))
                .build();

        CompletableFuture<Mqtt5ConnAck> future = client.connectWith()
                .simpleAuth(auth)
                .cleanStart(false)
                .sessionExpiryInterval(3600)
                .keepAlive(60)
                .willPublish()
                    .topic("will/" + clientId)
                    .payload(("{\"clientId\":\"" + clientId + "\",\"status\":\"offline\"}")
                            .getBytes(StandardCharsets.UTF_8))
                    .qos(MqttQos.AT_LEAST_ONCE)
                    .retain(true)
                    .applyWillPublish()
                .send();

        Mqtt5ConnAck connAck = future.get(connectTimeoutSeconds, TimeUnit.SECONDS);
        ctx.connected = true;

        log.info("[MQTT] CONNACK 收到: username={}, clientId={}, sessionPresent={}, reasonCode={}",
                username, clientId, connAck.isSessionPresent(), connAck.getReasonCode());

        return ctx;
    }

    /**
     * 注册唯一全局 publishes() 回调。
     * 所有收到的消息（实时 + offline）都走这条路径。
     */
    private void setupGlobalPublishCallback(String username, UserMqttContext ctx) {
        ctx.client.publishes(MqttGlobalPublishFilter.ALL, publish -> {
            String topic = publish.getTopic().toString();
            String payload = new String(publish.getPayloadAsBytes(), StandardCharsets.UTF_8);

            log.info("[MQTT] <<< 收到消息: username={}, topic={}, payloadLen={}, payload={}",
                    username, topic, payload.length(),
                    payload.length() > 200 ? payload.substring(0, 200) + "..." : payload);

            BiConsumer<String, String> listener = ctx.messageListener;
            if (listener != null) {
                try {
                    listener.accept(topic, payload);
                    log.debug("[MQTT] 消息已推送到 listener: username={}, topic={}", username, topic);
                } catch (Exception e) {
                    log.error("[MQTT] listener 回调异常: username={}, topic={}, error={}",
                            username, topic, e.getMessage(), e);
                }
            } else {
                log.warn("[MQTT] 收到消息但无 listener: username={}, topic={}", username, topic);
            }
        });

        log.info("[MQTT] 全局 publishes() 回调已注册: username={}", username);
    }

    /**
     * 将旧 context 的订阅记忆迁移到新 context。
     */
    private void preserveSubscriptions(UserMqttContext oldCtx, UserMqttContext newCtx) {
        if (oldCtx != null && !oldCtx.subscribedTopics.isEmpty()) {
            newCtx.subscribedTopics.putAll(oldCtx.subscribedTopics);
            log.info("[MQTT] 迁移订阅记忆: topics={}", newCtx.subscribedTopics.keySet());
        }
    }

    /**
     * 重连后自动重新订阅所有已记忆的主题。
     */
    private void resubscribeAll(String username, UserMqttContext ctx) {
        if (ctx.subscribedTopics.isEmpty()) {
            log.info("[MQTT] 无记忆订阅需要恢复: username={}", username);
            return;
        }

        log.info("[MQTT] 开始恢复订阅: username={}, topics={}", username, ctx.subscribedTopics.keySet());
        ctx.subscribedTopics.forEach((topic, qos) -> {
            try {
                CompletableFuture<Mqtt5SubAck> future = ctx.client.subscribeWith()
                        .topicFilter(topic)
                        .qos(MqttQos.fromCode(qos))
                        .send();
                future.get(subscribeTimeoutSeconds, TimeUnit.SECONDS);
                log.info("[MQTT] 恢复订阅成功: username={}, topic={}, qos={}", username, topic, qos);
            } catch (Exception e) {
                log.error("[MQTT] 恢复订阅失败: username={}, topic={}, error={}", username, topic, e.getMessage());
            }
        });
    }

    private UserMqttContext requireConnected(String username) {
        UserMqttContext ctx = userContexts.get(username);
        if (ctx == null) {
            throw new IllegalStateException("用户 " + username + " 未建立 MQTT 连接");
        }
        if (!ctx.client.getState().isConnected()) {
            throw new IllegalStateException("用户 " + username + " 的 MQTT 连接已断开");
        }
        if (!ctx.connected) {
            log.info("[MQTT] 客户端自动重连检测: username={}", username);
            ctx.connected = true;
        }
        return ctx;
    }

    private String resolveClientId(String username) {
        SysUser user = sysUserMapper.selectOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username));
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

        return builder.automaticReconnectWithDefaultConfig().buildAsync();
    }

    private Mqtt5AsyncClient buildTcpClient(String clientId) {
        return MqttClient.builder()
                .useMqttVersion5()
                .identifier(clientId)
                .serverHost(brokerHost)
                .serverPort(tcpPort)
                .automaticReconnectWithDefaultConfig()
                .buildAsync();
    }

    private void silentDisconnectClient(Mqtt5AsyncClient client) {
        if (client == null) return;
        try {
            client.disconnect().get(disconnectTimeoutSeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.debug("[MQTT] 断开连接异常(忽略): {}", e.getMessage());
        }
    }

    private String resolveProtocol(UserMqttContext ctx) {
        return ctx.client.getConfig().getSslConfig().isPresent() ? "TLS" : "TCP";
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  异常映射（与旧版一致）
    // ═══════════════════════════════════════════════════════════════════════════

    private RuntimeException mapPublishException(String topic, Exception e) {
        Mqtt5PubAckException pubAckException = findCause(e, Mqtt5PubAckException.class);
        if (pubAckException != null) {
            Mqtt5PubAck ack = pubAckException.getMqttMessage();
            String reasonString = ack.getReasonString().map(Object::toString).orElse("").toLowerCase();
            return switch (ack.getReasonCode()) {
                case NOT_AUTHORIZED -> new BusinessException(HttpStatus.FORBIDDEN, "无权发布该主题");
                case TOPIC_NAME_INVALID -> new BusinessException(HttpStatus.BAD_REQUEST, "发布主题不合法: " + topic);
                case PAYLOAD_FORMAT_INVALID -> new BusinessException(HttpStatus.BAD_REQUEST, "消息格式不合法");
                default -> {
                    if (reasonString.contains("not authorized") || reasonString.contains("acl")) {
                        yield new BusinessException(HttpStatus.FORBIDDEN, "无权发布该主题");
                    }
                    yield new BusinessException(HttpStatus.BAD_REQUEST,
                            "消息发布失败: " + ack.getReasonString().map(Object::toString).orElse(ack.getReasonCode().name()));
                }
            };
        }
        Throwable root = unwrap(e);
        String msg = root.getMessage() != null ? root.getMessage().toLowerCase() : "";
        if (msg.contains("not authorized") || msg.contains("acl")) {
            return new BusinessException(HttpStatus.FORBIDDEN, "无权发布该主题");
        }
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
            return new BusinessException(HttpStatus.BAD_REQUEST, "订阅失败: " +
                    ack.getReasonString().map(Object::toString)
                            .orElse(ack.getReasonCodes().stream().map(Enum::name)
                                    .reduce((a, b) -> a + "," + b).orElse("UNKNOWN")));
        }
        Throwable root = unwrap(e);
        return new BusinessException(HttpStatus.BAD_GATEWAY, "订阅失败: " + root.getMessage());
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
}
