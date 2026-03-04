
package com.crossdomain.exchange.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "app")
public class AppConfig {

    private String role = "subscriber";
    private String instanceId;
    private String defaultTopic = "cross-domain/exchange/default";
    private int defaultQos = 1;
}

