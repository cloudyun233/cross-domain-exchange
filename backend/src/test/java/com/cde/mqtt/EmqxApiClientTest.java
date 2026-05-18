package com.cde.mqtt;

import com.cde.entity.SysTopicAcl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestOperations;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EmqxApiClientTest {

    @Mock
    private RestOperations restTemplate;

    @Test
    void syncAllAclRulesReturnsFalseWhenCleanupFails() {
        EmqxApiClient client = new EmqxApiClient(restTemplate);
        ReflectionTestUtils.setField(client, "baseUrl", "http://emqx");
        ReflectionTestUtils.setField(client, "apiKey", "test-key");
        ReflectionTestUtils.setField(client, "secretKey", "test-secret");
        SysTopicAcl acl = new SysTopicAcl();
        acl.setUsername("producer_medical_swh");
        acl.setTopicFilter("cross_domain/medical/swh");
        acl.setAction("publish");
        acl.setAccessType("allow");
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
}
