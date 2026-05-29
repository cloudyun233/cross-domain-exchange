package com.cde.service.impl;

import com.cde.exception.BusinessException;
import com.cde.mqtt.MqttClientService;
import com.cde.service.AuditService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscribeServiceImplTest {

    @Mock
    private MqttClientService mqttClientService;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private SubscribeServiceImpl service;

    @Test
    void openSseRegistersEmitterAndMessageListener() {
        ArgumentCaptor<BiConsumer<String, String>> listener = ArgumentCaptor.forClass(BiConsumer.class);
        when(mqttClientService.getSubscribedTopics("alice")).thenReturn(Set.of());
        when(mqttClientService.getUserProtocol("alice")).thenReturn("not connected");

        var emitter = service.openSse("alice");

        assertThat(emitter).isNotNull();
        verify(mqttClientService).setMessageListener(eq("alice"), listener.capture());
        listener.getValue().accept("topic/a", "payload");
        assertThat(service.getSessionStatus("alice")).containsEntry("sseConnected", true);
    }

    @Test
    void connectSubscribeCancelDisconnectAndCloseAuditActions() {
        service.openSse("alice");
        when(mqttClientService.getSubscribedTopics("alice")).thenReturn(Set.of("topic/a"));
        when(mqttClientService.isUserConnected("alice")).thenReturn(true);

        service.connectMqtt("alice", "token");
        service.subscribeTopic("alice", "topic/a", 1);
        service.cancelTopic("alice", "topic/a");
        service.disconnectMqtt("alice");
        service.closeAll("alice");

        verify(mqttClientService).connectForUser(eq("alice"), eq("token"), any());
        verify(mqttClientService).subscribeForUser("alice", "topic/a", 1);
        verify(mqttClientService).unsubscribeForUser("alice", "topic/a");
        verify(mqttClientService).disconnectForUser("alice");
        verify(mqttClientService).closeAll("alice");
        verify(auditService).log(eq("alice"), eq("mqtt_connect"), any(), eq("backend"));
        verify(auditService).log(eq("alice"), eq("subscribe"), any(), eq("backend"));
        verify(auditService).log(eq("alice"), eq("unsubscribe"), any(), eq("backend"));
        verify(auditService).log(eq("alice"), eq("mqtt_disconnect"), any(), eq("backend"));
        verify(auditService).log(eq("alice"), eq("subscribe_close"), any(), eq("backend"));
    }

    @Test
    void connectMqttWorksBeforeSseExists() {
        when(mqttClientService.getSubscribedTopics("alice")).thenReturn(Set.of());

        service.connectMqtt("alice", "token");

        verify(mqttClientService).connectForUser(eq("alice"), eq("token"), any());
        verify(auditService).log(eq("alice"), eq("mqtt_connect"), any(), eq("backend"));
    }

    @Test
    void subscribeRequiresSseAndMqttConnection() {
        assertThatThrownBy(() -> service.subscribeTopic("alice", "topic/a", 1))
                .isInstanceOf(BusinessException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.BAD_REQUEST);

        service.openSse("alice");
        when(mqttClientService.isUserConnected("alice")).thenReturn(false);

        assertThatThrownBy(() -> service.subscribeTopic("alice", "topic/a", 1))
                .isInstanceOf(BusinessException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void sessionStatusReflectsMqttAndSseState() {
        service.openSse("alice");
        when(mqttClientService.getSubscribedTopics("alice")).thenReturn(Set.of("a", "b"));
        when(mqttClientService.isUserConnected("alice")).thenReturn(true);
        when(mqttClientService.getUserProtocol("alice")).thenReturn("TCP");

        assertThat(service.getSessionStatus("alice"))
                .containsEntry("mqttConnected", true)
                .containsEntry("protocol", "TCP")
                .containsEntry("sseConnected", true)
                .containsEntry("subscriptionCount", 2);
    }

    @Test
    void heartbeatRemovesBrokenEmitterWithoutThrowing() {
        SseEmitter emitter = mock(SseEmitter.class);
        emitters().put("alice", emitter);
        try {
            doThrow(new IOException("broken")).when(emitter).send(any(SseEmitter.SseEventBuilder.class));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        when(mqttClientService.getSubscribedTopics("alice")).thenReturn(Set.of());
        when(mqttClientService.getUserProtocol("alice")).thenReturn("not connected");

        service.heartbeat();

        assertThat(service.getSessionStatus("alice")).containsEntry("sseConnected", false);
    }

    @Test
    void pushCallbackHandlesMissingEmitterLongPayloadAndSendFailure() throws Exception {
        @SuppressWarnings("unchecked")
        BiConsumer<String, String> callback = ReflectionTestUtils.invokeMethod(service, "createPushCallback", "alice");

        callback.accept("topic/missing", "payload");

        SseEmitter emitter = mock(SseEmitter.class);
        emitters().put("alice", emitter);
        callback.accept("topic/long", "x".repeat(201));
        verify(emitter).send(any(SseEmitter.SseEventBuilder.class));

        doThrow(new IOException("broken")).when(emitter).send(any(SseEmitter.SseEventBuilder.class));
        callback.accept("topic/fail", "payload");

        assertThat(emitters()).doesNotContainKey("alice");
    }

    @SuppressWarnings("unchecked")
    private Map<String, SseEmitter> emitters() {
        return (Map<String, SseEmitter>) ReflectionTestUtils.getField(service, "userEmitters");
    }
}
