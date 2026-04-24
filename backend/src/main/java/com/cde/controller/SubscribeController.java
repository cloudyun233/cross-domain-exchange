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

/**
 * 订阅 REST API —— 精简重写版
 *
 * API 列表：
 * GET  /api/subscribe/sse              建立 SSE 长连接
 * POST /api/subscribe/connect          连接 MQTT（cleanStart=false）
 * POST /api/subscribe/topic            订阅主题
 * POST /api/subscribe/cancel           取消订阅
 * POST /api/subscribe/disconnect       断开 MQTT（保持 SSE + 订阅记忆）
 * GET  /api/subscribe/session-status   查询连接状态
 * POST /api/subscribe/close            完全关闭（退出登录）
 */
@Slf4j
@RestController
@RequestMapping("/api/subscribe")
@RequiredArgsConstructor
public class SubscribeController {

    private final SubscribeService subscribeService;

    /**
     * 建立 SSE 长连接。
     * 前端进入订阅页面时立即调用。
     */
    @GetMapping(value = "/sse", produces = "text/event-stream")
    public SseEmitter openSse(Authentication auth) {
        String username = auth.getName();
        log.info("[API] GET /sse: username={}", username);
        try {
            return subscribeService.openSse(username);
        } catch (Exception e) {
            log.error("[API] openSse 失败: username={}", username, e);
            return createErrorEmitter("SSE连接失败: " + e.getMessage());
        }
    }

    /**
     * 连接 MQTT（cleanStart=false，持久会话）。
     * 自动恢复已记忆的订阅，EMQX 推送 offline 消息。
     */
    @PostMapping("/connect")
    public ApiResponse<Map<String, Object>> connect(
            @RequestHeader("Authorization") String authHeader,
            Authentication auth
    ) {
        String username = auth.getName();
        String token = authHeader.replace("Bearer ", "");
        log.info("[API] POST /connect: username={}", username);
        try {
            subscribeService.connectMqtt(username, token);
            return ApiResponse.ok("MQTT连接已建立", subscribeService.getSessionStatus(username));
        } catch (BusinessException e) {
            log.warn("[API] connect 失败: username={}, error={}", username, e.getMessage());
            return ApiResponse.fail(e.getMessage());
        }
    }

    /**
     * 订阅主题。
     * 要求：MQTT 已连接 + SSE 已建立。
     */
    @PostMapping("/topic")
    public ApiResponse<Map<String, Object>> subscribeTopic(
            @RequestParam String topic,
            @RequestParam(defaultValue = "1") int qos,
            Authentication auth
    ) {
        String username = auth.getName();
        log.info("[API] POST /topic: username={}, topic={}, qos={}", username, topic, qos);
        try {
            subscribeService.subscribeTopic(username, topic, qos);
            return ApiResponse.ok("订阅成功", subscribeService.getSessionStatus(username));
        } catch (BusinessException e) {
            log.warn("[API] subscribeTopic 失败: username={}, topic={}, error={}", username, topic, e.getMessage());
            return ApiResponse.fail(e.getMessage());
        }
    }

    /**
     * 取消订阅。
     */
    @PostMapping("/cancel")
    public ApiResponse<Map<String, Object>> cancel(
            @RequestParam String topic,
            Authentication auth
    ) {
        String username = auth.getName();
        log.info("[API] POST /cancel: username={}, topic={}", username, topic);
        subscribeService.cancelTopic(username, topic);
        return ApiResponse.ok("已取消订阅", subscribeService.getSessionStatus(username));
    }

    /**
     * 仅断开 MQTT，SSE 保持长连接，订阅记忆保持。
     * EMQX 开始为该 clientId 缓存离线消息。
     */
    @PostMapping("/disconnect")
    public ApiResponse<Map<String, Object>> disconnect(Authentication auth) {
        String username = auth.getName();
        log.info("[API] POST /disconnect: username={}", username);
        subscribeService.disconnectMqtt(username);
        return ApiResponse.ok("MQTT已断开（SSE保持）", subscribeService.getSessionStatus(username));
    }

    /**
     * 查询连接状态。
     */
    @GetMapping("/session-status")
    public ApiResponse<Map<String, Object>> sessionStatus(Authentication auth) {
        return ApiResponse.ok(subscribeService.getSessionStatus(auth.getName()));
    }

    /**
     * 完全关闭订阅会话（退出登录 / 关闭标签页时调用）。
     * 取消所有订阅 + 断开 MQTT + 关闭 SSE。
     */
    @PostMapping("/close")
    public ApiResponse<Void> close(Authentication auth) {
        String username = auth.getName();
        log.info("[API] POST /close: username={}", username);
        subscribeService.closeAll(username);
        return ApiResponse.ok("订阅会话已关闭", null);
    }

    private SseEmitter createErrorEmitter(String message) {
        SseEmitter emitter = new SseEmitter(3000L);
        try {
            emitter.send(SseEmitter.event()
                    .name("error")
                    .data(Map.of("message", message, "reconnectable", true)));
            emitter.complete();
        } catch (IOException e) {
            emitter.completeWithError(e);
        }
        return emitter;
    }
}
