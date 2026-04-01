package com.cde.service.impl;

import com.cde.mqtt.EmqxApiClient;
import com.cde.service.MonitorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * 监控服务实现 (论文3.3.4: 实时可视化监控)
 * 定时从EMQX HTTP API采集数据
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MonitorServiceImpl implements MonitorService {

    private final EmqxApiClient emqxApiClient;

    // 缓存最近的监控数据
    private volatile Map<String, Object> cachedStats = new HashMap<>();
    private volatile Map<String, Object> cachedClients = new HashMap<>();
    private final Deque<Map<String, Object>> trafficHistory = new ConcurrentLinkedDeque<>();

    /**
     * 定时采集EMQX指标 (每5秒)
     */
    @Scheduled(fixedDelayString = "${emqx.monitor.poll-interval-ms:5000}")
    public void pollMetrics() {
        try {
            Map<String, Object> stats = emqxApiClient.fetchStats();
            Map<String, Object> clients = emqxApiClient.fetchClients();

            cachedStats = stats;
            cachedClients = clients;

            // 记录流量历史 (保留最近60条=5分钟)
            Map<String, Object> snapshot = new HashMap<>();
            snapshot.put("time", System.currentTimeMillis());
            snapshot.put("messagesIn", stats.getOrDefault("messages.received", 0));
            snapshot.put("messagesOut", stats.getOrDefault("messages.sent", 0));
            snapshot.put("connections", stats.getOrDefault("connections.count", 0));
            trafficHistory.addLast(snapshot);
            while (trafficHistory.size() > 60) {
                trafficHistory.pollFirst();
            }
        } catch (Exception e) {
            log.debug("EMQX指标采集失败(Broker可能未启动): {}", e.getMessage());
        }
    }

    @Override
    public Map<String, Object> getTrafficStats() {
        Map<String, Object> result = new HashMap<>(cachedStats);
        result.put("history", new ArrayList<>(trafficHistory));
        return result;
    }

    @Override
    public Map<String, Object> getClientStats() {
        return cachedClients;
    }

    @Override
    public Map<String, Object> getTopicStats() {
        try {
            return emqxApiClient.fetchSubscriptions();
        } catch (Exception e) {
            return Map.of("error", "EMQX未连接", "topics", List.of());
        }
    }

    @Override
    public Map<String, Object> getSystemMetrics() {
        Map<String, Object> metrics = new HashMap<>();
        Runtime rt = Runtime.getRuntime();
        metrics.put("jvmTotalMemory", rt.totalMemory() / 1024 / 1024);
        metrics.put("jvmFreeMemory", rt.freeMemory() / 1024 / 1024);
        metrics.put("jvmUsedMemory", (rt.totalMemory() - rt.freeMemory()) / 1024 / 1024);
        metrics.put("availableProcessors", rt.availableProcessors());
        metrics.put("connectionProtocol", cachedStats.getOrDefault("connectionProtocol", "TCP"));
        return metrics;
    }
}
