package com.crossdomain.exchange.repository;

import com.crossdomain.exchange.entity.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 消息Repository接口
 */
@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {

    Optional<Message> findByMessageId(String messageId);

    List<Message> findByTopicName(String topicName);

    List<Message> findBySourceDomain(String sourceDomain);

    List<Message> findByProtocolType(String protocolType);

    Page<Message> findByTopicNameOrderByCreatedAtDesc(String topicName, Pageable pageable);

    @Query("SELECT m FROM Message m WHERE m.createdAt BETWEEN :start AND :end")
    List<Message> findByCreatedAtBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT COUNT(m) FROM Message m WHERE m.success = true AND m.createdAt BETWEEN :start AND :end")
    Long countSuccessMessagesBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT COUNT(m) FROM Message m WHERE m.success = false AND m.createdAt BETWEEN :start AND :end")
    Long countFailureMessagesBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT AVG(m.latencyMs) FROM Message m WHERE m.success = true AND m.createdAt BETWEEN :start AND :end")
    Double avgLatencyBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
