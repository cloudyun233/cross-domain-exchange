package com.crossdomain.exchange.controller;

import com.crossdomain.exchange.dto.ApiResponse;
import com.crossdomain.exchange.entity.Message;
import com.crossdomain.exchange.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 监控指标控制器
 */
@RestController
@RequestMapping("/api/metrics")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class MetricController {

    private final MessageRepository messageRepository;

    @GetMapping("/overview")
    public ApiResponse<Map<String, Object>> getOverview() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime oneHourAgo = now.minusHours(1);

        Long totalMessages = messageRepository.count();
        Long successCount = messageRepository.countSuccessMessagesBetween(oneHourAgo, now);
        Long failureCount = messageRepository.countFailureMessagesBetween(oneHourAgo, now);
        Double avgLatency = messageRepository.avgLatencyBetween(oneHourAgo, now);

        Map<String, Object> overview = new HashMap<>();
        overview.put("totalMessages", totalMessages);
        overview.put("successCount", successCount);
        overview.put("failureCount", failureCount);
        overview.put("successRate", totalMessages > 0 ? (successCount.doubleValue() / totalMessages.doubleValue() * 100) : 0);
        overview.put("avgLatencyMs", avgLatency != null ? avgLatency : 0);

        return ApiResponse.success(overview);
    }

    @GetMapping("/messages")
    public ApiResponse<List<Message>> getRecentMessages(@RequestParam(defaultValue = "50") int limit) {
        List<Message> messages = messageRepository.findAll().stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .limit(limit)
                .toList();
        return ApiResponse.success(messages);
    }

    @GetMapping("/domains")
    public ApiResponse<List<Map<String, Object>>> getDomainMetrics() {
        String[] domains = {"domain-1", "domain-2", "domain-3", "domain-4"};
        List<Map<String, Object>> domainMetrics = new java.util.ArrayList<>();

        for (String domain : domains) {
            List<Message> domainMessages = messageRepository.findBySourceDomain(domain);
            long success = domainMessages.stream().filter(Message::getSuccess).count();
            long failure = domainMessages.size() - success;

            Map<String, Object> metric = new HashMap<>();
            metric.put("domain", domain);
            metric.put("totalMessages", domainMessages.size());
            metric.put("successCount", success);
            metric.put("failureCount", failure);
            domainMetrics.add(metric);
        }

        return ApiResponse.success(domainMetrics);
    }
}
