package com.cde.controller;

import com.cde.dto.ApiResponse;
import com.cde.service.MonitorService;
import com.cde.mqtt.MqttClientService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

/**
 * 系统监控 REST API
 *
 * <p>提供系统运行状态和 MQTT 消息统计信息，所有接口仅限管理员访问。
 * 数据来源包括 EMQX Broker API（消息/客户端/主题统计）和 JVM 运行时（系统指标）。
 * <ul>
 *   <li>GET /api/monitor/metrics          — 系统指标（JVM 内存、CPU 等）</li>
 *   <li>GET /api/monitor/message-stats    — 消息流量统计（来自 EMQX API）</li>
 *   <li>GET /api/monitor/client-stats     — 客户端连接统计（来自 EMQX API）</li>
 *   <li>GET /api/monitor/topic-stats      — 主题统计（来自 EMQX API）</li>
 *   <li>GET /api/monitor/connection-status — 本服务 MQTT 连接状态</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/monitor")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class MonitorController {
    private final MonitorService monitorService;
    private final MqttClientService mqttClientService;

    /**
     * 获取系统指标
     *
     * <p>返回 JVM 运行时指标，包括堆内存使用、线程数等。
     *
     * @return 系统指标键值对
     */
    @GetMapping("/metrics")
    public ApiResponse<Map<String, Object>> getMetrics() {
        return ApiResponse.ok(monitorService.getSystemMetrics());
    }

    /**
     * 获取消息流量统计
     *
     * <p>从 EMQX Broker API 获取消息收发量、吞吐率等统计数据。
     *
     * @return 消息流量统计键值对
     */
    @GetMapping("/message-stats")
    public ApiResponse<Map<String, Object>> getMessageStats() {
        return ApiResponse.ok(monitorService.getTrafficStats());
    }

    /**
     * 获取客户端连接统计
     *
     * <p>从 EMQX Broker API 获取在线客户端数量、连接数等统计数据。
     *
     * @return 客户端统计键值对
     */
    @GetMapping("/client-stats")
    public ApiResponse<Map<String, Object>> getClientStats() {
        return ApiResponse.ok(monitorService.getClientStats());
    }

    /**
     * 获取主题统计
     *
     * <p>从 EMQX Broker API 获取主题数量、订阅数等统计数据。
     *
     * @return 主题统计键值对
     */
    @GetMapping("/topic-stats")
    public ApiResponse<Map<String, Object>> getTopicStats() {
        return ApiResponse.ok(monitorService.getTopicStats());
    }

    /**
     * 获取本服务 MQTT 连接状态
     *
     * <p>返回后端服务与 EMQX Broker 的 MQTT 连接状态及当前使用的协议。
     *
     * @return 包含 connected（是否已连接）和 protocol（当前协议）的键值对
     */
    @GetMapping("/connection-status")
    public ApiResponse<Map<String, Object>> getConnectionStatus() {
        return ApiResponse.ok(Map.of(
                "connected", mqttClientService.isConnected(),
                "protocol", mqttClientService.getActiveProtocol()
        ));
    }
}
