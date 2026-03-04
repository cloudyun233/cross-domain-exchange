
package com.crossdomain.exchange.config;

import com.crossdomain.exchange.mqtt.MqttClientService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MqttStartupInitializer {

    private final AppConfig appConfig;
    private final MqttClientService mqttClientService;

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("Initializing MQTT client with role: {}, instance-id: {}", 
                 appConfig.getRole(), appConfig.getInstanceId());
        
        mqttClientService.connect("TCP");
        
        if ("subscriber".equalsIgnoreCase(appConfig.getRole())) {
            mqttClientService.subscribe(appConfig.getDefaultTopic(), appConfig.getDefaultQos());
            log.info("Subscribed to topic: {} with QoS: {}", 
                     appConfig.getDefaultTopic(), appConfig.getDefaultQos());
        } else if ("publisher".equalsIgnoreCase(appConfig.getRole())) {
            log.info("Publisher role initialized. Ready to publish messages.");
        } else {
            log.warn("Unknown role: {}, defaulting to subscriber", appConfig.getRole());
            mqttClientService.subscribe(appConfig.getDefaultTopic(), appConfig.getDefaultQos());
        }
    }
}

