package com.cde.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cde.dto.LoginRequest;
import com.cde.dto.LoginResponse;
import com.cde.entity.SysAuditLog;
import com.cde.entity.SysDomain;
import com.cde.entity.SysTopicAcl;
import com.cde.mqtt.EmqxApiClient;
import com.cde.mqtt.MqttClientService;
import com.cde.security.JwtUtil;
import com.cde.service.AclService;
import com.cde.service.AuditService;
import com.cde.service.DomainService;
import com.cde.service.MonitorService;
import com.cde.service.SubscribeService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ControllerUnitTest {

    @Test
    void authControllerDelegatesLoginAndCurrentUser() {
        var authService = mock(com.cde.service.AuthService.class);
        var jwtUtil = mock(JwtUtil.class);
        var controller = new AuthController(authService, jwtUtil);
        LoginRequest request = new LoginRequest();
        request.setUsername("alice");
        request.setPassword("pw");
        LoginResponse response = LoginResponse.builder().username("alice").token("token").build();
        when(authService.login(request)).thenReturn(response);
        when(jwtUtil.getUsernameFromToken("token")).thenReturn("alice");
        when(authService.getCurrentUserProfile("alice", "token")).thenReturn(response);

        assertThat(controller.login(request).getData()).isSameAs(response);
        assertThat(controller.getCurrentUser("Bearer token").getData()).isSameAs(response);
    }

    @Test
    void aclDomainMonitorStatusAndWebhookControllersDelegate() {
        AclService aclService = mock(AclService.class);
        AclController acl = new AclController(aclService);
        SysTopicAcl rule = new SysTopicAcl();
        when(aclService.listAll()).thenReturn(List.of(rule));
        when(aclService.listByUsername("alice")).thenReturn(List.of(rule));
        when(aclService.create(rule)).thenReturn(rule);
        when(aclService.update(1L, rule)).thenReturn(rule);
        assertThat(acl.list().getData()).containsExactly(rule);
        assertThat(acl.listByUsername("alice").getData()).containsExactly(rule);
        assertThat(acl.create(rule).getData()).isSameAs(rule);
        assertThat(acl.update(1L, rule).getData()).isSameAs(rule);
        acl.delete(1L);
        acl.syncToEmqx();
        verify(aclService).delete(1L);
        verify(aclService).syncToEmqx();

        DomainService domainService = mock(DomainService.class);
        DomainController domain = new DomainController(domainService);
        SysDomain sysDomain = new SysDomain();
        when(domainService.listAll()).thenReturn(List.of(sysDomain));
        when(domainService.getById(1L)).thenReturn(sysDomain);
        when(domainService.create(sysDomain)).thenReturn(sysDomain);
        when(domainService.update(1L, sysDomain)).thenReturn(sysDomain);
        assertThat(domain.list().getData()).containsExactly(sysDomain);
        assertThat(domain.tree().isSuccess()).isTrue();
        assertThat(domain.get(1L).getData()).isSameAs(sysDomain);
        assertThat(domain.create(sysDomain).getData()).isSameAs(sysDomain);
        assertThat(domain.update(1L, sysDomain).getData()).isSameAs(sysDomain);
        domain.delete(1L);
        verify(domainService).delete(1L);

        MonitorService monitorService = mock(MonitorService.class);
        MqttClientService mqttClientService = mock(MqttClientService.class);
        MonitorController monitor = new MonitorController(monitorService, mqttClientService);
        when(monitorService.getSystemMetrics()).thenReturn(Map.of("jvm", 1));
        when(monitorService.getTrafficStats()).thenReturn(Map.of("messages", 1));
        when(monitorService.getClientStats()).thenReturn(Map.of("clients", 1));
        when(monitorService.getTopicStats()).thenReturn(Map.of("topics", 1));
        when(mqttClientService.isConnected()).thenReturn(true);
        when(mqttClientService.getActiveProtocol()).thenReturn("TLS");
        assertThat(monitor.getMetrics().getData()).containsEntry("jvm", 1);
        assertThat(monitor.getMessageStats().getData()).containsEntry("messages", 1);
        assertThat(monitor.getClientStats().getData()).containsEntry("clients", 1);
        assertThat(monitor.getTopicStats().getData()).containsEntry("topics", 1);
        assertThat(monitor.getConnectionStatus().getData()).containsEntry("connected", true);

        EmqxApiClient emqx = mock(EmqxApiClient.class);
        StatusController status = new StatusController(emqx);
        assertThat(status.status().getStatus()).isEqualTo("ok");
        when(emqx.isApiReady()).thenReturn(true).thenThrow(new RuntimeException("offline"));
        assertThat(status.emqxStatus().getStatus()).isEqualTo("online");
        assertThat(status.emqxStatus().getStatus()).isEqualTo("offline");

        AuditService auditService = mock(AuditService.class);
        WebhookController webhook = new WebhookController(auditService);
        assertThat(webhook.handleEmqxWebhook(Map.of("event", "client.connected"))).containsEntry("result", "ok");
        verify(auditService).recordFromWebhook(Map.of("event", "client.connected"));
    }

    @Test
    void auditControllerReturnsPageAndPdfResponse() {
        AuditService auditService = mock(AuditService.class);
        AuditController controller = new AuditController(auditService);
        Page<SysAuditLog> page = new Page<>();
        when(auditService.queryLogs(1, 20, "client", "action")).thenReturn(page);
        when(auditService.exportLogsAsPdf("client", "action")).thenReturn("%PDF-".getBytes());

        assertThat(controller.list(1, 20, "client", "action").getData()).isSameAs(page);
        var response = controller.exportPdf("client", "action");
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
        assertThat(response.getBody()).isEqualTo("%PDF-".getBytes());
    }

    @Test
    void subscribeControllerDelegatesAllSessionActionsAndCreatesErrorEmitter() {
        SubscribeService subscribeService = mock(SubscribeService.class);
        SubscribeController controller = new SubscribeController(subscribeService);
        var auth = new UsernamePasswordAuthenticationToken("alice", null);
        Map<String, Object> status = Map.of("mqttConnected", true);
        when(subscribeService.openSse("alice")).thenReturn(new SseEmitter());
        when(subscribeService.getSessionStatus("alice")).thenReturn(status);

        assertThat(controller.openSse(auth)).isNotNull();
        assertThat(controller.connect("Bearer token", auth).getData()).isSameAs(status);
        assertThat(controller.subscribeTopic("topic/a", 1, auth).getData()).isSameAs(status);
        assertThat(controller.cancel("topic/a", auth).getData()).isSameAs(status);
        assertThat(controller.disconnect(auth).getData()).isSameAs(status);
        assertThat(controller.sessionStatus(auth).getData()).isSameAs(status);
        assertThat(controller.close(auth).isSuccess()).isTrue();
        verify(subscribeService).connectMqtt("alice", "token");
        verify(subscribeService).subscribeTopic("alice", "topic/a", 1);
        verify(subscribeService).cancelTopic("alice", "topic/a");
        verify(subscribeService).disconnectMqtt("alice");
        verify(subscribeService).closeAll("alice");

        when(subscribeService.openSse("bob")).thenThrow(new RuntimeException("boom"));
        assertThat(controller.openSse(new UsernamePasswordAuthenticationToken("bob", null))).isNotNull();
    }
}
