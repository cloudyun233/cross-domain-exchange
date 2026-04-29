package com.cde.service;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

/**
 * 订阅服务接口 —— 精简重写版
 *
 * 职责：SSE 长连接管理 + MQTT 连接/断开/订阅/取消 联动
 */
public interface SubscribeService {

    /**
     * 建立SSE长连接
     *
     * <p>超时策略：SSE_TIMEOUT_MS=0即永不超时，由15s心跳维持连接活性。
     * 若用户已存在旧emitter则先关闭再创建，保证单用户单连接。</p>
     *
     * @param username 用户名
     * @return SseEmitter 实例
     */
    SseEmitter openSse(String username);

    /**
     * 连接MQTT
     *
     * <p>cleanStart=false，启用持久会话（Persistent Session），Broker保留该客户端的
     * 订阅信息和离线消息。客户端重连后自动恢复会话，无需重新订阅。</p>
     *
     * @param username 用户名
     * @param token    JWT令牌，用于MQTT认证
     */
    void connectMqtt(String username, String token);

    /** 订阅主题（MQTT 必须已连接） */
    void subscribeTopic(String username, String topic, int qos);

    /** 取消订阅（向 broker 发送 UNSUBSCRIBE + 清除本地记忆） */
    void cancelTopic(String username, String topic);

    /** 仅断开 MQTT（SSE 保持，订阅记忆保持，EMQX 开始缓存离线消息） */
    void disconnectMqtt(String username);

    /**
     * 完全关闭会话
     *
     * <p>执行完整清理：取消所有MQTT订阅 → 断开MQTT连接 → 关闭SSE emitter。
     * 典型场景：用户退出登录时调用。</p>
     */
    void closeAll(String username);

    /** 查询连接状态 */
    Map<String, Object> getSessionStatus(String username);
}
