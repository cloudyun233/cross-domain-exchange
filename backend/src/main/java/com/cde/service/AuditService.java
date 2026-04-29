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

    /**
     * 从EMQX Webhook记录审计事件
     *
     * <p>事件类型映射：client.connected→connect, client.disconnected→disconnect,
     * message.publish→publish, client.authorize→acl_allow/acl_deny,
     * session.subscribed→subscribe, message.delivered→deliver</p>
     *
     * @param event EMQX Webhook推送的原始事件Map，包含event、clientid、peername等字段
     */
    void recordFromWebhook(Map<String, Object> event);

    /** 分页查询审计日志 */
    Page<SysAuditLog> queryLogs(int page, int size, String clientId, String actionType);

    /**
     * 导出审计日志为PDF
     *
     * <p>基于OpenPDF生成横向A4格式PDF，包含标题、过滤条件摘要和六列表格
     * （ID、时间、客户端、操作类型、IP地址、详情），使用STSong-Light字体支持中文。</p>
     *
     * @param clientId   客户端ID过滤条件，为空则不过滤
     * @param actionType 操作类型过滤条件，为空则不过滤
     * @return PDF文件的字节数组
     */
    byte[] exportLogsAsPdf(String clientId, String actionType);
}
