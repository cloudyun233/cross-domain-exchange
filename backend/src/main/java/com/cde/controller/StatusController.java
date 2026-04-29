package com.cde.controller;

import com.cde.dto.StatusResponse;
import com.cde.mqtt.EmqxApiClient;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 健康检查 REST API
 *
 * <p>提供后端服务和 EMQX Broker 的健康状态查询，所有接口公开访问（无需鉴权）。
 * 前端通过此接口判断连接状态并显示状态指示器。
 * <ul>
 *   <li>GET /api/status/backend — 后端服务健康检查</li>
 *   <li>GET /api/status/emqx    — EMQX Broker API 就绪检查</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/status")
@RequiredArgsConstructor
public class StatusController {

    private final EmqxApiClient emqxApiClient;

    /**
     * 后端服务健康检查
     *
     * <p>若能正常响应则说明后端服务运行正常，始终返回 "ok"。
     *
     * @return 状态响应，status 为 "ok"
     */
    @GetMapping("/backend")
    public StatusResponse status() {
        return new StatusResponse("ok");
    }

    /**
     * EMQX Broker API 就绪检查
     *
     * <p>通过调用 EMQX API 判断 Broker 是否在线，异常时返回 "offline"。
     *
     * @return 状态响应，status 为 "online" 或 "offline"
     */
    @GetMapping("/emqx")
    public StatusResponse emqxStatus() {
        try {
            boolean online = emqxApiClient.isApiReady();
            return new StatusResponse(online ? "online" : "offline");
        } catch (Exception e) {
            return new StatusResponse("offline");
        }
    }
}
