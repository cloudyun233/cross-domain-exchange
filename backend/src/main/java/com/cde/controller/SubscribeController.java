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

    /**
     * 仅建立 SSE 长连接，不触发任何 MQTT 操作。
     * 前端进入订阅页面时立即调用，保持最长生命周期。
     * GET /api/subscribe/sse
     */
    @GetMapping(value = "/sse", produces = "text/event-stream")
    public SseEmitter openSse(Authentication auth) {
        String username = auth.getName();
        try {
            return subscribeService.openSse(username);
        } catch (Exception e) {
            log.error("Failed to open SSE channel for user={}", username, e);
            return createErrorEmitter("SSE连接失败: " + e.getMessage(), true);
        }
    }

    /**
     * 兼容旧流程：一次性建立 SSE + MQTT 连接 + 订阅主题。
     * 若 SSE 已存在（通过 /sse 建立）则复用，不重建。
     * GET /api/subscribe/stream
     */
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

    /**
     * 在已有 MQTT 连接和 SSE 通道的情况下，新增订阅主题。
     * 前端点「开始监听」时调用（MQTT 已连接情局）。
     * POST /api/subscribe/topic
     */
    @PostMapping("/topic")
    public ApiResponse<Map<String, Object>> subscribeTopic(
            @RequestParam String topic,
            @RequestParam(defaultValue = "1") int qos,
            Authentication auth
    ) {
        String username = auth.getName();
        try {
            subscribeService.subscribeTopic(username, topic, qos);
            return ApiResponse.ok("订阅成功", subscribeService.getSessionStatus(username));
        } catch (BusinessException e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    @GetMapping("/session-status")
    public ApiResponse<Map<String, Object>> sessionStatus(Authentication auth) {
        return ApiResponse.ok(subscribeService.getSessionStatus(auth.getName()));
    }

    /**
     * 连接 MQTT（不重建 SSE）。
     * 前端点"连接 MQTT"按钮时调用，要求 SSE 已事先建立。
     * cleanStart=false 触发持久会话，EMQX 自动推送 offline 消息。
     * POST /api/subscribe/connect
     */
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

    /**
     * 仅断开 MQTT，SSE 保持长连接。
     * EMQX 开始为该 clientId 缓存离线消息。
     * POST /api/subscribe/disconnect
     */
    @PostMapping("/disconnect")
    public ApiResponse<Map<String, Object>> disconnect(Authentication auth) {
        String username = auth.getName();
        subscribeService.disconnectSession(username);
        return ApiResponse.ok("MQTT连接已断开（SSE保持）", subscribeService.getSessionStatus(username));
    }

    /**
     * 完全关闭订阅会话（退出登录 / 关闭标签页时调用）。
     * 断开 MQTT + 关闭 SSE + 清空订阅记忆。
     * POST /api/subscribe/close
     */
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
