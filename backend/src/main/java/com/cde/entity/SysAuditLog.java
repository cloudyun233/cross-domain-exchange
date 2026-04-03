package com.cde.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 审计日志表
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
