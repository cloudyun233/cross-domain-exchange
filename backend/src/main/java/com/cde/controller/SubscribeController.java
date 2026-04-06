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
            Authentication auth) {

        String username = auth.getName();
        String token = authHeader.replace("Bearer ", "");
        try {
            return subscribeService.subscribe(username, token, topic, qos);
        } catch (BusinessException e) {
            return createErrorEmitter(e.getMessage(), false);
        } catch (Exception e) {
            log.error("创建订阅 SSE 失败, user={}, topic={}", username, topic, e);
            return createErrorEmitter("SSE连接失败: " + e.getMessage(), true);
        }
    }

    @PostMapping("/cancel")
    public ApiResponse<Void> cancel(@RequestParam String topic, Authentication auth) {
        String username = auth.getName();
        subscribeService.unsubscribe(username, topic);
        return ApiResponse.ok("已取消订阅", null);
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
