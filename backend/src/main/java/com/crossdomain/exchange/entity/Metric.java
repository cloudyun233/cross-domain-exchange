package com.crossdomain.exchange.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 监控指标实体类
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "metrics")
@EntityListeners(AuditingEntityListener.class)
public class Metric {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "metric_type", nullable = false, length = 50)
    private String metricType;

    @Column(name = "protocol_type", length = 20)
    private String protocolType;

    @Column(name = "domain_name", length = 50)
    private String domainName;

    @Column(name = "topic_name", length = 255)
    private String topicName;

    @Column(name = "message_count")
    private Long messageCount;

    @Column(name = "success_count")
    private Long successCount;

    @Column(name = "failure_count")
    private Long failureCount;

    @Column(name = "avg_latency_ms")
    private Double avgLatencyMs;

    @Column(name = "min_latency_ms")
    private Long minLatencyMs;

    @Column(name = "max_latency_ms")
    private Long maxLatencyMs;

    @Column(name = "throughput_per_second")
    private Double throughputPerSecond;

    @Column(name = "time_window_start")
    private LocalDateTime timeWindowStart;

    @Column(name = "time_window_end")
    private LocalDateTime timeWindowEnd;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
