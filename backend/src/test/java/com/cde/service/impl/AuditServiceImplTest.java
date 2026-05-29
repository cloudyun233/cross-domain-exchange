package com.cde.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cde.entity.SysAuditLog;
import com.cde.mapper.SysAuditLogMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditServiceImplTest {

    @Mock
    private SysAuditLogMapper auditLogMapper;

    @InjectMocks
    private AuditServiceImpl auditService;

    @Test
    void logPersistsDefaultIpAndWarningActions() {
        ArgumentCaptor<SysAuditLog> captor = ArgumentCaptor.forClass(SysAuditLog.class);

        auditService.log("client-a", "json_schema_validate_fail", "detail", null);

        verify(auditLogMapper).insert(captor.capture());
        assertThat(captor.getValue().getClientId()).isEqualTo("client-a");
        assertThat(captor.getValue().getActionType()).isEqualTo("json_schema_validate_fail");
        assertThat(captor.getValue().getIpAddress()).isEqualTo("0.0.0.0");
        assertThat(captor.getValue().getActionTime()).isNotNull();
    }

    @Test
    void recordFromWebhookMapsKnownEventsAndParsesPeerNames() {
        auditService.recordFromWebhook(Map.of("event", "client.connected", "clientid", "c1", "peername", "[2001:db8::1]:1883", "proto_name", "MQTT"));
        auditService.recordFromWebhook(Map.of("event", "client.disconnected", "clientid", "c1", "peername", "10.0.0.1:1883", "reason", "normal"));
        auditService.recordFromWebhook(Map.of("event", "message.publish", "clientid", "c1", "topic", "t", "qos", 1));
        auditService.recordFromWebhook(Map.of("event", "client.authorize", "clientid", "c1", "result", "deny", "action", "publish", "topic", "t"));
        auditService.recordFromWebhook(Map.of("event", "client.authorize", "clientid", "c1", "result", "allow", "action", "subscribe", "topic", "t"));
        auditService.recordFromWebhook(Map.of("event", "session.subscribed", "clientid", "c1", "topic", "t", "qos", 1));
        auditService.recordFromWebhook(Map.of("event", "message.delivered", "clientid", "c1", "topic", "t", "qos", 1));

        ArgumentCaptor<SysAuditLog> captor = ArgumentCaptor.forClass(SysAuditLog.class);
        verify(auditLogMapper, org.mockito.Mockito.times(7)).insert(captor.capture());
        assertThat(captor.getAllValues()).extracting(SysAuditLog::getActionType)
                .containsExactly("connect", "disconnect", "publish", "acl_deny", "acl_allow", "subscribe", "deliver");
        assertThat(captor.getAllValues().get(0).getIpAddress()).isEqualTo("2001:db8::1");
        assertThat(captor.getAllValues().get(1).getIpAddress()).isEqualTo("10.0.0.1");
    }

    @Test
    void recordFromWebhookIgnoresUnknownEvents() {
        auditService.recordFromWebhook(Map.of("event", "unknown"));

        verify(auditLogMapper, never()).insert(any());
    }

    @Test
    void queryLogsClampsPageAndSize() {
        when(auditLogMapper.selectPage(any(), any())).thenReturn(new Page<>());

        auditService.queryLogs(-5, 10_000, "client-a", "acl_deny");

        ArgumentCaptor<Page<SysAuditLog>> captor = ArgumentCaptor.forClass(Page.class);
        verify(auditLogMapper).selectPage(captor.capture(), any());
        assertThat(captor.getValue().getCurrent()).isEqualTo(1);
        assertThat(captor.getValue().getSize()).isEqualTo(500);
    }

    @Test
    void exportLogsAsPdfCreatesPdfWithAuditRecords() {
        SysAuditLog log = new SysAuditLog();
        log.setId(1L);
        log.setClientId("client-a");
        log.setActionType("acl_deny");
        log.setDetail("非法操作拦截");
        log.setIpAddress("127.0.0.1");
        log.setActionTime(LocalDateTime.of(2026, 4, 25, 10, 30));
        Page<SysAuditLog> page = new Page<>(1, 500);
        page.setRecords(List.of(log));
        when(auditLogMapper.selectPage(any(), any())).thenReturn(page);

        byte[] pdf = auditService.exportLogsAsPdf("client-a", "acl_deny");

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 5, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
    }
}
