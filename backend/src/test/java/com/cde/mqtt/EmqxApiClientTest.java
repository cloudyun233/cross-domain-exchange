package com.cde.mqtt;

import com.cde.entity.SysTopicAcl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestOperations;

import java.util.Map;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EmqxApiClientTest {

    @Mock
    private RestOperations restTemplate;

    @Test
    void defaultConstructorCreatesRestTemplate() {
        assertThat(new EmqxApiClient()).isNotNull();
    }

    @Test
    void apiReadyReflectsNodesEndpoint() {
        EmqxApiClient client = configuredClient();
        when(restTemplate.exchange(anyString(), any(), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok("ok"))
                .thenThrow(new RuntimeException("offline"));

        assertThat(client.isApiReady()).isTrue();
        assertThat(client.isApiReady()).isFalse();
    }

    @Test
    @SuppressWarnings("unchecked")
    void pushAclRuleUsesGlobalOrEncodedUserEndpointAndHeaders() {
        EmqxApiClient client = configuredClient();
        when(restTemplate.exchange(anyString(), any(), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok("ok"));

        client.pushAclRule(acl("*", "cross/#"));
        client.pushAclRule(acl("user name/a", "cross/a"));

        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<HttpEntity> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate, org.mockito.Mockito.times(2)).exchange(
                urlCaptor.capture(), any(), entityCaptor.capture(), eq(String.class));

        assertThat(urlCaptor.getAllValues().get(0)).endsWith("/rules/all");
        assertThat(urlCaptor.getAllValues().get(1)).endsWith("/rules/users/user%20name%2Fa");
        assertThat(entityCaptor.getAllValues().get(1).getHeaders().getFirst("Authorization"))
                .startsWith("Basic ");
        assertThat((Map<String, Object>) entityCaptor.getAllValues().get(1).getBody())
                .containsEntry("username", "user name/a")
                .containsKey("rules");
    }

    @Test
    void pushAclRuleMapsConflictAndOtherFailures() {
        EmqxApiClient client = configuredClient();
        when(restTemplate.exchange(anyString(), any(), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new HttpClientErrorException(HttpStatus.CONFLICT))
                .thenThrow(new HttpClientErrorException(HttpStatus.BAD_REQUEST))
                .thenThrow(new RuntimeException("down"));

        assertThatThrownBy(() -> client.pushAclRule(acl("alice", "cross/a")))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("相同");
        assertThatThrownBy(() -> client.pushAclRule(acl("alice", "cross/a")))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to push ACL");
        assertThatThrownBy(() -> client.pushAclRule(acl("alice", "cross/a")))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to push ACL");
    }

    @Test
    void syncAllAclRulesPushesGlobalAndGroupedUserRules() {
        EmqxApiClient client = configuredClient();
        when(restTemplate.exchange(anyString(), any(), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok("ok"));

        boolean synced = client.syncAllAclRules(List.of(
                acl("*", "cross/#"),
                acl("alice/a", "cross/a"),
                acl("alice/a", "cross/b")
        ));

        assertThat(synced).isTrue();
        verify(restTemplate).exchange(
                eq("http://emqx/authorization/sources/built_in_database/rules/all"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(String.class)
        );
        verify(restTemplate).exchange(
                eq("http://emqx/authorization/sources/built_in_database/rules/users/alice%2Fa"),
                eq(HttpMethod.PUT),
                any(HttpEntity.class),
                eq(String.class)
        );
    }

    @Test
    void syncAllAclRulesReturnsFalseWhenCleanupFails() {
        EmqxApiClient client = configuredClient();
        SysTopicAcl acl = acl("producer_medical_swh", "cross_domain/medical/swh");
        doThrow(new RuntimeException("delete failed"))
                .when(restTemplate)
                .exchange(
                        eq("http://emqx/authorization/sources/built_in_database/rules"),
                        eq(HttpMethod.DELETE),
                        any(HttpEntity.class),
                        eq(String.class)
                );

        boolean synced = client.syncAllAclRules(List.of(acl));

        assertThat(synced).isFalse();
        verify(restTemplate, never()).exchange(
                eq("http://emqx/authorization/sources/built_in_database/rules/users/producer_medical_swh"),
                eq(HttpMethod.PUT),
                any(HttpEntity.class),
                eq(String.class)
        );
    }

    @Test
    void syncAllAclRulesReturnsFalseWhenEnableOrRulePushFails() {
        EmqxApiClient enableFailureClient = configuredClient();
        when(restTemplate.exchange(eq("http://emqx/authorization/sources/built_in_database"), eq(HttpMethod.PUT), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok("disabled"))
                .thenThrow(new RuntimeException("enable failed"));
        when(restTemplate.exchange(eq("http://emqx/authorization/sources/built_in_database/rules"), eq(HttpMethod.DELETE), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok("deleted"));

        assertThat(enableFailureClient.syncAllAclRules(List.of(acl("alice", "cross/a")))).isFalse();

        EmqxApiClient globalPushFailureClient = configuredClient();
        when(restTemplate.exchange(anyString(), any(), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok("ok"));
        doThrow(new RuntimeException("all push failed"))
                .when(restTemplate).exchange(
                        eq("http://emqx/authorization/sources/built_in_database/rules/all"),
                        eq(HttpMethod.POST),
                        any(HttpEntity.class),
                        eq(String.class)
                );

        assertThat(globalPushFailureClient.syncAllAclRules(List.of(acl("*", "cross/#")))).isFalse();

        EmqxApiClient userPushFailureClient = configuredClient();
        when(restTemplate.exchange(anyString(), any(), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok("ok"));
        doThrow(new RuntimeException("user push failed"))
                .when(restTemplate).exchange(
                        eq("http://emqx/authorization/sources/built_in_database/rules/users/alice"),
                        eq(HttpMethod.PUT),
                        any(HttpEntity.class),
                        eq(String.class)
                );

        assertThat(userPushFailureClient.syncAllAclRules(List.of(acl("alice", "cross/a")))).isFalse();
    }

    @Test
    void syncAllAclRulesHandlesEmptyRulesAndUnexpectedFailure() {
        EmqxApiClient client = configuredClient();
        when(restTemplate.exchange(anyString(), any(), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok("ok"));

        assertThat(client.syncAllAclRules(List.of())).isTrue();
        assertThat(client.syncAllAclRules(null)).isFalse();
    }

    @Test
    void fetchMethodsNormalizeBodiesAndReturnFallbacks() {
        EmqxApiClient client = configuredClient();
        when(restTemplate.exchange(eq("http://emqx/stats?aggregate=true"), eq(HttpMethod.GET), any(HttpEntity.class), eq(Object.class)))
                .thenReturn(ResponseEntity.ok(Map.of("connections.count", 2)));
        when(restTemplate.exchange(eq("http://emqx/metrics?aggregate=true"), eq(HttpMethod.GET), any(HttpEntity.class), eq(Object.class)))
                .thenReturn(ResponseEntity.ok(List.of(Map.of("messages.sent", 7))));
        when(restTemplate.exchange(eq("http://emqx/clients?limit=100"), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("data", List.of("client"))));
        when(restTemplate.exchange(eq("http://emqx/subscriptions?limit=200"), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenThrow(new RuntimeException("offline"));

        assertThat(client.fetchStats()).containsEntry("connections.count", 2);
        assertThat(client.fetchMetrics()).containsEntry("messages.sent", 7);
        assertThat(client.fetchClients()).containsEntry("data", List.of("client"));
        assertThat(client.fetchSubscriptions()).containsEntry("data", List.of());
    }

    @Test
    void fetchMethodsReturnFallbacksForFailuresAndNullBodies() {
        EmqxApiClient client = configuredClient();
        when(restTemplate.exchange(eq("http://emqx/stats?aggregate=true"), eq(HttpMethod.GET), any(HttpEntity.class), eq(Object.class)))
                .thenReturn(ResponseEntity.ok("unexpected"));
        when(restTemplate.exchange(eq("http://emqx/metrics?aggregate=true"), eq(HttpMethod.GET), any(HttpEntity.class), eq(Object.class)))
                .thenThrow(new RuntimeException("offline"));
        when(restTemplate.exchange(eq("http://emqx/clients?limit=100"), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(null))
                .thenThrow(new RuntimeException("offline"));
        when(restTemplate.exchange(eq("http://emqx/subscriptions?limit=200"), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("data", List.of("sub"))));

        assertThat(client.fetchStats()).containsEntry("connections.count", 0);
        assertThat(client.fetchMetrics()).containsEntry("messages.sent", 0);
        assertThat(client.fetchClients()).isEmpty();
        assertThat(client.fetchClients()).containsEntry("data", List.of());
        assertThat(client.fetchSubscriptions()).containsEntry("data", List.of("sub"));
    }

    private EmqxApiClient configuredClient() {
        EmqxApiClient client = new EmqxApiClient(restTemplate);
        ReflectionTestUtils.setField(client, "baseUrl", "http://emqx");
        ReflectionTestUtils.setField(client, "apiKey", "test-key");
        ReflectionTestUtils.setField(client, "secretKey", "test-secret");
        return client;
    }

    private SysTopicAcl acl(String username, String topic) {
        SysTopicAcl acl = new SysTopicAcl();
        acl.setUsername(username);
        acl.setTopicFilter(topic);
        acl.setAction("publish");
        acl.setAccessType("allow");
        return acl;
    }
}
