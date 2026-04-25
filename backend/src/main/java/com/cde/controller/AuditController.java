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

@RestController
@RequestMapping("/api/audit-logs")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AuditController {
    private static final DateTimeFormatter FILE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final AuditService auditService;

    @GetMapping
    public ApiResponse<Page<SysAuditLog>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String clientId,
            @RequestParam(required = false) String actionType) {
        return ApiResponse.ok(auditService.queryLogs(page, size, clientId, actionType));
    }

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
