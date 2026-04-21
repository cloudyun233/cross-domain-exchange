package com.cde.controller;

import com.cde.service.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

/**
 * EMQX Webhook事件接收 (统一审计入口)
 * 接收: client.connected, client.disconnected, message.publish,
 *       client.authorize, session.subscribed, message.delivered
 */
@Slf4j
@RestController
@RequestMapping("/api/webhook")
@RequiredArgsConstructor
public class WebhookController {

    private final AuditService auditService;

    @PostMapping("/emqx")
    public Map<String, Object> handleEmqxWebhook(@RequestBody Map<String, Object> event) {
        log.debug("收到EMQX Webhook事件: {}", event);
        auditService.recordFromWebhook(event);
        return Map.of("result", "ok");
    }
}
