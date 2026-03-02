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
 * 消息实体类
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "messages")
@EntityListeners(AuditingEntityListener.class)
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "message_id", unique = true, length = 100)
    private String messageId;

    @Column(name = "topic_name", nullable = false, length = 255)
    private String topicName;

    @Column(name = "source_domain", nullable = false, length = 50)
    private String sourceDomain;

    @Column(name = "target_domains", length = 500)
    private String targetDomains;

    @Column(columnDefinition = "TEXT")
    private String payload;

    @Column(nullable = false)
    private Integer qos;

    @Column(nullable = false)
    private Boolean retained;

    @Column(name = "protocol_type", length = 20)
    private String protocolType;

    @Column(name = "send_timestamp")
    private Long sendTimestamp;

    @Column(name = "receive_timestamp")
    private Long receiveTimestamp;

    @Column(name = "latency_ms")
    private Long latencyMs;

    @Column(nullable = false)
    private Boolean success;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
