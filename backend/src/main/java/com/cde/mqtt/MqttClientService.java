package com.cde.mqtt;

import com.cde.security.JwtUtil;
import io.sisu.nng.Message;
import io.sisu.nng.Nng;
import io.sisu.nng.NngException;
import io.sisu.nng.Socket;
import io.sisu.nng.internal.mqtt.constants.MqttPacketType;
import io.sisu.nng.mqtt.MqttClientSocket;
import io.sisu.nng.mqtt.MqttQuicClientSocket;
import io.sisu.nng.mqtt.data.TopicQos;
import io.sisu.nng.mqtt.msg.PublishMsg;
import io.sisu.nng.mqtt.msg.ConnectMsg;
import io.sisu.nng.mqtt.msg.SubscribeMsg;
import io.sisu.nng.internal.jna.UInt32ByReference;
import io.sisu.nng.internal.mqtt.BytesPointer;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;

/**
 * Backend MQTT client backed by NanoSDK.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MqttClientService {

    private enum TransportProtocol {
        DISCONNECTED,
        QUIC,
        TLS,
        TCP
    }

    private static final int CONNECT_TIMEOUT_MS = 5000;

    private final JwtUtil jwtUtil;

    @Value("${mqtt.broker.host:localhost}")
    private String brokerHost;

    @Value("${mqtt.broker.quic-port:14567}")
    private int quicPort;

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

    @Value("${mqtt.retry.max-delay-ms:30000}")
    private long maxRetryDelayMs;

    private final Map<String, BiConsumer<String, String>> subscriptionCallbacks = new ConcurrentHashMap<>();
    private final Map<String, Integer> subscriptionQos = new ConcurrentHashMap<>();
    private final Object socketLock = new Object();
    private final AtomicBoolean reconnectPending = new AtomicBoolean(false);
    private final ScheduledExecutorService reconnectExecutor = Executors.newSingleThreadScheduledExecutor(
            new DaemonThreadFactory("mqtt-reconnect"));

    private volatile Socket mqttClient;
    private volatile NanoSdkTlsConfig tlsConfig;
    private volatile TransportProtocol activeProtocol = TransportProtocol.DISCONNECTED;
    private volatile boolean connected;
    private volatile boolean shuttingDown;
    private volatile String activeClientId;

    @PostConstruct
    public void init() {
        connectWithFallback();
    }

    @PreDestroy
    public void destroy() {
        shuttingDown = true;
        reconnectExecutor.shutdownNow();
        disconnectQuietly();
    }

    public void publish(String topic, String payload, int qos) {
        ensureConnected();
        synchronized (socketLock) {
            if (!isConnected() || mqttClient == null) {
                throw new IllegalStateException("MQTT broker is not connected");
            }
            sendPublish(mqttClient, topic, payload, qos);
            log.info("Published MQTT message over {}: topic={}, qos={}", activeProtocol, topic, qos);
        }
    }

    public void subscribe(String topic, int qos, BiConsumer<String, String> callback) {
        boolean wasConnected = isConnected();
        Integer previousQos = subscriptionQos.put(topic, qos);
        subscriptionCallbacks.put(topic, callback);

        ensureConnected();

        if (wasConnected && (previousQos == null || previousQos != qos)) {
            synchronized (socketLock) {
                sendSubscribe(topic, qos);
            }
        }
    }

    public String getActiveProtocol() {
        return activeProtocol.name();
    }

    public boolean isConnected() {
        return connected && mqttClient != null;
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
        synchronized (socketLock) {
            disconnectQuietly();

            String clientId = clientIdPrefix + "-" + System.currentTimeMillis();
            List<TransportAttempt> attempts = List.of(
                    new TransportAttempt(TransportProtocol.QUIC, () -> openQuicTransport()),
                    new TransportAttempt(TransportProtocol.TLS, () -> openTlsTransport()),
                    new TransportAttempt(TransportProtocol.TCP, () -> openTcpTransport())
            );

            for (TransportAttempt attempt : attempts) {
                try {
                    AcquiredTransport transport = attempt.open();
                    try {
                        transport.socket.setSendTimeout(CONNECT_TIMEOUT_MS);
                        transport.socket.setReceiveTimeout(CONNECT_TIMEOUT_MS);
                        sendConnect(transport.socket, clientId);
                        waitForConnack(transport.socket, attempt.protocol);

                        mqttClient = transport.socket;
                        tlsConfig = transport.tlsConfig;
                        activeProtocol = attempt.protocol;
                        connected = true;
                        activeClientId = clientId;

                        startReceiverLoop();
                        resubscribeAll();
                        log.info("MQTT connected over {}: {}", attempt.protocol, describeTransport(attempt.protocol));
                        return true;
                    } catch (Exception e) {
                        closeQuietly(transport.socket);
                        closeQuietly(transport.tlsConfig);
                        log.warn("MQTT {} connection failed: {}", attempt.protocol, e.getMessage());
                    }
                } catch (Exception e) {
                    log.warn("MQTT {} connection preparation failed: {}", attempt.protocol, e.getMessage());
                }
            }

            activeProtocol = TransportProtocol.DISCONNECTED;
            connected = false;
            mqttClient = null;
            tlsConfig = null;
            activeClientId = null;
            log.warn("MQTT connection failed for all fallback protocols");
            scheduleReconnect();
            return false;
        }
    }

    private AcquiredTransport openQuicTransport() throws Exception {
        String url = "mqtt-quic://" + brokerHost + ":" + quicPort;
        Socket socket = new MqttQuicClientSocket(url);
        return new AcquiredTransport(socket, null, TransportProtocol.QUIC);
    }

    private AcquiredTransport openTlsTransport() throws Exception {
        String url = "tls+tcp://" + brokerHost + ":" + tlsPort;
        NanoSdkTlsConfig config = NanoSdkTlsConfig.insecureClient(brokerHost);
        MqttClientSocket socket = new MqttClientSocket();
        try {
            config.applyTo(socket);
            socket.dial(url);
            return new AcquiredTransport(socket, config, TransportProtocol.TLS);
        } catch (Exception e) {
            closeQuietly(socket);
            closeQuietly(config);
            throw e;
        }
    }

    private AcquiredTransport openTcpTransport() throws Exception {
        String url = "tcp://" + brokerHost + ":" + tcpPort;
        MqttClientSocket socket = new MqttClientSocket();
        try {
            socket.dial(url);
            return new AcquiredTransport(socket, null, TransportProtocol.TCP);
        } catch (Exception e) {
            closeQuietly(socket);
            throw e;
        }
    }

    private void sendConnect(Socket socket, String clientId) throws NngException {
        ConnectMsg connectMsg = new ConnectMsg();
        connectMsg.setCleanSession(false);
        connectMsg.setKeepAlive((short) 60);
        connectMsg.setClientId(clientId);
        connectMsg.setUserName(brokerUsername);
        connectMsg.setPassword(generateBrokerToken());
        connectMsg.setProtoVersion(4);
        socket.sendMessage(connectMsg);
    }

    private void waitForConnack(Socket socket, TransportProtocol protocol) throws NngException {
        long started = System.currentTimeMillis();
        while (System.currentTimeMillis() - started <= CONNECT_TIMEOUT_MS) {
            try (Message message = socket.receiveMessage()) {
                MqttPacketType packetType = getPacketType(message);
                if (packetType == null) {
                    continue;
                }
                if (packetType == MqttPacketType.NNG_MQTT_CONNACK) {
                    byte returnCode = Nng.lib().nng_mqtt_msg_get_connack_return_code(message.getMessagePointer());
                    if (returnCode != 0) {
                        throw new IllegalStateException(protocol + " connection rejected, returnCode=" + returnCode);
                    }
                    return;
                }
                if (packetType == MqttPacketType.NNG_MQTT_PUBLISH) {
                    dispatchPublish(message);
                    continue;
                }
                log.debug("Ignoring MQTT packet during connect: {}", packetType);
            } catch (NngException e) {
                if (isTimeout(e)) {
                    throw new IllegalStateException(protocol + " connect timed out", e);
                }
                throw e;
            }
        }

        throw new IllegalStateException(protocol + " connect timed out");
    }

    private void startReceiverLoop() {
        Thread thread = new Thread(this::receiveLoop, "mqtt-receiver-" + activeProtocol.name().toLowerCase());
        thread.setDaemon(true);
        thread.start();
    }

    private void receiveLoop() {
        while (!shuttingDown && isConnected()) {
            try (Message message = mqttClient.receiveMessage()) {
                handleMessage(message);
            } catch (NngException e) {
                if (shuttingDown || isTimeout(e)) {
                    continue;
                }
                log.warn("MQTT {} receiver stopped: {}", activeProtocol, e.getMessage());
                handleConnectionLoss();
                return;
            } catch (Exception e) {
                if (!shuttingDown) {
                    log.warn("MQTT {} receiver error: {}", activeProtocol, e.getMessage());
                    handleConnectionLoss();
                }
                return;
            }
        }
    }

    private void handleMessage(Message message) throws NngException {
        MqttPacketType packetType = getPacketType(message);
        if (packetType == null) {
            log.debug("Ignoring unknown MQTT packet");
            return;
        }

        log.debug("MQTT {} receiver packet: {}", activeProtocol, packetType);
        switch (packetType) {
            case NNG_MQTT_PUBLISH -> dispatchPublish(message);
            case NNG_MQTT_CONNACK, NNG_MQTT_PUBACK, NNG_MQTT_PUBREC, NNG_MQTT_PUBREL,
                 NNG_MQTT_PUBCOMP, NNG_MQTT_SUBACK, NNG_MQTT_UNSUBACK,
                 NNG_MQTT_PINGREQ, NNG_MQTT_PINGRESP, NNG_MQTT_DISCONNECT,
                 NNG_MQTT_AUTH -> log.debug("Ignoring MQTT control packet: {}", packetType);
            default -> log.debug("Ignoring MQTT packet: {}", packetType);
        }
    }

    private MqttPacketType getPacketType(Message message) {
        byte packetType = Nng.lib().nng_mqtt_msg_get_packet_type(message.getMessagePointer());
        return MqttPacketType.getFromValue(packetType);
    }

    private void dispatchPublish(Message message) {
        String topic = getPublishTopic(message);
        String payload = getPublishPayload(message);
        log.debug("MQTT {} dispatch publish: topic={}, payloadLength={}", activeProtocol, topic, payload.length());
        subscriptionCallbacks.forEach((filter, callback) -> {
            if (matchesTopic(filter, topic)) {
                callback.accept(topic, payload);
            }
        });
    }

    private String getPublishTopic(Message message) {
        UInt32ByReference lengthRef = new UInt32ByReference();
        String topic = Nng.lib().nng_mqtt_msg_get_publish_topic(message.getMessagePointer(), lengthRef);
        int topicLength = lengthRef.getUInt32().intValue();
        return topic.substring(0, topicLength);
    }

    private String getPublishPayload(Message message) {
        UInt32ByReference lengthRef = new UInt32ByReference();
        BytesPointer payload = Nng.lib().nng_mqtt_msg_get_publish_payload(message.getMessagePointer(), lengthRef);
        int payloadLength = lengthRef.getUInt32().intValue();
        if (payload == null || payloadLength <= 0) {
            return "";
        }
        return StandardCharsets.UTF_8.decode(payload.getPointer().getByteBuffer(0, payloadLength)).toString();
    }

    private void sendPublish(Socket socket, String topic, String payload, int qos) {
        try {
            PublishMsg message = new PublishMsg();
            message.setTopic(topic);
            message.setPayload(payload);
            message.setQos((byte) qos);
            socket.sendMessage(message);
        } catch (Exception e) {
            throw new RuntimeException("Failed to publish MQTT message: " + e.getMessage(), e);
        }
    }

    private void sendSubscribe(String topic, int qos) {
        try {
            synchronized (socketLock) {
                if (mqttClient == null) {
                    throw new IllegalStateException("MQTT broker is not connected");
                }
                SubscribeMsg subscribeMsg = new SubscribeMsg(List.of(new TopicQos(topic, (byte) qos)));
                mqttClient.sendMessage(subscribeMsg);
                log.info("Subscribed to topic {}, qos={}", topic, qos);
            }
        } catch (Exception e) {
            handleConnectionLoss();
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

    private void handleConnectionLoss() {
        synchronized (socketLock) {
            connected = false;
            activeProtocol = TransportProtocol.DISCONNECTED;
            closeQuietly(mqttClient);
            closeQuietly(tlsConfig);
            mqttClient = null;
            tlsConfig = null;
            activeClientId = null;
        }
        scheduleReconnect();
    }

    private void scheduleReconnect() {
        if (!reconnectPending.compareAndSet(false, true) || shuttingDown) {
            return;
        }
        reconnectExecutor.execute(() -> {
            try {
                long delay = Math.max(0L, initialRetryDelayMs);
                for (int attempt = 1; attempt <= maxRetryAttempts && !shuttingDown; attempt++) {
                    try {
                        TimeUnit.MILLISECONDS.sleep(delay);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        return;
                    }

                    if (connectWithFallback()) {
                        return;
                    }

                    delay = Math.min(delay * 2, maxRetryDelayMs);
                }
            } finally {
                reconnectPending.set(false);
            }
        });
    }

    private void disconnectQuietly() {
        closeQuietly(mqttClient);
        closeQuietly(tlsConfig);
        mqttClient = null;
        tlsConfig = null;
        connected = false;
        activeProtocol = TransportProtocol.DISCONNECTED;
    }

    private void closeQuietly(Socket socket) {
        if (socket == null) {
            return;
        }
        try {
            socket.close();
        } catch (Exception e) {
            log.debug("Ignoring MQTT close error: {}", e.getMessage());
        }
    }

    private void closeQuietly(NanoSdkTlsConfig config) {
        if (config == null) {
            return;
        }
        try {
            config.close();
        } catch (Exception e) {
            log.debug("Ignoring TLS config close error: {}", e.getMessage());
        }
    }

    private boolean isTimeout(Throwable e) {
        String message = e.getMessage();
        if (!StringUtils.hasText(message)) {
            return false;
        }
        String lower = message.toLowerCase();
        return lower.contains("timed out") || lower.contains("timeout");
    }

    private String generateBrokerToken() {
        return jwtUtil.generateToken(brokerUsername, brokerDomainCode, brokerRoleType);
    }

    private String describeTransport(TransportProtocol protocol) {
        return switch (protocol) {
            case QUIC -> "mqtt-quic://" + brokerHost + ":" + quicPort;
            case TLS -> "tls+tcp://" + brokerHost + ":" + tlsPort;
            case TCP -> "tcp://" + brokerHost + ":" + tcpPort;
            default -> "disconnected";
        };
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

    private record AcquiredTransport(Socket socket, NanoSdkTlsConfig tlsConfig, TransportProtocol protocol) {
    }

    private record TransportAttempt(TransportProtocol protocol, TransportSupplier supplier) {
        AcquiredTransport open() throws Exception {
            return supplier.open();
        }
    }

    @FunctionalInterface
    private interface TransportSupplier {
        AcquiredTransport open() throws Exception;
    }

    private static final class DaemonThreadFactory implements ThreadFactory {
        private final String prefix;
        private int index = 0;

        private DaemonThreadFactory(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public synchronized Thread newThread(Runnable r) {
            Thread thread = new Thread(r, prefix + "-" + (++index));
            thread.setDaemon(true);
            return thread;
        }
    }
}
