package com.cde.service;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 订阅推送服务 (SSE实时推送)
 * 用户级连接：每个用户独立MQTT连接，支持离线消息补发
 */
public interface SubscribeService {

    /** 注册SSE连接并建立用户级MQTT订阅 */
    SseEmitter subscribe(String username, String token, String topic, int qos);

    /** 取消订阅 */
    void unsubscribe(String username, String topic);

    /** 推送消息到所有匹配的SSE连接 */
    void pushMessage(String topic, String payload);
}
