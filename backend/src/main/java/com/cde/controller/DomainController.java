package com.cde.controller;

import com.cde.dto.ApiResponse;
import com.cde.entity.SysDomain;
import com.cde.service.DomainService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/domains")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class DomainController {
    private final DomainService domainService;

    @GetMapping
    public ApiResponse<List<SysDomain>> list() {
        return ApiResponse.ok(domainService.listAll());
    }

    @GetMapping("/{id}")
    public ApiResponse<SysDomain> get(@PathVariable Long id) {
        return ApiResponse.ok(domainService.getById(id));
    }

    @PostMapping
    public ApiResponse<SysDomain> create(@RequestBody SysDomain domain) {
        return ApiResponse.ok("安全域创建成功", domainService.create(domain));
    }

    @PutMapping("/{id}")
    public ApiResponse<SysDomain> update(@PathVariable Long id, @RequestBody SysDomain domain) {
        return ApiResponse.ok("安全域更新成功", domainService.update(id, domain));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        domainService.delete(id);
        return ApiResponse.ok("安全域删除成功", null);
    }
}
