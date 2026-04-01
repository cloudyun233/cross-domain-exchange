package com.cde.controller;

import com.cde.dto.ApiResponse;
import com.cde.service.AuthService;
import com.cde.service.SubscribeService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

@RestController
@RequestMapping("/api/subscribe")
@RequiredArgsConstructor
public class SubscribeController {

    private final SubscribeService subscribeService;
    private final AuthService authService;

    /**
     * 开始订阅 (返回SSE流)
     */
    @GetMapping(value = "/stream", produces = "text/event-stream")
    public SseEmitter stream(
            @RequestParam String topic,
            @RequestParam(defaultValue = "1") int qos,
            Authentication auth) {

        String clientId = auth.getName();

        // ACL权限校验
        if (!authService.checkACL(clientId, topic, "subscribe")) {
            SseEmitter emitter = new SseEmitter();
            try {
                emitter.send(SseEmitter.event().name("error")
                        .data(Map.of("message", "ACL校验失败：无订阅权限")));
                emitter.complete();
            } catch (Exception e) { /* ignore */ }
            return emitter;
        }

        return subscribeService.subscribe(clientId, topic, qos);
    }

    /**
     * 取消订阅
     */
    @PostMapping("/cancel")
    public ApiResponse<Void> cancel(@RequestParam String topic, Authentication auth) {
        subscribeService.unsubscribe(auth.getName(), topic);
        return ApiResponse.ok("已取消订阅", null);
    }
}
