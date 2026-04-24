package com.cde.service;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

/**
 * 订阅服务接口 —— 精简重写版
 *
 * 职责：SSE 长连接管理 + MQTT 连接/断开/订阅/取消 联动
 */
public interface SubscribeService {

    /** 建立 SSE 长连接（前端进入订阅页时调用） */
    SseEmitter openSse(String username);

    /** 连接 MQTT（cleanStart=false，触发持久会话，自动恢复已记忆订阅） */
    void connectMqtt(String username, String token);

    /** 订阅主题（MQTT 必须已连接） */
    void subscribeTopic(String username, String topic, int qos);

    /** 取消订阅（向 broker 发送 UNSUBSCRIBE + 清除本地记忆） */
    void cancelTopic(String username, String topic);

    /** 仅断开 MQTT（SSE 保持，订阅记忆保持，EMQX 开始缓存离线消息） */
    void disconnectMqtt(String username);

    /** 完全关闭：取消所有订阅 + 断开 MQTT + 关闭 SSE（退出登录时调用） */
    void closeAll(String username);

    /** 查询连接状态 */
    Map<String, Object> getSessionStatus(String username);
}
