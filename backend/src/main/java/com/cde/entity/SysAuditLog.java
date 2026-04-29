package com.cde.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 审计日志表。
 * <p>
 * 作为统一记录表，同时承载两类事件来源：
 * <ol>
 *   <li>后端操作：用户通过 Web API 执行的管理操作</li>
 *   <li>EMQX Webhook 事件：Broker 推送的客户端连接、消息发布、订阅等 MQTT 事件</li>
 * </ol>
 * <p>
 * actionType 枚举值及来源：
 * <ul>
 *   <li>connect — 客户端连接成功（EMQX webhook: client.connected）</li>
 *   <li>publish — 消息发布事件（EMQX webhook: message.publish）</li>
 *   <li>subscribe — 主题订阅事件（EMQX webhook: client.subscribed）</li>
 *   <li>acl_deny — ACL 权限拒绝事件（EMQX webhook: client_acl_deny 或后端拦截）</li>
 * </ul>
 */
@Data
@TableName("sys_audit_log")
public class SysAuditLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 客户端ID */
    private String clientId;

    /** 操作类型 (connect/publish/subscribe/acl_deny) */
    private String actionType;

    /** 详细信息 */
    private String detail;

    /** 客户端IP */
    private String ipAddress;

    /** 操作发生时间 */
    private LocalDateTime actionTime;
}
