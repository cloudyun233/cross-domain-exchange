package com.cde.controller;

import com.cde.dto.ApiResponse;
import com.cde.entity.SysTopicAcl;
import com.cde.service.AclService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/acl-rules")
@RequiredArgsConstructor
public class AclController {
    private final AclService aclService;

    @GetMapping
    public ApiResponse<List<SysTopicAcl>> list() {
        return ApiResponse.ok(aclService.listAll());
    }

    @GetMapping("/client/{clientId}")
    public ApiResponse<List<SysTopicAcl>> listByClient(@PathVariable String clientId) {
        return ApiResponse.ok(aclService.listByClientId(clientId));
    }

    @PostMapping
    public ApiResponse<SysTopicAcl> create(@RequestBody SysTopicAcl acl) {
        return ApiResponse.ok("ACL规则创建成功（已同步到Broker）", aclService.create(acl));
    }

    @PutMapping("/{id}")
    public ApiResponse<SysTopicAcl> update(@PathVariable Long id, @RequestBody SysTopicAcl acl) {
        return ApiResponse.ok("ACL规则更新成功（已同步到Broker）", aclService.update(id, acl));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        aclService.delete(id);
        return ApiResponse.ok("ACL规则删除成功（已同步到Broker）", null);
    }

    @PostMapping("/sync")
    public ApiResponse<Void> syncToEmqx() {
        aclService.syncToEmqx();
        return ApiResponse.ok("ACL规则全量同步到Broker成功", null);
    }
}
