package com.cde.service.impl;

import com.cde.exception.BusinessException;
import com.cde.mqtt.MqttClientService;
import com.cde.service.AuditService;
import com.cde.service.DomainService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TopicServiceImplTest {

    @Mock
    private MqttClientService mqttClientService;

    @Mock
    private AuditService auditService;

    @Mock
    private DomainService domainService;

    @InjectMocks
    private TopicServiceImpl service;

    @Test
    void publishConnectsPublishesAndAuditsSuccess() {
        service.publishMsg("topic/a", "payload", 1, false, "alice", "token");

        verify(mqttClientService).connectForUser("alice", "token");
        verify(mqttClientService).publishForUser("alice", "topic/a", "payload", 1, false);
        verify(auditService).log(eq("alice"), eq("publish"), any(), eq("backend"));
    }

    @Test
    void publishAuditsForbiddenBusinessExceptionAsAclDeny() {
        doThrow(new BusinessException(HttpStatus.FORBIDDEN, "denied"))
                .when(mqttClientService).publishForUser("alice", "topic/a", "payload", 1, false);

        assertThatThrownBy(() -> service.publishMsg("topic/a", "payload", 1, false, "alice", "token"))
                .isInstanceOf(BusinessException.class);

        verify(auditService).log(eq("alice"), eq("acl_deny"), any(), eq("backend"));
    }

    @Test
    void publishAuditsOtherBusinessAndUnexpectedFailures() {
        doThrow(new BusinessException(HttpStatus.BAD_REQUEST, "bad"))
                .when(mqttClientService).publishForUser("alice", "topic/a", "payload", 1, false);
        assertThatThrownBy(() -> service.publishMsg("topic/a", "payload", 1, false, "alice", "token"))
                .isInstanceOf(BusinessException.class);
        verify(auditService).log(eq("alice"), eq("publish_fail"), any(), eq("backend"));

        doThrow(new RuntimeException("boom"))
                .when(mqttClientService).publishForUser("bob", "topic/b", "payload", 1, false);
        assertThatThrownBy(() -> service.publishMsg("topic/b", "payload", 1, false, "bob", "token"))
                .isInstanceOf(BusinessException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        verify(auditService).log(eq("bob"), eq("publish_fail"), any(), eq("backend"));
    }

    @Test
    void buildDomainTopicTreeDelegates() {
        service.buildDomainTopicTree();
        verify(domainService).buildDomainTree();
    }
}
