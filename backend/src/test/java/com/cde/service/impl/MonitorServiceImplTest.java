package com.cde.service.impl;

import com.cde.mqtt.EmqxApiClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MonitorServiceImplTest {

    @Mock
    private EmqxApiClient emqxApiClient;

    @InjectMocks
    private MonitorServiceImpl monitorService;

    @Test
    void pollMetricsCachesStatsAndCalculatesDeltas() {
        when(emqxApiClient.fetchStats())
                .thenReturn(Map.of("live_connections.count", 3))
                .thenReturn(Map.of("connections.count", 4));
        when(emqxApiClient.fetchMetrics())
                .thenReturn(Map.of("messages.received", 10, "messages.sent", 20))
                .thenReturn(Map.of("messages.received", 15, "messages.sent", 18));
        when(emqxApiClient.fetchClients()).thenReturn(Map.of("data", "clients"));

        monitorService.pollMetrics();
        monitorService.pollMetrics();

        var stats = monitorService.getTrafficStats();
        assertThat(stats.get("onlineConnections")).isEqualTo(4);
        assertThat(stats.get("totalMessagesReceived")).isEqualTo(15);
        assertThat(stats.get("totalMessagesSent")).isEqualTo(18);
        assertThat(stats.get("history")).asList().hasSize(2);
        assertThat(monitorService.getClientStats()).containsEntry("data", "clients");
    }

    @Test
    void pollMetricsAndTopicStatsTolerateEmqxFailures() {
        when(emqxApiClient.fetchStats()).thenThrow(new RuntimeException("offline"));
        monitorService.pollMetrics();
        assertThat(monitorService.getTrafficStats()).containsEntry("onlineConnections", 0);

        when(emqxApiClient.fetchSubscriptions()).thenThrow(new RuntimeException("offline"));
        assertThat(monitorService.getTopicStats())
                .containsEntry("topics", java.util.List.of())
                .containsKey("error");
    }

    @Test
    void returnsTopicStatsAndJvmMetrics() {
        when(emqxApiClient.fetchSubscriptions()).thenReturn(Map.of("data", java.util.List.of("a")));

        assertThat(monitorService.getTopicStats()).containsEntry("data", java.util.List.of("a"));
        assertThat(monitorService.getSystemMetrics())
                .containsKeys("jvmTotalMemory", "jvmFreeMemory", "jvmUsedMemory", "availableProcessors", "connectionProtocol");
    }
}
