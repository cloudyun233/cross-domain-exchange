package com.cde.controller;

import com.cde.dto.ApiResponse;
import com.cde.service.MonitorService;
import com.cde.mqtt.MqttClientService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/monitor")
@RequiredArgsConstructor
public class MonitorController {
    private final MonitorService monitorService;
    private final MqttClientService mqttClientService;

    @GetMapping("/metrics")
    public ApiResponse<Map<String, Object>> getMetrics() {
        return ApiResponse.ok(monitorService.getSystemMetrics());
    }

    @GetMapping("/message-stats")
    public ApiResponse<Map<String, Object>> getMessageStats() {
        return ApiResponse.ok(monitorService.getTrafficStats());
    }

    @GetMapping("/client-stats")
    public ApiResponse<Map<String, Object>> getClientStats() {
        return ApiResponse.ok(monitorService.getClientStats());
    }

    @GetMapping("/topic-stats")
    public ApiResponse<Map<String, Object>> getTopicStats() {
        return ApiResponse.ok(monitorService.getTopicStats());
    }

    @GetMapping("/connection-status")
    public ApiResponse<Map<String, Object>> getConnectionStatus() {
        return ApiResponse.ok(Map.of(
                "connected", mqttClientService.isConnected(),
                "protocol", mqttClientService.getActiveProtocol()
        ));
    }
}
