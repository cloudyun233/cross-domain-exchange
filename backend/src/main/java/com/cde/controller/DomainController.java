package com.cde.controller;

import com.cde.dto.ApiResponse;
import com.cde.dto.DomainTreeNode;
import com.cde.entity.SysDomain;
import com.cde.service.DomainService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 安全域管理 REST API
 *
 * <p>提供安全域的增删改查及树形结构查询。查询接口对所有已认证用户开放，
 * 增删改操作仅限管理员。树形接口将域数据组装为前端可视化所需的层级结构。
 * <ul>
 *   <li>GET    /api/domains      — 查询全部安全域（平铺列表）</li>
 *   <li>GET    /api/domains/tree — 查询安全域树形结构（供前端树形控件使用）</li>
 *   <li>GET    /api/domains/{id} — 查询单个安全域</li>
 *   <li>POST   /api/domains      — 创建安全域（仅管理员）</li>
 *   <li>PUT    /api/domains/{id} — 更新安全域（仅管理员）</li>
 *   <li>DELETE /api/domains/{id} — 删除安全域（仅管理员）</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/domains")
@RequiredArgsConstructor
public class DomainController {

    private final DomainService domainService;

    /**
     * 查询全部安全域（平铺列表）
     *
     * @return 所有安全域列表
     */
    @GetMapping
    public ApiResponse<List<SysDomain>> list() {
        return ApiResponse.ok(domainService.listAll());
    }

    /**
     * 查询安全域树形结构
     *
     * <p>将安全域数据组装为树形层级结构，供前端树形控件展示域的父子关系。
     *
     * @return 树形结构节点列表
     */
    @GetMapping("/tree")
    public ApiResponse<List<DomainTreeNode>> tree() {
        return ApiResponse.ok(domainService.buildDomainTree());
    }

    /**
     * 查询单个安全域
     *
     * @param id 安全域 ID
     * @return 安全域详情
     */
    @GetMapping("/{id}")
    public ApiResponse<SysDomain> get(@PathVariable Long id) {
        return ApiResponse.ok(domainService.getById(id));
    }

    /**
     * 创建安全域（仅管理员）
     *
     * @param domain 安全域实体
     * @return 创建成功的安全域
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<SysDomain> create(@RequestBody SysDomain domain) {
        return ApiResponse.ok("安全域创建成功", domainService.create(domain));
    }

    /**
     * 更新安全域（仅管理员）
     *
     * @param id     安全域 ID
     * @param domain 更新后的安全域实体
     * @return 更新后的安全域
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<SysDomain> update(@PathVariable Long id, @RequestBody SysDomain domain) {
        return ApiResponse.ok("安全域更新成功", domainService.update(id, domain));
    }

    /**
     * 删除安全域（仅管理员）
     *
     * @param id 安全域 ID
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        domainService.delete(id);
        return ApiResponse.ok("安全域删除成功", null);
    }
}
