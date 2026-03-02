package com.crossdomain.exchange.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * MQTT配置类
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "mqtt")
public class MqttConfig {

    private Broker broker = new Broker();
    private Retry retry = new Retry();

    @Data
    public static class Broker {
        private String host;
        private Integer tcpPort;
        private Integer tlsPort;
        private Integer quicPort;
        private String clientIdPrefix;
    }

    @Data
    public static class Retry {
        private Integer maxAttempts;
        private Long initialDelayMs;
        private Long maxDelayMs;
    }
}
