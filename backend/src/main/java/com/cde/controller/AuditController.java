package com.cde.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cde.dto.ApiResponse;
import com.cde.entity.SysAuditLog;
import com.cde.service.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 审计日志 REST API
 *
 * <p>提供审计日志的分页查询和 PDF 导出功能，所有接口仅限管理员访问。
 * <ul>
 *   <li>GET /api/audit-logs             — 分页查询审计日志，支持按客户端 ID 和操作类型筛选</li>
 *   <li>GET /api/audit-logs/export/pdf  — 导出审计日志为 PDF 文件（基于 OpenPDF 生成）</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/audit-logs")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AuditController {
    private static final DateTimeFormatter FILE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final AuditService auditService;

    /**
     * 分页查询审计日志
     *
     * <p>支持按客户端 ID 和操作类型进行条件筛选，返回分页结果。
     *
     * @param page       页码，默认第 1 页
     * @param size       每页条数，默认 20 条
     * @param clientId   客户端 ID（可选筛选条件）
     * @param actionType 操作类型（可选筛选条件）
     * @return 审计日志分页数据
     */
    @GetMapping
    public ApiResponse<Page<SysAuditLog>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String clientId,
            @RequestParam(required = false) String actionType) {
        return ApiResponse.ok(auditService.queryLogs(page, size, clientId, actionType));
    }

    /**
     * 导出审计日志为 PDF
     *
     * <p>基于 OpenPDF 生成 PDF 文件，支持按客户端 ID 和操作类型筛选。
     * 返回的 PDF 以附件形式下载，文件名包含导出时间戳。
     *
     * @param clientId   客户端 ID（可选筛选条件）
     * @param actionType 操作类型（可选筛选条件）
     * @return PDF 文件字节流，Content-Type 为 application/pdf
     */
    @GetMapping(value = "/export/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> exportPdf(
            @RequestParam(required = false) String clientId,
            @RequestParam(required = false) String actionType) {
        byte[] pdf = auditService.exportLogsAsPdf(clientId, actionType);
        String filename = "audit-logs-" + FILE_TIME_FORMATTER.format(LocalDateTime.now()) + ".pdf";
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(pdf.length)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(filename, StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .body(pdf);
    }
}
