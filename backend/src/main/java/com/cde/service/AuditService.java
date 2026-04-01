package com.cde.service;

import com.cde.entity.SysAuditLog;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.Map;

/**
 * 审计日志服务接口 (论文3.3.4: 全链路操作审计)
 */
public interface AuditService {

    /** 记录审计日志 */
    void log(String clientId, String actionType, String detail, String ipAddress);

    /** 从EMQX Webhook记录审计事件 */
    void recordFromWebhook(Map<String, Object> event);

    /** 分页查询审计日志 */
    Page<SysAuditLog> queryLogs(int page, int size, String clientId, String actionType);
}
