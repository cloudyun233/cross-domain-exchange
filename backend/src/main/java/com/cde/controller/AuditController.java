package com.cde.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cde.dto.ApiResponse;
import com.cde.entity.SysAuditLog;
import com.cde.service.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/audit-logs")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AuditController {
    private final AuditService auditService;

    @GetMapping
    public ApiResponse<Page<SysAuditLog>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String clientId,
            @RequestParam(required = false) String actionType) {
        return ApiResponse.ok(auditService.queryLogs(page, size, clientId, actionType));
    }
}
