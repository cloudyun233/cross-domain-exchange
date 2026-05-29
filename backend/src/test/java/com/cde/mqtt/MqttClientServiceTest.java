package com.cde.mqtt;

import com.cde.entity.SysUser;
import com.cde.exception.BusinessException;
import com.cde.mapper.SysUserMapper;
import com.hivemq.client.mqtt.MqttClientSslConfig;
import com.hivemq.client.mqtt.MqttClientState;
import com.hivemq.client.mqtt.MqttGlobalPublishFilter;
import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.datatypes.MqttUtf8String;
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient;
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient.Mqtt5SubscribeAndCallbackBuilder;
import com.hivemq.client.mqtt.mqtt5.Mqtt5ClientConfig;
import com.hivemq.client.mqtt.mqtt5.exceptions.Mqtt5PubAckException;
import com.hivemq.client.mqtt.mqtt5.exceptions.Mqtt5SubAckException;
import com.hivemq.client.mqtt.mqtt5.message.auth.Mqtt5SimpleAuth;
import com.hivemq.client.mqtt.mqtt5.message.connect.Mqtt5ConnectBuilder;
import com.hivemq.client.mqtt.mqtt5.message.connect.connack.Mqtt5ConnAck;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5PublishBuilder;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5WillPublishBuilder;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5PublishResult;
import com.hivemq.client.mqtt.mqtt5.message.publish.puback.Mqtt5PubAck;
import com.hivemq.client.mqtt.mqtt5.message.publish.puback.Mqtt5PubAckReasonCode;
import com.hivemq.client.mqtt.mqtt5.message.subscribe.suback.Mqtt5SubAck;
import com.hivemq.client.mqtt.mqtt5.message.subscribe.suback.Mqtt5SubAckReasonCode;
import com.hivemq.client.mqtt.mqtt5.message.unsubscribe.Mqtt5UnsubscribeBuilder;
import com.hivemq.client.mqtt.mqtt5.message.unsubscribe.unsuback.Mqtt5UnsubAck;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.BiConsumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MqttClientServiceTest {

    @Mock
    private SysUserMapper userMapper;

    @Test
    void subscribeStoresTopicOnlyAfterBrokerAckAndRejectsInvalidQos() throws Exception {
        MqttClientService service = new MqttClientService(userMapper);
        Mqtt5AsyncClient client = connectedClient(false);
        Object ctx = addContext(service, "alice", client, true);
        mockSubscribe(client, "topic/a", CompletableFuture.completedFuture(mock(Mqtt5SubAck.class)));

        service.subscribeForUser("alice", "topic/a", 1);

        assertThat(topics(ctx)).containsEntry("topic/a", 1);
        assertThatThrownBy(() -> service.subscribeForUser("alice", "topic/b", 9))
                .isInstanceOf(BusinessException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void subscribeFailureDoesNotStoreTopicAndMapsBrokerError() throws Exception {
        MqttClientService service = new MqttClientService(userMapper);
        Mqtt5AsyncClient client = connectedClient(false);
        Object ctx = addContext(service, "alice", client, true);
        CompletableFuture<Mqtt5SubAck> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("broker timeout"));
        mockSubscribe(client, "topic/a", failed);

        assertThatThrownBy(() -> service.subscribeForUser("alice", "topic/a", 1))
                .isInstanceOf(BusinessException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(topics(ctx)).doesNotContainKey("topic/a");
    }

    @Test
    void unsubscribeRemovesLocalMemoryOnlyWhenBrokerAckSucceeds() throws Exception {
        MqttClientService service = new MqttClientService(userMapper);
        Mqtt5AsyncClient client = connectedClient(false);
        Object ctx = addContext(service, "alice", client, true);
        topics(ctx).put("topic/a", 1);
        mockUnsubscribe(client, "topic/a", CompletableFuture.completedFuture(mock(Mqtt5UnsubAck.class)));

        service.unsubscribeForUser("alice", "topic/a");

        assertThat(topics(ctx)).doesNotContainKey("topic/a");
    }

    @Test
    void unsubscribeKeepsLocalMemoryWhenBrokerFails() throws Exception {
        MqttClientService service = new MqttClientService(userMapper);
        Mqtt5AsyncClient client = connectedClient(false);
        Object ctx = addContext(service, "alice", client, true);
        topics(ctx).put("topic/a", 1);
        CompletableFuture<Mqtt5UnsubAck> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("broker down"));
        mockUnsubscribe(client, "topic/a", failed);

        assertThatThrownBy(() -> service.unsubscribeForUser("alice", "topic/a"))
                .isInstanceOf(BusinessException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(topics(ctx)).containsKey("topic/a");
    }

    @Test
    void unsubscribeRejectsMissingOrDisconnectedContext() throws Exception {
        MqttClientService service = new MqttClientService(userMapper);

        assertThatThrownBy(() -> service.unsubscribeForUser("missing", "topic/a"))
                .isInstanceOf(BusinessException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.BAD_REQUEST);

        addContext(service, "alice", connectedClient(false), false);
        assertThatThrownBy(() -> service.unsubscribeForUser("alice", "topic/a"))
                .isInstanceOf(BusinessException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void publishRequiresConnectedContextAndValidQos() throws Exception {
        MqttClientService service = new MqttClientService(userMapper);
        assertThatThrownBy(() -> service.publishForUser("missing", "topic/a", "payload", 1, false))
                .isInstanceOf(BusinessException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.BAD_REQUEST);

        Mqtt5AsyncClient client = connectedClient(false);
        addContext(service, "alice", client, true);
        mockPublish(client, "topic/a", CompletableFuture.completedFuture(mock(Mqtt5PublishResult.class)));

        service.publishForUser("alice", "topic/a", "payload", 2, true);

        assertThatThrownBy(() -> service.publishForUser("alice", "topic/a", "payload", -1, false))
                .isInstanceOf(BusinessException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void publishMapsBrokerFailureToBusinessException() throws Exception {
        MqttClientService service = new MqttClientService(userMapper);
        Mqtt5AsyncClient client = connectedClient(false);
        addContext(service, "alice", client, true);
        CompletableFuture<Mqtt5PublishResult> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("broker down"));
        mockPublish(client, "topic/a", failed);

        assertThatThrownBy(() -> service.publishForUser("alice", "topic/a", "payload", 1, true))
                .isInstanceOf(BusinessException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.BAD_GATEWAY);
    }

    @Test
    void statusDisconnectCloseAndCleanupUseUserContextState() throws Exception {
        MqttClientService service = new MqttClientService(userMapper);
        Mqtt5AsyncClient tlsClient = connectedClient(true);
        Object active = addContext(service, "active", tlsClient, true);
        topics(active).put("topic/a", 1);
        mockUnsubscribe(tlsClient, "topic/a", CompletableFuture.completedFuture(mock(Mqtt5UnsubAck.class)));

        assertThat(service.isUserConnected("active")).isTrue();
        assertThat(service.getUserProtocol("active")).isEqualTo("TLS");
        assertThat(service.getActiveProtocol()).isEqualTo("TLS");
        assertThat(service.isConnected()).isTrue();
        assertThat(service.getSubscribedTopics("active")).containsExactly("topic/a");

        service.disconnectForUser("active");
        assertThat(ReflectionTestUtils.getField(active, "connected")).isEqualTo(false);
        assertThat(service.getUserProtocol("active")).isEqualTo("未连接");

        Mqtt5AsyncClient oldClient = connectedClient(false);
        Object old = addContext(service, "old", oldClient, false);
        ReflectionTestUtils.setField(old, "disconnectedAtMillis", System.currentTimeMillis() - 3_700_000L);
        service.cleanupDisconnectedContexts();

        assertThat(contexts(service)).doesNotContainKey("old");
        service.closeAll("active");
        assertThat(contexts(service)).doesNotContainKey("active");
    }

    @Test
    void noContextDisconnectCloseDestroyAndEmptyStatusAreSafe() {
        MqttClientService service = new MqttClientService(userMapper);

        service.disconnectForUser("missing");
        service.closeAll("missing");
        service.destroy();

        assertThat(service.getSubscribedTopics("missing")).isEmpty();
        assertThat(service.isUserConnected("missing")).isFalse();
        assertThat(service.getUserProtocol("missing")).isEqualTo("未连接");
        assertThat(service.getActiveProtocol()).isEqualTo("未连接");
        assertThat(service.isConnected()).isFalse();
    }

    @Test
    void closeAllIgnoresBrokerUnsubscribeFailuresAndClearsContext() throws Exception {
        MqttClientService service = new MqttClientService(userMapper);
        Mqtt5AsyncClient client = connectedClient(false);
        Object ctx = addContext(service, "alice", client, true);
        topics(ctx).put("topic/a", 1);
        CompletableFuture<Mqtt5UnsubAck> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("broker down"));
        mockUnsubscribe(client, "topic/a", failed);

        service.closeAll("alice");

        assertThat(contexts(service)).doesNotContainKey("alice");
        assertThat(topics(ctx)).isEmpty();
    }

    @Test
    void destroyClosesAllStoredContexts() throws Exception {
        MqttClientService service = new MqttClientService(userMapper);
        addContext(service, "alice", connectedClient(false), false);
        addContext(service, "bob", connectedClient(false), false);

        service.destroy();

        assertThat(contexts(service)).isEmpty();
    }

    @Test
    void connectForExistingConnectedUserOnlyUpdatesListener() throws Exception {
        MqttClientService service = new MqttClientService(userMapper);
        Mqtt5AsyncClient client = connectedClient(false);
        Object ctx = addContext(service, "alice", client, true);
        BiConsumer<String, String> listener = (topic, payload) -> {};

        service.connectForUser("alice", "token", listener);

        assertThat(ReflectionTestUtils.getField(ctx, "messageListener")).isSameAs(listener);

        service.connectForUser("alice", "token");
        assertThat(ReflectionTestUtils.getField(ctx, "messageListener")).isSameAs(listener);
    }

    @Test
    void connectUsesTlsClientWhenFirstConnectionSucceeds() throws Exception {
        Mqtt5AsyncClient tlsClient = connectedClient(true);
        TestableMqttClientService service = new TestableMqttClientService(userMapper, tlsClient, null);
        SysUser user = new SysUser();
        user.setClientId("client-123");
        when(userMapper.selectOne(any())).thenReturn(user);
        mockConnect(tlsClient, CompletableFuture.completedFuture(mock(Mqtt5ConnAck.class)));
        BiConsumer<String, String> listener = (topic, payload) -> {};

        service.connectForUser("alice", "token", listener);

        assertThat(service.isUserConnected("alice")).isTrue();
        assertThat(service.getUserProtocol("alice")).isEqualTo("TLS");
        Object ctx = contexts(service).get("alice");
        assertThat(ReflectionTestUtils.getField(ctx, "messageListener")).isSameAs(listener);
        verify(tlsClient).publishes(eq(MqttGlobalPublishFilter.ALL), any());
    }

    @Test
    void connectFallsBackToTcpAndRestoresRememberedSubscriptionsAfterTlsTimeout() throws Exception {
        Mqtt5AsyncClient oldClient = connectedClient(false);
        Mqtt5AsyncClient tlsClient = connectedClient(true);
        Mqtt5AsyncClient tcpClient = connectedClient(false);
        TestableMqttClientService service = new TestableMqttClientService(userMapper, tlsClient, tcpClient);
        ReflectionTestUtils.setField(service, "connectTimeoutSeconds", 0);
        SysUser user = new SysUser();
        user.setClientId("client-123");
        when(userMapper.selectOne(any())).thenReturn(user);
        Object oldCtx = addContext(service, "alice", oldClient, false);
        topics(oldCtx).put("topic/a", 1);
        mockConnect(tlsClient, new CompletableFuture<>());
        mockConnect(tcpClient, CompletableFuture.completedFuture(mock(Mqtt5ConnAck.class)));
        mockSubscribe(tcpClient, "topic/a", CompletableFuture.completedFuture(mock(Mqtt5SubAck.class)));

        service.connectForUser("alice", "token");

        assertThat(service.getUserProtocol("alice")).isEqualTo("TCP");
        assertThat(service.getSubscribedTopics("alice")).containsExactly("topic/a");
        verify(tcpClient).connectWith();
    }

    @Test
    void connectMapsTcpFailureToBadGatewayAfterTlsFailure() {
        Mqtt5AsyncClient tlsClient = connectedClient(true);
        Mqtt5AsyncClient tcpClient = connectedClient(false);
        TestableMqttClientService service = new TestableMqttClientService(userMapper, tlsClient, tcpClient);
        when(userMapper.selectOne(any())).thenReturn(null);
        CompletableFuture<Mqtt5ConnAck> tlsFailed = new CompletableFuture<>();
        tlsFailed.completeExceptionally(new RuntimeException("tls down"));
        CompletableFuture<Mqtt5ConnAck> tcpFailed = new CompletableFuture<>();
        tcpFailed.completeExceptionally(new RuntimeException("tcp down"));
        mockConnect(tlsClient, tlsFailed);
        mockConnect(tcpClient, tcpFailed);

        assertThatThrownBy(() -> service.connectForUser("alice", "token"))
                .isInstanceOf(BusinessException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.BAD_GATEWAY);
    }

    @Test
    void clientFactoriesBuildTlsAndTcpClientsWithoutConnecting() {
        MqttClientService service = new MqttClientService(userMapper);
        ReflectionTestUtils.setField(service, "brokerHost", "localhost");
        ReflectionTestUtils.setField(service, "tlsPort", 8883);
        ReflectionTestUtils.setField(service, "tcpPort", 1883);

        ReflectionTestUtils.setField(service, "insecureTrustAll", false);
        Mqtt5AsyncClient tlsClient = ReflectionTestUtils.invokeMethod(service, "buildTlsClient", "client-1");
        ReflectionTestUtils.setField(service, "insecureTrustAll", true);
        Mqtt5AsyncClient insecureTlsClient = ReflectionTestUtils.invokeMethod(service, "buildTlsClient", "client-2");
        Mqtt5AsyncClient tcpClient = ReflectionTestUtils.invokeMethod(service, "buildTcpClient", "client-3");

        assertThat(tlsClient.getConfig().getSslConfig()).isPresent();
        assertThat(insecureTlsClient.getConfig().getSslConfig()).isPresent();
        assertThat(tcpClient.getConfig().getSslConfig()).isEmpty();
    }

    @Test
    void setMessageListenerUpdatesExistingContextAndIgnoresMissingOne() throws Exception {
        MqttClientService service = new MqttClientService(userMapper);
        Object ctx = addContext(service, "alice", connectedClient(false), true);
        BiConsumer<String, String> listener = (topic, payload) -> {};

        service.setMessageListener("alice", listener);
        service.setMessageListener("missing", listener);

        assertThat(ReflectionTestUtils.getField(ctx, "messageListener")).isSameAs(listener);
    }

    @Test
    void globalPublishCallbackForwardsPayloadAndSurvivesListenerFailures() throws Exception {
        MqttClientService service = new MqttClientService(userMapper);
        Mqtt5AsyncClient client = connectedClient(false);
        Object ctx = newContext(client, true);
        ArrayList<String> received = new ArrayList<>();
        ReflectionTestUtils.setField(ctx, "messageListener",
                (BiConsumer<String, String>) (topic, payload) -> received.add(topic + "=" + payload));

        ReflectionTestUtils.invokeMethod(service, "setupGlobalPublishCallback", "alice", ctx);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<java.util.function.Consumer<Mqtt5Publish>> captor = ArgumentCaptor.forClass(java.util.function.Consumer.class);
        verify(client).publishes(eq(MqttGlobalPublishFilter.ALL), captor.capture());

        captor.getValue().accept(Mqtt5Publish.builder()
                .topic("topic/a")
                .payload("payload".getBytes(StandardCharsets.UTF_8))
                .build());
        assertThat(received).containsExactly("topic/a=payload");

        captor.getValue().accept(Mqtt5Publish.builder()
                .topic("topic/long")
                .payload("x".repeat(201).getBytes(StandardCharsets.UTF_8))
                .build());

        ReflectionTestUtils.setField(ctx, "messageListener",
                (BiConsumer<String, String>) (topic, payload) -> { throw new RuntimeException("boom"); });
        captor.getValue().accept(Mqtt5Publish.builder().topic("topic/b").payload(new byte[0]).build());

        ReflectionTestUtils.setField(ctx, "messageListener", null);
        captor.getValue().accept(Mqtt5Publish.builder().topic("topic/c").payload(new byte[0]).build());
    }

    @Test
    void subscriptionMemoryHelpersPreserveAndRestoreTopics() throws Exception {
        MqttClientService service = new MqttClientService(userMapper);
        Object oldCtx = newContext(connectedClient(false), false);
        Object newCtx = newContext(connectedClient(false), true);
        topics(oldCtx).put("topic/a", 1);

        ReflectionTestUtils.invokeMethod(service, "preserveSubscriptions", oldCtx, newCtx);
        assertThat(topics(newCtx)).containsEntry("topic/a", 1);

        Mqtt5AsyncClient successClient = connectedClient(false);
        Object successCtx = newContext(successClient, true);
        topics(successCtx).put("topic/a", 1);
        mockSubscribe(successClient, "topic/a", CompletableFuture.completedFuture(mock(Mqtt5SubAck.class)));
        ReflectionTestUtils.invokeMethod(service, "resubscribeAll", "alice", successCtx);

        Mqtt5AsyncClient failureClient = connectedClient(false);
        Object failureCtx = newContext(failureClient, true);
        topics(failureCtx).put("topic/b", 1);
        CompletableFuture<Mqtt5SubAck> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("broker down"));
        mockSubscribe(failureClient, "topic/b", failed);
        ReflectionTestUtils.invokeMethod(service, "resubscribeAll", "alice", failureCtx);

        Object emptyCtx = newContext(connectedClient(false), true);
        ReflectionTestUtils.invokeMethod(service, "resubscribeAll", "alice", emptyCtx);
    }

    @Test
    void requireConnectedRejectsDisconnectedClientAndMarksAutoReconnect() throws Exception {
        MqttClientService service = new MqttClientService(userMapper);
        addContext(service, "offline", disconnectedClient(), true);
        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(service, "requireConnected", "offline"))
                .isInstanceOf(BusinessException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.BAD_REQUEST);

        Object reconnected = addContext(service, "alice", connectedClient(false), false);
        ReflectionTestUtils.invokeMethod(service, "requireConnected", "alice");

        assertThat(ReflectionTestUtils.getField(reconnected, "connected")).isEqualTo(true);
        assertThat(ReflectionTestUtils.getField(reconnected, "disconnectedAtMillis")).isEqualTo(0L);
    }

    @Test
    void resolveClientIdUsesStoredClientIdAndFallsBackToUsername() {
        MqttClientService service = new MqttClientService(userMapper);
        SysUser user = new SysUser();
        user.setClientId("client-123");
        when(userMapper.selectOne(any())).thenReturn(user).thenReturn(null);

        assertThat(ReflectionTestUtils.invokeMethod(service, "resolveClientId", "alice").toString())
                .isEqualTo("client-123");
        assertThat(ReflectionTestUtils.invokeMethod(service, "resolveClientId", "alice").toString())
                .isEqualTo("alice");
    }

    @Test
    void silentDisconnectHandlesNullAndClientFailure() {
        MqttClientService service = new MqttClientService(userMapper);
        Mqtt5AsyncClient client = connectedClient(false);
        CompletableFuture<Void> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("disconnect failed"));
        when(client.disconnect()).thenReturn(failed);

        ReflectionTestUtils.invokeMethod(service, "silentDisconnectClient", (Object) null);
        ReflectionTestUtils.invokeMethod(service, "silentDisconnectClient", client);
    }

    @Test
    void mapsWrappedBrokerExceptionsToBusinessExceptions() {
        MqttClientService service = new MqttClientService(userMapper);

        RuntimeException publish = ReflectionTestUtils.invokeMethod(service, "mapPublishException",
                "topic/a", new ExecutionException(new RuntimeException("not authorized by acl")));
        RuntimeException subscribe = ReflectionTestUtils.invokeMethod(service, "mapSubscribeException",
                "topic/a", new ExecutionException(new RuntimeException("broker timeout")));

        assertThat(publish).isInstanceOf(BusinessException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(subscribe).isInstanceOf(BusinessException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.BAD_GATEWAY);
    }

    @Test
    void mapsPubAckReasonCodesToBusinessExceptions() {
        MqttClientService service = new MqttClientService(userMapper);

        assertThat(publishMappedStatus(service, Mqtt5PubAckReasonCode.NOT_AUTHORIZED, Optional.empty()))
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(publishMappedStatus(service, Mqtt5PubAckReasonCode.TOPIC_NAME_INVALID, Optional.empty()))
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(publishMappedStatus(service, Mqtt5PubAckReasonCode.PAYLOAD_FORMAT_INVALID, Optional.empty()))
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(publishMappedStatus(service, Mqtt5PubAckReasonCode.UNSPECIFIED_ERROR,
                Optional.of(MqttUtf8String.of("acl denied"))))
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(publishMappedStatus(service, Mqtt5PubAckReasonCode.UNSPECIFIED_ERROR,
                Optional.of(MqttUtf8String.of("quota exceeded"))))
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void mapsSubAckReasonCodesToBusinessExceptions() {
        MqttClientService service = new MqttClientService(userMapper);

        assertThat(subscribeMappedStatus(service, List.of(Mqtt5SubAckReasonCode.NOT_AUTHORIZED), Optional.empty()))
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(subscribeMappedStatus(service, List.of(Mqtt5SubAckReasonCode.TOPIC_FILTER_INVALID), Optional.empty()))
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(subscribeMappedStatus(service, List.of(Mqtt5SubAckReasonCode.UNSPECIFIED_ERROR),
                Optional.of(MqttUtf8String.of("quota exceeded"))))
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private HttpStatus publishMappedStatus(
            MqttClientService service, Mqtt5PubAckReasonCode reasonCode, Optional<MqttUtf8String> reasonString
    ) {
        Mqtt5PubAck ack = mock(Mqtt5PubAck.class);
        when(ack.getReasonCode()).thenReturn(reasonCode);
        when(ack.getReasonString()).thenReturn(reasonString);
        RuntimeException mapped = ReflectionTestUtils.invokeMethod(service, "mapPublishException",
                "topic/a", new ExecutionException(new Mqtt5PubAckException(ack, "failed")));
        return (HttpStatus) ((BusinessException) mapped).getStatus();
    }

    private HttpStatus subscribeMappedStatus(
            MqttClientService service, List<Mqtt5SubAckReasonCode> reasonCodes, Optional<MqttUtf8String> reasonString
    ) {
        Mqtt5SubAck ack = mock(Mqtt5SubAck.class);
        when(ack.getReasonCodes()).thenReturn(reasonCodes);
        when(ack.getReasonString()).thenReturn(reasonString);
        RuntimeException mapped = ReflectionTestUtils.invokeMethod(service, "mapSubscribeException",
                "topic/a", new ExecutionException(new Mqtt5SubAckException(ack, "failed")));
        return (HttpStatus) ((BusinessException) mapped).getStatus();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> contexts(MqttClientService service) {
        return (Map<String, Object>) ReflectionTestUtils.getField(service, "userContexts");
    }

    private Object addContext(MqttClientService service, String username, Mqtt5AsyncClient client, boolean connected)
            throws Exception {
        Object ctx = newContext(client, connected);
        contexts(service).put(username, ctx);
        return ctx;
    }

    private Object newContext(Mqtt5AsyncClient client, boolean connected) throws Exception {
        Class<?> contextClass = Class.forName("com.cde.mqtt.MqttClientService$UserMqttContext");
        Constructor<?> constructor = contextClass.getDeclaredConstructor(Mqtt5AsyncClient.class);
        constructor.setAccessible(true);
        Object ctx = constructor.newInstance(client);
        ReflectionTestUtils.setField(ctx, "connected", connected);
        if (!connected) {
            ReflectionTestUtils.setField(ctx, "disconnectedAtMillis", System.currentTimeMillis());
        }
        return ctx;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Integer> topics(Object ctx) {
        return (Map<String, Integer>) ReflectionTestUtils.getField(ctx, "subscribedTopics");
    }

    private Mqtt5AsyncClient connectedClient(boolean tls) {
        Mqtt5AsyncClient client = mock(Mqtt5AsyncClient.class);
        Mqtt5ClientConfig config = mock(Mqtt5ClientConfig.class);
        when(client.getState()).thenReturn(MqttClientState.CONNECTED);
        when(client.getConfig()).thenReturn(config);
        when(config.getSslConfig()).thenReturn(tls ? Optional.of(mock(MqttClientSslConfig.class)) : Optional.empty());
        when(client.disconnect()).thenReturn(CompletableFuture.completedFuture(null));
        return client;
    }

    private Mqtt5AsyncClient disconnectedClient() {
        Mqtt5AsyncClient client = connectedClient(false);
        when(client.getState()).thenReturn(MqttClientState.DISCONNECTED);
        return client;
    }

    private void mockSubscribe(Mqtt5AsyncClient client, String topic, CompletableFuture<Mqtt5SubAck> result) {
        Mqtt5SubscribeAndCallbackBuilder.Start start = mock(Mqtt5SubscribeAndCallbackBuilder.Start.class);
        Mqtt5SubscribeAndCallbackBuilder.Start.Complete complete =
                mock(Mqtt5SubscribeAndCallbackBuilder.Start.Complete.class);
        when(client.subscribeWith()).thenReturn(start);
        when(start.topicFilter(topic)).thenReturn(complete);
        when(complete.qos(any(MqttQos.class))).thenReturn(complete);
        when(complete.send()).thenReturn(result);
    }

    @SuppressWarnings("unchecked")
    private void mockUnsubscribe(Mqtt5AsyncClient client, String topic, CompletableFuture<Mqtt5UnsubAck> result) {
        Mqtt5UnsubscribeBuilder.Send.Start<CompletableFuture<Mqtt5UnsubAck>> start =
                mock(Mqtt5UnsubscribeBuilder.Send.Start.class);
        Mqtt5UnsubscribeBuilder.Send.Complete<CompletableFuture<Mqtt5UnsubAck>> complete =
                mock(Mqtt5UnsubscribeBuilder.Send.Complete.class);
        when(client.unsubscribeWith()).thenReturn(start);
        when(start.topicFilter(topic)).thenReturn(complete);
        when(complete.send()).thenReturn(result);
    }

    @SuppressWarnings("unchecked")
    private void mockPublish(Mqtt5AsyncClient client, String topic, CompletableFuture<Mqtt5PublishResult> result) {
        Mqtt5PublishBuilder.Send<CompletableFuture<Mqtt5PublishResult>> start =
                mock(Mqtt5PublishBuilder.Send.class);
        Mqtt5PublishBuilder.Send.Complete<CompletableFuture<Mqtt5PublishResult>> complete =
                mock(Mqtt5PublishBuilder.Send.Complete.class);
        when(client.publishWith()).thenReturn(start);
        when(start.topic(topic)).thenReturn(complete);
        when(complete.payload(any(byte[].class))).thenReturn(complete);
        when(complete.qos(any(MqttQos.class))).thenReturn(complete);
        when(complete.retain(anyBoolean())).thenReturn(complete);
        when(complete.send()).thenReturn(result);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void mockConnect(Mqtt5AsyncClient client, CompletableFuture<Mqtt5ConnAck> result) {
        Mqtt5ConnectBuilder.Send<CompletableFuture<Mqtt5ConnAck>> connect =
                mock(Mqtt5ConnectBuilder.Send.class);
        Mqtt5WillPublishBuilder.Nested.Complete will =
                mock(Mqtt5WillPublishBuilder.Nested.Complete.class);
        when(client.connectWith()).thenReturn(connect);
        when(connect.simpleAuth(any(Mqtt5SimpleAuth.class))).thenReturn(connect);
        when(connect.cleanStart(false)).thenReturn(connect);
        when(connect.sessionExpiryInterval(3600)).thenReturn(connect);
        when(connect.keepAlive(60)).thenReturn(connect);
        when(connect.willPublish()).thenReturn(will);
        when(will.topic(anyString())).thenReturn(will);
        when(will.payload(any(byte[].class))).thenReturn(will);
        when(will.qos(any(MqttQos.class))).thenReturn(will);
        when(will.retain(true)).thenReturn(will);
        when(will.applyWillPublish()).thenReturn(connect);
        when(connect.send()).thenReturn(result);
    }

    private static class TestableMqttClientService extends MqttClientService {
        private final Mqtt5AsyncClient tlsClient;
        private final Mqtt5AsyncClient tcpClient;

        TestableMqttClientService(SysUserMapper userMapper, Mqtt5AsyncClient tlsClient, Mqtt5AsyncClient tcpClient) {
            super(userMapper);
            this.tlsClient = tlsClient;
            this.tcpClient = tcpClient;
        }

        @Override
        protected Mqtt5AsyncClient buildTlsClient(String clientId) {
            return tlsClient;
        }

        @Override
        protected Mqtt5AsyncClient buildTcpClient(String clientId) {
            return tcpClient;
        }
    }
}
