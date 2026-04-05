package com.cde.mqtt;

import com.cde.entity.SysTopicAcl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Base64;

/**
 * EMQX HTTP API client.
 */
@Slf4j
@Component
public class EmqxApiClient {

    @Value("${emqx.api.base-url:http://localhost:18083/api/v5}")
    private String baseUrl;

    @Value("${emqx.api.api-key}")
    private String apiKey;

    @Value("${emqx.api.secret-key}")
    private String secretKey;

    private final RestTemplate restTemplate;

    public EmqxApiClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(3000);
        this.restTemplate = new RestTemplate(factory);
    }

    public boolean isApiReady() {
        try {
            exchange(baseUrl + "/nodes", HttpMethod.GET, null, String.class);
            return true;
        } catch (Exception e) {
            log.debug("EMQX API is not ready yet: {}", e.getMessage());
            return false;
        }
    }

    public void pushAclRule(SysTopicAcl acl) {
        try {
            String url = baseUrl + "/authorization/sources/built_in_database/rules/users";
            Map<String, Object> rule = new HashMap<>();
            rule.put("username", acl.getUsername());
            rule.put("rules", List.of(Map.of(
                    "topic", acl.getTopicFilter(),
                    "action", acl.getAction(),
                    "permission", acl.getAccessType()
            )));

            exchange(url, HttpMethod.POST, List.of(rule), String.class);
            log.info("ACL pushed to EMQX: username={}, topic={}", acl.getUsername(), acl.getTopicFilter());
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().value() == 409) {
                throw new RuntimeException("该用户已有相同规则");
            }
            log.warn("Failed to push ACL to EMQX: {}", e.getMessage());
            throw new RuntimeException("Failed to push ACL to EMQX: " + e.getMessage());
        } catch (Exception e) {
            log.warn("Failed to push ACL to EMQX: {}", e.getMessage());
            throw new RuntimeException("Failed to push ACL to EMQX: " + e.getMessage());
        }
    }

    public void syncAllAclRules(List<SysTopicAcl> rules) {
        try {
            String sourceUrl = baseUrl + "/authorization/sources/built_in_database";
            String rulesUrl = baseUrl + "/authorization/sources/built_in_database/rules";
            String userUrlTemplate = baseUrl + "/authorization/sources/built_in_database/rules/users/{username}";

            try {
                Map<String, Object> disableBody = Map.of(
                        "type", "built_in_database",
                        "enable", false
                );
                exchange(sourceUrl, HttpMethod.PUT, disableBody, String.class);
                log.debug("Disabled built_in_database authorization source");
            } catch (Exception e) {
                log.debug("Ignoring failure to disable built_in_database: {}", e.getMessage());
            }

            try {
                exchange(rulesUrl, HttpMethod.DELETE, null, String.class);
                log.debug("Cleared all ACL rules from built_in_database");
            } catch (Exception e) {
                log.debug("Ignoring EMQX ACL cleanup failure: {}", e.getMessage());
            }

            try {
                Map<String, Object> enableBody = Map.of(
                        "type", "built_in_database",
                        "enable", true
                );
                exchange(sourceUrl, HttpMethod.PUT, enableBody, String.class);
                log.debug("Enabled built_in_database authorization source");
            } catch (Exception e) {
                log.debug("Ignoring failure to enable built_in_database: {}", e.getMessage());
            }

            Map<String, List<Map<String, Object>>> grouped = new HashMap<>();
            for (SysTopicAcl acl : rules) {
                grouped.computeIfAbsent(acl.getUsername(), key -> new ArrayList<>())
                        .add(Map.of(
                                "topic", acl.getTopicFilter(),
                                "action", acl.getAction(),
                                "permission", acl.getAccessType()
                        ));
            }

            grouped.forEach((username, aclRules) -> {
                try {
                    String url = userUrlTemplate.replace("{username}", username);
                    Map<String, Object> body = new HashMap<>();
                    body.put("username", username);
                    body.put("rules", aclRules);
                    exchange(url, HttpMethod.PUT, body, String.class);
                    log.debug("Pushed ACL rules to EMQX for user: {}", username);
                } catch (Exception e) {
                    log.warn("Failed to push ACL rules for user {}: {}", username, e.getMessage());
                }
            });

            log.info("ACL full sync to EMQX completed, rules={}", rules.size());
        } catch (Exception e) {
            log.warn("Failed to sync ACL rules to EMQX: {}", e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> fetchStats() {
        try {
            ResponseEntity<List> response = exchange(baseUrl + "/stats", HttpMethod.GET, null, List.class);
            if (response.getBody() != null && !response.getBody().isEmpty()) {
                return (Map<String, Object>) response.getBody().get(0);
            }
        } catch (Exception e) {
            log.debug("Failed to fetch EMQX stats: {}", e.getMessage());
        }
        return defaultStats();
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> fetchClients() {
        try {
            ResponseEntity<Map> response = exchange(baseUrl + "/clients?limit=100", HttpMethod.GET, null, Map.class);
            return response.getBody() != null ? response.getBody() : Map.of();
        } catch (Exception e) {
            log.debug("Failed to fetch EMQX clients: {}", e.getMessage());
        }
        return Map.of("data", List.of());
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> fetchSubscriptions() {
        try {
            ResponseEntity<Map> response = exchange(baseUrl + "/subscriptions?limit=200", HttpMethod.GET, null, Map.class);
            return response.getBody() != null ? response.getBody() : Map.of();
        } catch (Exception e) {
            log.debug("Failed to fetch EMQX subscriptions: {}", e.getMessage());
        }
        return Map.of("data", List.of());
    }

    private <T> ResponseEntity<T> exchange(String url, HttpMethod method, Object body, Class<T> responseType) {
        HttpEntity<?> entity = body == null
                ? new HttpEntity<>(createHeaders())
                : new HttpEntity<>(body, createHeaders());
        return restTemplate.exchange(url, method, entity, responseType);
    }

    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String auth = apiKey + ":" + secretKey;
        String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());
        headers.set("Authorization", "Basic " + encodedAuth);
        return headers;
    }

    private Map<String, Object> defaultStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("connections.count", 0);
        stats.put("messages.received", 0);
        stats.put("messages.sent", 0);
        stats.put("topics.count", 0);
        stats.put("subscriptions.count", 0);
        return stats;
    }
}
