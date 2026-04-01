package com.cde.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 审计日志表 (论文表4-10)
 */
@Data
@TableName("sys_audit_log")
public class SysAuditLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 客户端ID */
    private String clientId;

    /** 操作类型 (connect/disconnect/publish/subscribe/acl_deny/auth_success/auth_fail) */
    private String actionType;

    /** 详细报文或原因 */
    private String detail;

    /** 客户端IP */
    private String ipAddress;

    /** 记录时间 */
    private LocalDateTime createTime;
}
