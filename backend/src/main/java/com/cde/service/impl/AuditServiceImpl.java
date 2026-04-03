package com.cde.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cde.entity.SysAuditLog;
import com.cde.mapper.SysAuditLogMapper;
import com.cde.service.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 审计日志服务实现 (论文3.3.4: 统一审计入口)
 * 接收来源: 后端业务操作 + EMQX Webhook事件
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditServiceImpl implements AuditService {

    private final SysAuditLogMapper auditLogMapper;

    @Override
    public void log(String clientId, String actionType, String detail, String ipAddress) {
        SysAuditLog logEntry = new SysAuditLog();
        logEntry.setClientId(clientId);
        logEntry.setActionType(actionType);
        logEntry.setDetail(detail);
        logEntry.setIpAddress(ipAddress != null ? ipAddress : "0.0.0.0");
        logEntry.setActionTime(LocalDateTime.now());
        auditLogMapper.insert(logEntry);

        if (actionType.contains("deny") || actionType.contains("fail")) {
            log.warn("[审计警告] 非法操作拦截: client={}, action={}, detail={}", clientId, actionType, detail);
        } else {
            log.info("[审计记录] client={}, action={}, detail={}", clientId, actionType, detail);
        }
    }

    /**
     * 从EMQX Webhook记录审计事件
     * 支持事件: client.connected, client.disconnected, message.publish,
     *           client.authorize, client.subscribe, message.delivered
     */
    @Override
    public void recordFromWebhook(Map<String, Object> event) {
        String eventType = String.valueOf(event.getOrDefault("event", "unknown"));
        String clientId = String.valueOf(event.getOrDefault("clientid", "unknown"));
        String ipAddress = String.valueOf(event.getOrDefault("peername", "0.0.0.0"));
        // 提取IP部分 (peername格式: "ip:port")
        if (ipAddress.contains(":")) {
            ipAddress = ipAddress.substring(0, ipAddress.lastIndexOf(':'));
        }

        String actionType;
        String detail;

        switch (eventType) {
            case "client.connected":
                actionType = "connect";
                detail = String.format("客户端连接成功, 协议=%s",
                        event.getOrDefault("proto_name", "MQTT"));
                break;
            case "client.disconnected":
                actionType = "disconnect";
                detail = String.format("客户端断开连接, 原因=%s",
                        event.getOrDefault("reason", "normal"));
                break;
            case "message.publish":
                actionType = "publish";
                String topic = String.valueOf(event.getOrDefault("topic", ""));
                detail = String.format("发布消息到主题 %s, QoS=%s",
                        topic, event.getOrDefault("qos", "0"));
                break;
            case "client.authorize":
                String result = String.valueOf(event.getOrDefault("result", ""));
                if ("deny".equals(result)) {
                    actionType = "acl_deny";
                    detail = String.format("非法操作拦截：用户 %s 无%s权限, 主题=%s",
                            clientId,
                            event.getOrDefault("action", "unknown"),
                            event.getOrDefault("topic", ""));
                } else {
                    actionType = "acl_allow";
                    detail = String.format("ACL校验通过: action=%s, topic=%s",
                            event.getOrDefault("action", ""),
                            event.getOrDefault("topic", ""));
                }
                break;
            case "client.subscribe":
                actionType = "subscribe";
                detail = String.format("订阅主题, topic=%s",
                        event.getOrDefault("topic", ""));
                break;
            case "message.delivered":
                actionType = "deliver";
                detail = String.format("消息投递成功, topic=%s",
                        event.getOrDefault("topic", ""));
                break;
            default:
                actionType = eventType;
                detail = event.toString();
        }

        log(clientId, actionType, detail, ipAddress);
    }

    @Override
    public Page<SysAuditLog> queryLogs(int page, int size, String clientId, String actionType) {
        LambdaQueryWrapper<SysAuditLog> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(clientId)) {
            wrapper.eq(SysAuditLog::getClientId, clientId);
        }
        if (StringUtils.hasText(actionType)) {
            wrapper.eq(SysAuditLog::getActionType, actionType);
        }
        wrapper.orderByDesc(SysAuditLog::getActionTime);
        return auditLogMapper.selectPage(new Page<>(page, size), wrapper);
    }
}
