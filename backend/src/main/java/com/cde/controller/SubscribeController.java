package com.cde.controller;

import com.cde.dto.ApiResponse;
import com.cde.exception.BusinessException;
import com.cde.service.SubscribeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/subscribe")
@RequiredArgsConstructor
public class SubscribeController {

    private final SubscribeService subscribeService;

    @GetMapping(value = "/stream", produces = "text/event-stream")
    public SseEmitter stream(
            @RequestParam String topic,
            @RequestParam(defaultValue = "1") int qos,
            @RequestHeader("Authorization") String authHeader,
            Authentication auth
    ) {
        String username = auth.getName();
        String token = authHeader.replace("Bearer ", "");
        try {
            return subscribeService.subscribe(username, token, topic, qos);
        } catch (BusinessException e) {
            return createErrorEmitter(e.getMessage(), false);
        } catch (Exception e) {
            log.error("Failed to create SSE subscription, user={}, topic={}", username, topic, e);
            return createErrorEmitter("SSE连接失败: " + e.getMessage(), true);
        }
    }

    @PostMapping("/cancel")
    public ApiResponse<Void> cancel(@RequestParam String topic, Authentication auth) {
        String username = auth.getName();
        subscribeService.unsubscribe(username, topic);
        return ApiResponse.ok("已取消订阅", null);
    }

    @GetMapping("/session-status")
    public ApiResponse<Map<String, Object>> sessionStatus(Authentication auth) {
        return ApiResponse.ok(subscribeService.getSessionStatus(auth.getName()));
    }

    @PostMapping("/connect")
    public ApiResponse<Map<String, Object>> connect(
            @RequestHeader("Authorization") String authHeader,
            Authentication auth
    ) {
        String token = authHeader.replace("Bearer ", "");
        String username = auth.getName();
        subscribeService.connectSession(username, token);
        return ApiResponse.ok("MQTT连接已建立", subscribeService.getSessionStatus(username));
    }

    @PostMapping("/disconnect")
    public ApiResponse<Map<String, Object>> disconnect(Authentication auth) {
        String username = auth.getName();
        subscribeService.disconnectSession(username);
        return ApiResponse.ok("MQTT连接已断开", subscribeService.getSessionStatus(username));
    }

    @PostMapping("/close")
    public ApiResponse<Void> close(Authentication auth) {
        subscribeService.closeSession(auth.getName());
        return ApiResponse.ok("订阅会话已关闭", null);
    }

    private SseEmitter createErrorEmitter(String message, boolean reconnectable) {
        SseEmitter emitter = new SseEmitter(3000L);
        try {
            emitter.send(SseEmitter.event()
                    .name("error")
                    .data(Map.of(
                            "message", message,
                            "reconnectable", reconnectable
                    )));
            emitter.complete();
        } catch (IOException e) {
            emitter.completeWithError(e);
        }
        return emitter;
    }
}
