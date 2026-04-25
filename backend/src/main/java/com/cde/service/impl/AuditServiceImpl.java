package com.cde.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cde.entity.SysAuditLog;
import com.cde.mapper.SysAuditLogMapper;
import com.cde.service.AuditService;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * 审计日志服务实现 (论文3.3.4: 统一审计入口)
 * 接收来源: 后端业务操作 + EMQX Webhook事件
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditServiceImpl implements AuditService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

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
     *           client.authorize, session.subscribed, message.delivered
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
            case "session.subscribed":
                actionType = "subscribe";
                detail = String.format("订阅主题 %s, QoS=%s",
                        event.getOrDefault("topic", ""),
                        event.getOrDefault("qos", "0"));
                break;
            case "message.delivered":
                actionType = "deliver";
                detail = String.format("消息投递成功, 主题=%s, QoS=%s",
                        event.getOrDefault("topic", ""),
                        event.getOrDefault("qos", "0"));
                break;
            default:
                log.debug("[审计] 收到未识别的Webhook事件: {}, 已忽略", eventType);
                return;
        }

        log(clientId, actionType, detail, ipAddress);
    }

    @Override
    public Page<SysAuditLog> queryLogs(int page, int size, String clientId, String actionType) {
        return auditLogMapper.selectPage(new Page<>(page, size), buildLogQuery(clientId, actionType));
    }

    @Override
    public byte[] exportLogsAsPdf(String clientId, String actionType) {
        List<SysAuditLog> logs = auditLogMapper.selectList(buildLogQuery(clientId, actionType));
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4.rotate(), 24, 24, 28, 28);
        try {
            PdfWriter.getInstance(document, outputStream);
            document.open();
            Font titleFont = createFont(16, Font.BOLD);
            Font normalFont = createFont(9, Font.NORMAL);
            Font headerFont = createFont(9, Font.BOLD);

            Paragraph title = new Paragraph("审计日志导出", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingAfter(8);
            document.add(title);

            Paragraph filters = new Paragraph(String.format("过滤条件：客户端ID=%s，操作类型=%s，导出时间=%s",
                    StringUtils.hasText(clientId) ? clientId : "全部",
                    StringUtils.hasText(actionType) ? actionType : "全部",
                    DATE_TIME_FORMATTER.format(LocalDateTime.now())), normalFont);
            filters.setSpacingAfter(12);
            document.add(filters);

            PdfPTable table = new PdfPTable(new float[]{1.1f, 2.2f, 2f, 1.6f, 1.6f, 5.5f});
            table.setWidthPercentage(100);
            addHeader(table, headerFont, "ID", "时间", "客户端", "操作类型", "IP地址", "详情");
            for (SysAuditLog logEntry : logs) {
                addCell(table, normalFont, String.valueOf(logEntry.getId()));
                addCell(table, normalFont, formatTime(logEntry.getActionTime()));
                addCell(table, normalFont, safeText(logEntry.getClientId()));
                addCell(table, normalFont, safeText(logEntry.getActionType()));
                addCell(table, normalFont, safeText(logEntry.getIpAddress()));
                addCell(table, normalFont, safeText(logEntry.getDetail()));
            }
            document.add(table);
        } catch (DocumentException e) {
            throw new IllegalStateException("审计日志PDF导出失败", e);
        } finally {
            document.close();
        }
        return outputStream.toByteArray();
    }

    private LambdaQueryWrapper<SysAuditLog> buildLogQuery(String clientId, String actionType) {
        LambdaQueryWrapper<SysAuditLog> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(clientId)) {
            wrapper.eq(SysAuditLog::getClientId, clientId);
        }
        if (StringUtils.hasText(actionType)) {
            wrapper.eq(SysAuditLog::getActionType, actionType);
        }
        wrapper.orderByDesc(SysAuditLog::getActionTime);
        return wrapper;
    }

    private Font createFont(int size, int style) {
        return FontFactory.getFont("STSong-Light", "UniGB-UCS2-H", BaseFont.NOT_EMBEDDED, size, style);
    }

    private void addHeader(PdfPTable table, Font font, String... headers) {
        for (String header : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(header, font));
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            cell.setBackgroundColor(new Color(238, 242, 247));
            cell.setPadding(6);
            table.addCell(cell);
        }
    }

    private void addCell(PdfPTable table, Font font, String value) {
        PdfPCell cell = new PdfPCell(new Phrase(value, font));
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setPadding(5);
        table.addCell(cell);
    }

    private String formatTime(LocalDateTime time) {
        return time == null ? "-" : DATE_TIME_FORMATTER.format(time);
    }

    private String safeText(String value) {
        return StringUtils.hasText(value) ? value : "-";
    }
}
