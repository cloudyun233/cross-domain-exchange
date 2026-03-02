package com.crossdomain.exchange.controller;

import com.crossdomain.exchange.dto.ApiResponse;
import com.crossdomain.exchange.mqtt.MqttClientService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * MQTT控制器
 */
@RestController
@RequestMapping("/api/mqtt")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class MqttController {

    private final MqttClientService mqttClientService;

    @PostMapping("/connect")
    public ApiResponse<Map<String, Object>> connect(@RequestParam(defaultValue = "TCP") String protocol) {
        try {
            mqttClientService.connect(protocol);
            Map<String, Object> result = new HashMap<>();
            result.put("connected", true);
            result.put("protocol", protocol);
            return ApiResponse.success("连接成功", result);
        } catch (Exception e) {
            return ApiResponse.error("连接失败: " + e.getMessage());
        }
    }

    @PostMapping("/disconnect")
    public ApiResponse<Void> disconnect() {
        mqttClientService.disconnect();
        return ApiResponse.success("断开连接成功", null);
    }

    @GetMapping("/status")
    public ApiResponse<Map<String, Object>> getStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("connected", mqttClientService.isConnected());
        status.put("protocol", mqttClientService.getCurrentProtocol());
        return ApiResponse.success(status);
    }

    @PostMapping("/publish")
    public ApiResponse<Void> publish(
            @RequestParam String topic,
            @RequestParam String payload,
            @RequestParam(defaultValue = "1") int qos,
            @RequestParam(defaultValue = "false") boolean retained,
            @RequestParam(defaultValue = "domain-1") String sourceDomain) {
        try {
            mqttClientService.publish(topic, payload, qos, retained, sourceDomain);
            return ApiResponse.success("消息发布成功", null);
        } catch (Exception e) {
            return ApiResponse.error("消息发布失败: " + e.getMessage());
        }
    }

    @PostMapping("/subscribe")
    public ApiResponse<Void> subscribe(
            @RequestParam String topic,
            @RequestParam(defaultValue = "1") int qos) {
        try {
            mqttClientService.subscribe(topic, qos);
            return ApiResponse.success("订阅成功", null);
        } catch (Exception e) {
            return ApiResponse.error("订阅失败: " + e.getMessage());
        }
    }

    @PostMapping("/unsubscribe")
    public ApiResponse<Void> unsubscribe(@RequestParam String topic) {
        mqttClientService.unsubscribe(topic);
        return ApiResponse.success("取消订阅成功", null);
    }
}
