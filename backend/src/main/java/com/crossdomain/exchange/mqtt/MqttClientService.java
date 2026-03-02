package com.crossdomain.exchange.mqtt;

import com.crossdomain.exchange.config.MqttConfig;
import com.crossdomain.exchange.entity.Message;
import com.crossdomain.exchange.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * MQTT客户端服务类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MqttClientService {

    private final MqttConfig mqttConfig;
    private final MessageRepository messageRepository;

    private MqttClient mqttClient;
    private String currentProtocol = "TCP";

    public void connect(String protocol) {
        try {
            String clientId = mqttConfig.getBroker().getClientIdPrefix() + "-" + UUID.randomUUID();
            String brokerUrl;
            int port;

            switch (protocol.toUpperCase()) {
                case "TLS":
                    port = mqttConfig.getBroker().getTlsPort();
                    brokerUrl = "ssl://" + mqttConfig.getBroker().getHost() + ":" + port;
                    break;
                case "TCP":
                default:
                    port = mqttConfig.getBroker().getTcpPort();
                    brokerUrl = "tcp://" + mqttConfig.getBroker().getHost() + ":" + port;
                    break;
            }

            MemoryPersistence persistence = new MemoryPersistence();
            mqttClient = new MqttClient(brokerUrl, clientId, persistence);

            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(true);
            options.setAutomaticReconnect(true);
            options.setConnectionTimeout(10);
            options.setKeepAliveInterval(30);

            mqttClient.setCallback(new MqttCallback() {
                @Override
                public void connectionLost(Throwable cause) {
                    log.warn("MQTT连接丢失，开始重试...", cause);
                    retryConnect();
                }

                @Override
                public void messageArrived(String topic, MqttMessage message) {
                    handleIncomingMessage(topic, message);
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {
                    log.debug("消息发送完成: {}", token.getMessageId());
                }
            });

            log.info("正在连接MQTT Broker: {}", brokerUrl);
            mqttClient.connect(options);
            currentProtocol = protocol;
            log.info("MQTT连接成功，协议: {}", protocol);

        } catch (MqttException e) {
            log.error("MQTT连接失败", e);
            throw new RuntimeException("MQTT连接失败: " + e.getMessage());
        }
    }

    private void retryConnect() {
        AtomicInteger attempts = new AtomicInteger(0);
        int maxAttempts = mqttConfig.getRetry().getMaxAttempts();
        long initialDelay = mqttConfig.getRetry().getInitialDelayMs();
        long maxDelay = mqttConfig.getRetry().getMaxDelayMs();

        new Thread(() -> {
            while (attempts.get() < maxAttempts && !isConnected()) {
                try {
                    long delay = Math.min(initialDelay * (long) Math.pow(2, attempts.get()), maxDelay);
                    log.info("等待 {}ms 后重试连接 (第 {}/{} 次)", delay, attempts.get() + 1, maxAttempts);
                    Thread.sleep(delay);
                    
                    connect(currentProtocol);
                    if (isConnected()) {
                        log.info("MQTT重连成功");
                        return;
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (Exception e) {
                    log.error("重试连接失败", e);
                }
                attempts.incrementAndGet();
            }
            log.error("MQTT重连失败，已达到最大重试次数: {}", maxAttempts);
        }).start();
    }

    public boolean isConnected() {
        return mqttClient != null && mqttClient.isConnected();
    }

    public void disconnect() {
        try {
            if (mqttClient != null && mqttClient.isConnected()) {
                mqttClient.disconnect();
                log.info("MQTT连接已断开");
            }
        } catch (MqttException e) {
            log.error("断开MQTT连接失败", e);
        }
    }

    public void publish(String topic, String payload, int qos, boolean retained, String sourceDomain) {
        try {
            if (!isConnected()) {
                connect(currentProtocol);
            }

            long sendTimestamp = System.currentTimeMillis();
            MqttMessage message = new MqttMessage(payload.getBytes());
            message.setQos(qos);
            message.setRetained(retained);

            mqttClient.publish(topic, message);
            log.info("消息已发布: topic={}, qos={}", topic, qos);

            saveMessage(topic, payload, qos, retained, sourceDomain, sendTimestamp, true, null);

        } catch (MqttException e) {
            log.error("发布消息失败", e);
            saveMessage(topic, payload, qos, retained, sourceDomain, System.currentTimeMillis(), false, e.getMessage());
        }
    }

    public void subscribe(String topic, int qos) {
        try {
            if (!isConnected()) {
                connect(currentProtocol);
            }
            mqttClient.subscribe(topic, qos);
            log.info("已订阅主题: {}, qos={}", topic, qos);
        } catch (MqttException e) {
            log.error("订阅主题失败: {}", topic, e);
            throw new RuntimeException("订阅失败: " + e.getMessage());
        }
    }

    public void unsubscribe(String topic) {
        try {
            if (mqttClient != null && mqttClient.isConnected()) {
                mqttClient.unsubscribe(topic);
                log.info("已取消订阅主题: {}", topic);
            }
        } catch (MqttException e) {
            log.error("取消订阅失败: {}", topic, e);
        }
    }

    private void handleIncomingMessage(String topic, MqttMessage mqttMessage) {
        long receiveTimestamp = System.currentTimeMillis();
        String payload = new String(mqttMessage.getPayload());
        
        log.info("收到消息: topic={}, qos={}", topic, mqttMessage.getQos());

        Message message = Message.builder()
                .messageId(UUID.randomUUID().toString())
                .topicName(topic)
                .sourceDomain("unknown")
                .payload(payload)
                .qos(mqttMessage.getQos())
                .retained(mqttMessage.isRetained())
                .protocolType(currentProtocol)
                .receiveTimestamp(receiveTimestamp)
                .success(true)
                .build();

        messageRepository.save(message);
    }

    private void saveMessage(String topic, String payload, int qos, boolean retained, 
                            String sourceDomain, long sendTimestamp, boolean success, String errorMessage) {
        Message message = Message.builder()
                .messageId(UUID.randomUUID().toString())
                .topicName(topic)
                .sourceDomain(sourceDomain)
                .payload(payload)
                .qos(qos)
                .retained(retained)
                .protocolType(currentProtocol)
                .sendTimestamp(sendTimestamp)
                .success(success)
                .errorMessage(errorMessage)
                .build();

        messageRepository.save(message);
    }

    public String getCurrentProtocol() {
        return currentProtocol;
    }
}
