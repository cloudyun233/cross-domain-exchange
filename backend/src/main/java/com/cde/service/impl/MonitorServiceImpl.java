package com.cde.service.impl;

import com.cde.mqtt.EmqxApiClient;
import com.cde.service.MonitorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * 监控服务实现
 *
 * <p>通过定时轮询（{@code @Scheduled}，间隔由emqx.monitor.poll-interval-ms配置，默认5s）
 * 从EMQX Management API拉取指标数据。流量历史采用滑动窗口策略，保留最近60个快照。
 * 消息速率通过相邻两次轮询的累计值差值（delta）计算。</p>
 *
 * <p>线程安全：cachedStats/cachedMetrics/cachedClients使用volatile保证可见性；
 * trafficHistory使用ConcurrentLinkedDeque保证并发安全。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MonitorServiceImpl implements MonitorService {

    private final EmqxApiClient emqxApiClient;

    private volatile Map<String, Object> cachedStats = new HashMap<>();
    private volatile Map<String, Object> cachedMetrics = new HashMap<>();
    private volatile Map<String, Object> cachedClients = new HashMap<>();
    private final Deque<Map<String, Object>> trafficHistory = new ConcurrentLinkedDeque<>();

    private volatile long lastMessagesReceived = 0;
    private volatile long lastMessagesSent = 0;

    /**
     * 定时轮询EMQX指标并计算增量
     *
     * <p>delta计算逻辑：当前累计值 - 上次累计值 = 本轮新增消息数。
     * 首次轮询时lastMessagesReceived为0，delta记为0以避免冷启动误报。
     * 滑动窗口超过60个快照时淘汰最早的数据。</p>
     */
    @Scheduled(fixedDelayString = "${emqx.monitor.poll-interval-ms:5000}")
    public void pollMetrics() {
        try {
            Map<String, Object> stats = emqxApiClient.fetchStats();
            Map<String, Object> metrics = emqxApiClient.fetchMetrics();
            Map<String, Object> clients = emqxApiClient.fetchClients();

            cachedStats = stats;
            cachedMetrics = metrics;
            cachedClients = clients;

            long currentMessagesReceived = numberValue(metrics.get("messages.received"));
            long currentMessagesSent = numberValue(metrics.get("messages.sent"));
            long onlineConnections = numberValue(stats.getOrDefault("live_connections.count",
                    stats.getOrDefault("connections.count", 0)));

            long deltaReceived = lastMessagesReceived == 0 ? 0 : Math.max(0, currentMessagesReceived - lastMessagesReceived);
            long deltaSent = lastMessagesSent == 0 ? 0 : Math.max(0, currentMessagesSent - lastMessagesSent);

            lastMessagesReceived = currentMessagesReceived;
            lastMessagesSent = currentMessagesSent;

            Map<String, Object> snapshot = new HashMap<>();
            snapshot.put("time", System.currentTimeMillis());
            snapshot.put("messagesIn", deltaReceived);
            snapshot.put("messagesOut", deltaSent);
            snapshot.put("onlineConnections", onlineConnections);
            snapshot.put("totalMessagesReceived", currentMessagesReceived);
            snapshot.put("totalMessagesSent", currentMessagesSent);
            trafficHistory.addLast(snapshot);
            while (trafficHistory.size() > 60) {
                trafficHistory.pollFirst();
            }
        } catch (Exception e) {
            log.debug("Failed to poll EMQX metrics: {}", e.getMessage());
        }
    }

    @Override
    public Map<String, Object> getTrafficStats() {
        Map<String, Object> result = new HashMap<>(cachedStats);
        result.putAll(cachedMetrics);
        result.put("onlineConnections", cachedStats.getOrDefault("live_connections.count",
                cachedStats.getOrDefault("connections.count", 0)));
        result.put("totalMessagesReceived", cachedMetrics.getOrDefault("messages.received", 0));
        result.put("totalMessagesSent", cachedMetrics.getOrDefault("messages.sent", 0));
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

    private long numberValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return 0L;
    }
}
