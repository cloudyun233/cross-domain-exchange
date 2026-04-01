package com.cde.mqtt;

import com.cde.entity.SysTopicAcl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * EMQX HTTP API 客户端
 * 职责: ACL规则实时推送 + 监控数据采集
 */
@Slf4j
@Component
public class EmqxApiClient {

    @Value("${emqx.api.base-url:http://localhost:18083/api/v5}")
    private String baseUrl;
    @Value("${emqx.api.username:admin}")
    private String username;
    @Value("${emqx.api.password:public}")
    private String password;

    private final RestTemplate restTemplate = new RestTemplate();

    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth(username, password);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    // ==================== ACL推送 ====================

    /**
     * 推送单条ACL规则到EMQX
     */
    public void pushAclRule(SysTopicAcl acl) {
        try {
            String url = baseUrl + "/authorization/sources/built_in_database/rules/users";
            Map<String, Object> rule = new HashMap<>();
            rule.put("username", acl.getClientId());
            rule.put("rules", List.of(Map.of(
                    "topic", acl.getTopicFilter(),
                    "action", acl.getAction(),
                    "permission", acl.getAccessType()
            )));

            HttpEntity<List<Map<String, Object>>> entity =
                    new HttpEntity<>(List.of(rule), createHeaders());
            restTemplate.postForEntity(url, entity, String.class);
            log.info("ACL规则推送成功: client={}, topic={}", acl.getClientId(), acl.getTopicFilter());
        } catch (Exception e) {
            log.warn("ACL推送到EMQX失败(Broker可能未启动): {}", e.getMessage());
        }
    }

    /**
     * 全量同步ACL规则到EMQX
     */
    public void syncAllAclRules(List<SysTopicAcl> rules) {
        try {
            // 先清空现有规则
            String deleteUrl = baseUrl + "/authorization/sources/built_in_database/rules/users";
            try {
                restTemplate.exchange(deleteUrl, HttpMethod.DELETE,
                        new HttpEntity<>(createHeaders()), String.class);
            } catch (Exception e) { /* 忽略清空失败 */ }

            // 按clientId分组推送
            Map<String, List<Map<String, Object>>> grouped = new HashMap<>();
            for (SysTopicAcl acl : rules) {
                grouped.computeIfAbsent(acl.getClientId(), k -> new ArrayList<>())
                        .add(Map.of(
                                "topic", acl.getTopicFilter(),
                                "action", acl.getAction(),
                                "permission", acl.getAccessType()
                        ));
            }

            List<Map<String, Object>> body = new ArrayList<>();
            grouped.forEach((clientId, aclRules) -> {
                Map<String, Object> entry = new HashMap<>();
                entry.put("username", clientId);
                entry.put("rules", aclRules);
                body.add(entry);
            });

            if (!body.isEmpty()) {
                String postUrl = baseUrl + "/authorization/sources/built_in_database/rules/users";
                HttpEntity<List<Map<String, Object>>> entity = new HttpEntity<>(body, createHeaders());
                restTemplate.postForEntity(postUrl, entity, String.class);
            }
            log.info("ACL全量同步成功, 共{}条规则", rules.size());
        } catch (Exception e) {
            log.warn("ACL全量同步到EMQX失败: {}", e.getMessage());
        }
    }

    // ==================== 监控采集 ====================

    @SuppressWarnings("unchecked")
    public Map<String, Object> fetchStats() {
        try {
            HttpEntity<?> entity = new HttpEntity<>(createHeaders());
            ResponseEntity<List> resp = restTemplate.exchange(
                    baseUrl + "/stats", HttpMethod.GET, entity, List.class);
            if (resp.getBody() != null && !resp.getBody().isEmpty()) {
                return (Map<String, Object>) resp.getBody().get(0);
            }
        } catch (Exception e) {
            log.debug("获取EMQX统计失败: {}", e.getMessage());
        }
        return defaultStats();
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> fetchClients() {
        try {
            HttpEntity<?> entity = new HttpEntity<>(createHeaders());
            ResponseEntity<Map> resp = restTemplate.exchange(
                    baseUrl + "/clients?limit=100", HttpMethod.GET, entity, Map.class);
            return resp.getBody() != null ? resp.getBody() : Map.of();
        } catch (Exception e) {
            log.debug("获取EMQX客户端列表失败: {}", e.getMessage());
        }
        return Map.of("data", List.of());
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> fetchSubscriptions() {
        try {
            HttpEntity<?> entity = new HttpEntity<>(createHeaders());
            ResponseEntity<Map> resp = restTemplate.exchange(
                    baseUrl + "/subscriptions?limit=200", HttpMethod.GET, entity, Map.class);
            return resp.getBody() != null ? resp.getBody() : Map.of();
        } catch (Exception e) {
            log.debug("获取EMQX订阅列表失败: {}", e.getMessage());
        }
        return Map.of("data", List.of());
    }

    private Map<String, Object> defaultStats() {
        Map<String, Object> m = new HashMap<>();
        m.put("connections.count", 0);
        m.put("messages.received", 0);
        m.put("messages.sent", 0);
        m.put("topics.count", 0);
        m.put("subscriptions.count", 0);
        return m;
    }
}
