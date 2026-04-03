package com.cde.service;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 订阅推送服务 (SSE实时推送)
 */
public interface SubscribeService {

    /** 注册SSE连接 */
    SseEmitter subscribe(String clientId, String connectionId, String topic, int qos);

    /** 取消订阅 */
    void unsubscribe(String clientId, String connectionId, String topic);

    /** 推送消息到所有匹配的SSE连接 */
    void pushMessage(String topic, String payload);
}
