package com.cde.service;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

public interface SubscribeService {

    /** 仅建立 SSE 长连接，不触发 MQTT 操作（前端进入订阅页时调用） */
    SseEmitter openSse(String username);

    /** 兼容旧流程：SSE + MQTT + 订阅一体 */
    SseEmitter subscribe(String username, String token, String topic, int qos);

    /**
     * 在已有 MQTT 连接的情况下新增订阅主题。
     * 要求：MQTT 必须已连接且 SSE 必须已建立。
     * 电斯其实点“开始监听”按钟时调用（MQTT 已连接情局）。
     */
    void subscribeTopic(String username, String topic, int qos);

    void unsubscribe(String username, String topic);

    Map<String, Object> getSessionStatus(String username);

    /** 连接 MQTT（不重建 SSE），cleanStart=false 触发持久会话 */
    void connectSession(String username, String token);

    /** 仅断开 MQTT，SSE 保持，EMQX 开始缓存离线消息 */
    void disconnectSession(String username);

    /** 完全关闭：断开 MQTT + 关闭 SSE（退出登录时） */
    void closeSession(String username);
}
