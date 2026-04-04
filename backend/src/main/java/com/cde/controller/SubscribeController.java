package com.cde.controller;

import com.cde.dto.ApiResponse;
import com.cde.service.SubscribeService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

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
            Authentication auth) {

        String username = auth.getName();
        String token = authHeader.replace("Bearer ", "");
        return subscribeService.subscribe(username, token, topic, qos);
    }

    @PostMapping("/cancel")
    public ApiResponse<Void> cancel(@RequestParam String topic, @RequestParam(required = false) String connectionId, Authentication auth) {
        String username = auth.getName();
        subscribeService.unsubscribe(username, topic);
        return ApiResponse.ok("已取消订阅", null);
    }
}
