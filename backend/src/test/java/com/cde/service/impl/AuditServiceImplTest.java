package com.cde.service.impl;

import com.cde.entity.SysAuditLog;
import com.cde.mapper.SysAuditLogMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditServiceImplTest {

    @Mock
    private SysAuditLogMapper auditLogMapper;

    @InjectMocks
    private AuditServiceImpl auditService;

    @Test
    void exportLogsAsPdfCreatesPdfWithAuditRecords() {
        SysAuditLog log = new SysAuditLog();
        log.setId(1L);
        log.setClientId("client-a");
        log.setActionType("acl_deny");
        log.setDetail("非法操作拦截");
        log.setIpAddress("127.0.0.1");
        log.setActionTime(LocalDateTime.of(2026, 4, 25, 10, 30));
        when(auditLogMapper.selectList(any())).thenReturn(List.of(log));

        byte[] pdf = auditService.exportLogsAsPdf("client-a", "acl_deny");

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 5, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
    }
}
