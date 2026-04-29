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
 * <p>管理 SSE 长连接与 MQTT 客户端的生命周期，实现消息的实时推送。
 * SSE 与 MQTT 解耦设计：SSE 负责服务端到前端的消息推送通道，
 * MQTT 负责与 EMQX Broker 的消息收发。断开 MQTT 时 SSE 保持连接，
 * Broker 会缓存离线消息，重连后自动恢复。
 *
 * <p>生命周期：openSse -> connect -> subscribeTopic -> [cancel] -> disconnect -> close
 *
 * <p>API 列表：
 * <ul>
 *   <li>GET  /api/subscribe/sse              建立 SSE 长连接</li>
 *   <li>POST /api/subscribe/connect          连接 MQTT（cleanStart=false）</li>
 *   <li>POST /api/subscribe/topic            订阅主题</li>
 *   <li>POST /api/subscribe/cancel           取消订阅</li>
 *   <li>POST /api/subscribe/disconnect       断开 MQTT（保持 SSE + 订阅记忆）</li>
 *   <li>GET  /api/subscribe/session-status   查询连接状态</li>
 *   <li>POST /api/subscribe/close            完全关闭（退出登录）</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/subscribe")
@RequiredArgsConstructor
public class SubscribeController {

    private final SubscribeService subscribeService;

    /**
     * 建立 SSE 长连接。
     *
     * <p>前端进入订阅页面时首先调用此接口，建立服务端到前端的消息推送通道。
     * SSE 连接建立后，后续 MQTT 收到的消息将通过此通道实时推送到前端。
     * 若 SSE 建立失败，返回一个 error 事件的 SseEmitter，前端可据此提示并重连。
     *
     * @param auth Spring Security 认证信息，用于提取当前用户名
     * @return SSE 发射器，用于持续推送消息事件
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
     *
     * <p>使用 JWT 作为 MQTT 连接凭据，cleanStart=false 表示复用持久会话。
     * 前端通常先建立 SSE；若未建立，后端仍会连接 MQTT5，但消息可能暂时无法推送。
     *
     * @param authHeader Authorization 请求头，JWT 令牌用于 MQTT 认证
     * @param auth       Spring Security 认证信息
     * @return 当前会话状态信息
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
     *
     * <p>向 EMQX Broker 发送订阅请求，订阅成功后消息通过 SSE 推送到前端。
     * 前置条件：MQTT 已连接且 SSE 已建立。
     *
     * @param topic 订阅的主题路径
     * @param qos   服务质量等级，默认 1（至少一次）
     * @param auth  Spring Security 认证信息
     * @return 当前会话状态信息（含已订阅主题列表）
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
     *
     * <p>向 EMQX Broker 发送取消订阅请求，取消后不再接收该主题的消息。
     *
     * @param topic 要取消订阅的主题路径
     * @param auth  Spring Security 认证信息
     * @return 当前会话状态信息
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
     * 仅断开 MQTT 连接，SSE 保持长连接，订阅记忆保持。
     *
     * <p>断开后 EMQX 开始为该 clientId 缓存离线消息，下次 connect 时自动恢复订阅并推送离线消息。
     * 适用于用户暂时离开订阅页面但未退出登录的场景。
     *
     * @param auth Spring Security 认证信息
     * @return 当前会话状态信息
     */
    @PostMapping("/disconnect")
    public ApiResponse<Map<String, Object>> disconnect(Authentication auth) {
        String username = auth.getName();
        log.info("[API] POST /disconnect: username={}", username);
        subscribeService.disconnectMqtt(username);
        return ApiResponse.ok("MQTT已断开（SSE保持）", subscribeService.getSessionStatus(username));
    }

    /**
     * 查询当前会话连接状态。
     *
     * <p>返回 SSE 连接状态、MQTT 连接状态及已订阅主题列表。
     *
     * @param auth Spring Security 认证信息
     * @return 会话状态信息
     */
    @GetMapping("/session-status")
    public ApiResponse<Map<String, Object>> sessionStatus(Authentication auth) {
        return ApiResponse.ok(subscribeService.getSessionStatus(auth.getName()));
    }

    /**
     * 完全关闭订阅会话（退出登录 / 关闭标签页时调用）。
     *
     * <p>取消所有订阅 + 断开 MQTT + 关闭 SSE，彻底清理该用户的订阅资源。
     * 与 disconnect 的区别：close 会清除订阅记忆，下次进入需重新订阅。
     *
     * @param auth Spring Security 认证信息
     */
    @PostMapping("/close")
    public ApiResponse<Void> close(Authentication auth) {
        String username = auth.getName();
        log.info("[API] POST /close: username={}", username);
        subscribeService.closeAll(username);
        return ApiResponse.ok("订阅会话已关闭", null);
    }

    /**
     * 创建发送错误事件的 SSE 发射器
     *
     * <p>当 SSE 连接建立失败时使用。发送一个名为 "error" 的 SSE 事件，
     * 事件数据包含 message（错误描述）和 reconnectable（是否可重连）两个字段，
     * 前端据此判断是否自动重连。发送完成后立即关闭发射器。
     *
     * @param message 错误描述信息
     * @return 已发送错误事件并关闭的 SseEmitter
     */
    private SseEmitter createErrorEmitter(String message) {
        SseEmitter emitter = new SseEmitter(3000L); // 3秒超时，仅用于发送错误事件
        try {
            emitter.send(SseEmitter.event()
                    .name("error") // SSE 事件名，前端通过 EventSource.addEventListener("error", ...) 监听
                    .data(Map.of("message", message, "reconnectable", true))); // reconnectable=true 告知前端可自动重连
            emitter.complete(); // 错误事件发送完毕，主动关闭发射器
        } catch (IOException e) {
            emitter.completeWithError(e);
        }
        return emitter;
    }
}
