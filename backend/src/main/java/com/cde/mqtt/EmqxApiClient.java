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
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * EMQX HTTP API 客户端 —— 用于管理操作（ACL 同步、指标轮询、客户端/订阅查询）。
 *
 * <h3>核心职责</h3>
 * <ul>
 *   <li><b>ACL 规则同步</b>：将数据库中的 ACL 规则推送到 EMQX 内置数据库授权源，
 *       支持单条推送和全量同步</li>
 *   <li><b>运行指标采集</b>：定时轮询 EMQX 的 stats/metrics 端点，获取连接数、主题数、消息收发量等</li>
 *   <li><b>客户端与订阅查询</b>：查询当前在线客户端列表和订阅关系</li>
 * </ul>
 *
 * <h3>认证方式</h3>
 * <p>使用 HTTP Basic Auth，以 API Key / Secret Key 编码后放入 Authorization 请求头。
 * API Key 和 Secret Key 通过 Spring 配置 {@code emqx.api.api-key} / {@code emqx.api.secret-key} 注入。</p>
 *
 * <h3>超时配置</h3>
 * <p>连接超时和读取超时均为 3 秒，避免 EMQX 不可用时阻塞业务线程。</p>
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

    /**
     * 推送单条 ACL 规则到 EMQX。
     *
     * <p>根据规则所属用户选择不同的 API 路径：</p>
     * <ul>
     *   <li>username 为 {@code "*"}（全局规则）→ POST {@code /rules/all}，
     *       批量添加到"所有用户"规则列表</li>
     *   <li>username 为具体用户 → PUT {@code /rules/users/{username}}，
     *       设置该用户的专属规则（整体替换）</li>
     * </ul>
     *
     * <p>409 Conflict 处理：EMQX 对重复规则返回 HTTP 409，此处映射为
     * "该用户已有相同规则"的运行时异常，由上层捕获并提示用户。</p>
     *
     * @param acl 待推送的 ACL 规则实体
     * @throws RuntimeException 推送失败或规则重复时抛出
     */
    public void pushAclRule(SysTopicAcl acl) {
        try {
            Map<String, Object> rule = Map.of(
                    "topic", acl.getTopicFilter(),
                    "action", acl.getAction(),
                    "permission", acl.getAccessType()
            );

            if ("*".equals(acl.getUsername())) {
                String allUrl = baseUrl + "/authorization/sources/built_in_database/rules/all";
                exchange(allUrl, HttpMethod.POST, Map.of("rules", List.of(rule)), String.class);
            } else {
                String userUrl = baseUrl + "/authorization/sources/built_in_database/rules/users/" + acl.getUsername();
                Map<String, Object> body = new HashMap<>();
                body.put("username", acl.getUsername());
                body.put("rules", List.of(rule));
                exchange(userUrl, HttpMethod.PUT, body, String.class);
            }

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

    /**
     * 全量同步 ACL 规则到 EMQX 内置数据库授权源。
     *
     * <p>同步策略为"先清后推"，分四步执行：</p>
     * <ol>
     *   <li><b>禁用</b> built_in_database 授权源 —— EMQX API 在授权源启用状态下
     *       清空规则可能不生效，需先禁用</li>
     *   <li><b>清空</b>所有现有 ACL 规则 —— DELETE {@code /rules}</li>
     *   <li><b>重新启用</b>授权源 —— 恢复授权检查功能</li>
     *   <li><b>推送</b>新规则 —— 将规则分为全局规则({@code username="*"})和用户专属规则，
     *       全局规则 POST 到 {@code /rules/all}，用户规则 PUT 到 {@code /rules/users/{username}}</li>
     * </ol>
     *
     * <p>禁用/启用步骤的失败会被忽略（仅记录 debug 日志），因为授权源可能本就处于目标状态。
     * 单条规则推送失败不影响其余规则（仅记录 warn 日志）。</p>
     *
     * @param rules 待同步的 ACL 规则列表
     */
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

            List<Map<String, Object>> allRules = new ArrayList<>();
            Map<String, List<Map<String, Object>>> userRules = new HashMap<>();
            for (SysTopicAcl acl : rules) {
                Map<String, Object> rule = Map.of(
                        "topic", acl.getTopicFilter(),
                        "action", acl.getAction(),
                        "permission", acl.getAccessType()
                );
                if ("*".equals(acl.getUsername())) {
                    allRules.add(rule);
                } else {
                    userRules.computeIfAbsent(acl.getUsername(), key -> new ArrayList<>()).add(rule);
                }
            }

            if (!allRules.isEmpty()) {
                try {
                    String allUrl = baseUrl + "/authorization/sources/built_in_database/rules/all";
                    Map<String, Object> body = Map.of("rules", allRules);
                    exchange(allUrl, HttpMethod.POST, body, String.class);
                    log.debug("Pushed {} 'all' ACL rules to EMQX", allRules.size());
                } catch (Exception e) {
                    log.warn("Failed to push 'all' ACL rules: {}", e.getMessage());
                }
            }

            userRules.forEach((username, aclRules) -> {
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

    /**
     * 获取 EMQX 统计数据（连接数、主题数、订阅数等）。
     *
     * <p>调用 {@code GET /stats?aggregate=true} 端点，aggregate=true 表示聚合所有节点数据。
     * 返回包含 connections.count、live_connections.count、topics.count、subscriptions.count 等字段。
     * EMQX 不可达时返回 {@link #defaultStats()} 兜底值。</p>
     */
    public Map<String, Object> fetchStats() {
        try {
            ResponseEntity<Object> response = exchange(baseUrl + "/stats?aggregate=true", HttpMethod.GET, null, Object.class);
            return normalizeMetricBody(response.getBody(), defaultStats());
        } catch (Exception e) {
            log.debug("Failed to fetch EMQX stats: {}", e.getMessage());
        }
        return defaultStats();
    }

    /**
     * 获取 EMQX 运行指标（消息收发量等）。
     *
     * <p>调用 {@code GET /metrics?aggregate=true} 端点，返回 messages.received、messages.sent 等计数器。
     * EMQX 不可达时返回 {@link #defaultMetrics()} 兜底值。</p>
     */
    public Map<String, Object> fetchMetrics() {
        try {
            ResponseEntity<Object> response = exchange(baseUrl + "/metrics?aggregate=true", HttpMethod.GET, null, Object.class);
            return normalizeMetricBody(response.getBody(), defaultMetrics());
        } catch (Exception e) {
            log.debug("Failed to fetch EMQX metrics: {}", e.getMessage());
        }
        return defaultMetrics();
    }

    /**
     * 获取当前在线客户端列表。
     *
     * <p>调用 {@code GET /clients?limit=100} 端点，返回最近 100 个客户端的详细信息
     * （clientId、username、IP、连接时间等）。EMQX 不可达时返回空列表。</p>
     */
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

    /**
     * 获取当前订阅关系列表。
     *
     * <p>调用 {@code GET /subscriptions?limit=200} 端点，返回最近 200 条订阅关系
     * （clientId、topic、qos 等）。EMQX 不可达时返回空列表。</p>
     */
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
        String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
        headers.set("Authorization", "Basic " + encodedAuth);
        return headers;
    }

    /**
     * Stats 兜底值 —— EMQX 不可达时返回全零统计，避免前端展示异常。
     */
    private Map<String, Object> defaultStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("connections.count", 0);
        stats.put("live_connections.count", 0);
        stats.put("topics.count", 0);
        stats.put("subscriptions.count", 0);
        return stats;
    }

    /**
     * Metrics 兜底值 —— EMQX 不可达时返回全零指标，避免前端展示异常。
     */
    private Map<String, Object> defaultMetrics() {
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("messages.received", 0);
        metrics.put("messages.sent", 0);
        return metrics;
    }

    /**
     * 归一化 EMQX API 返回的指标数据。
     *
     * <p>EMQX 的 stats/metrics 端点响应格式因部署模式不同而不同：
     * <ul>
     *   <li>单节点部署 → 返回 {@code Map}（直接是键值对）</li>
     *   <li>集群聚合（aggregate=true）→ 也返回 {@code Map}</li>
     *   <li>某些版本/端点 → 返回 {@code List<Map>}（每个节点一条）</li>
     * </ul>
     * 此方法统一处理这两种格式：Map 直接返回，List 取第一个元素，其他情况使用兜底值。</p>
     *
     * @param body     EMQX API 原始响应体
     * @param fallback 兜底值（EMQX 不可达或响应格式异常时使用）
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> normalizeMetricBody(Object body, Map<String, Object> fallback) {
        if (body instanceof Map<?, ?> mapBody) {
            return new HashMap<>((Map<String, Object>) mapBody);
        }
        if (body instanceof List<?> listBody && !listBody.isEmpty() && listBody.get(0) instanceof Map<?, ?> first) {
            return new HashMap<>((Map<String, Object>) first);
        }
        return fallback;
    }
}
