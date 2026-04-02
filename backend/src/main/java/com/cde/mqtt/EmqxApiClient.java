package com.cde.mqtt;

import com.cde.entity.SysTopicAcl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * EMQX HTTP API client.
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
    private volatile String apiToken;

    public boolean isApiReady() {
        try {
            getApiToken(false);
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
            rule.put("username", acl.getClientId());
            rule.put("rules", List.of(Map.of(
                    "topic", acl.getTopicFilter(),
                    "action", acl.getAction(),
                    "permission", acl.getAccessType()
            )));

            exchange(url, HttpMethod.POST, List.of(rule), String.class);
            log.info("ACL pushed to EMQX: client={}, topic={}", acl.getClientId(), acl.getTopicFilter());
        } catch (Exception e) {
            log.warn("Failed to push ACL to EMQX: {}", e.getMessage());
        }
    }

    public void syncAllAclRules(List<SysTopicAcl> rules) {
        try {
            String url = baseUrl + "/authorization/sources/built_in_database/rules/users";
            try {
                exchange(url, HttpMethod.DELETE, null, String.class);
            } catch (Exception e) {
                log.debug("Ignoring EMQX ACL cleanup failure before full sync: {}", e.getMessage());
            }

            Map<String, List<Map<String, Object>>> grouped = new HashMap<>();
            for (SysTopicAcl acl : rules) {
                grouped.computeIfAbsent(acl.getClientId(), key -> new ArrayList<>())
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
                exchange(url, HttpMethod.POST, body, String.class);
            }
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
        try {
            return doExchange(url, method, body, responseType, false);
        } catch (HttpClientErrorException.Unauthorized e) {
            return doExchange(url, method, body, responseType, true);
        }
    }

    private <T> ResponseEntity<T> doExchange(
            String url,
            HttpMethod method,
            Object body,
            Class<T> responseType,
            boolean forceRefreshToken
    ) {
        HttpEntity<?> entity = body == null
                ? new HttpEntity<>(createHeaders(forceRefreshToken))
                : new HttpEntity<>(body, createHeaders(forceRefreshToken));
        return restTemplate.exchange(url, method, entity, responseType);
    }

    private HttpHeaders createHeaders(boolean forceRefreshToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(getApiToken(forceRefreshToken));
        return headers;
    }

    @SuppressWarnings("unchecked")
    private synchronized String getApiToken(boolean forceRefreshToken) {
        if (!forceRefreshToken && StringUtils.hasText(apiToken)) {
            return apiToken;
        }

        Map<String, String> request = Map.of(
                "username", username,
                "password", password
        );
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl + "/login",
                HttpMethod.POST,
                new HttpEntity<>(request, headers),
                Map.class
        );

        Object token = response.getBody() == null ? null : response.getBody().get("token");
        if (!(token instanceof String tokenValue) || !StringUtils.hasText(tokenValue)) {
            throw new IllegalStateException("EMQX login did not return a token");
        }

        apiToken = tokenValue;
        return apiToken;
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
