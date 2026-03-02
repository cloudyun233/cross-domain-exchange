package com.crossdomain.exchange.repository;

import com.crossdomain.exchange.entity.Metric;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 监控指标Repository接口
 */
@Repository
public interface MetricRepository extends JpaRepository<Metric, Long> {

    List<Metric> findByMetricType(String metricType);

    List<Metric> findByProtocolType(String protocolType);

    List<Metric> findByDomainName(String domainName);

    Page<Metric> findByMetricTypeOrderByCreatedAtDesc(String metricType, Pageable pageable);

    @Query("SELECT m FROM Metric m WHERE m.createdAt BETWEEN :start AND :end ORDER BY m.createdAt DESC")
    List<Metric> findByCreatedAtBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT m FROM Metric m WHERE m.metricType = :metricType AND m.createdAt BETWEEN :start AND :end ORDER BY m.createdAt DESC")
    List<Metric> findByMetricTypeAndCreatedAtBetween(
            @Param("metricType") String metricType,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);
}
