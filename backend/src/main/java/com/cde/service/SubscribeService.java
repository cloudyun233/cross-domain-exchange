package com.cde.service;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

public interface SubscribeService {

    SseEmitter subscribe(String username, String token, String topic, int qos);

    void unsubscribe(String username, String topic);

    Map<String, Object> getSessionStatus(String username);

    void connectSession(String username, String token);

    void disconnectSession(String username);

    void closeSession(String username);
}
