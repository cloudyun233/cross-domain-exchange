package com.cde.controller;

import com.cde.dto.ApiResponse;
import com.cde.entity.SysTopicAcl;
import com.cde.service.AclService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/**
 * ACL 规则管理 REST API
 *
 * <p>提供 MQTT 主题访问控制（ACL）规则的增删改查，所有接口仅限管理员访问。
 * 每次增删改操作均实时同步至 EMQX Broker，确保权限变更即时生效。
 * <ul>
 *   <li>GET    /api/acl-rules                — 查询全部 ACL 规则</li>
 *   <li>GET    /api/acl-rules/username/{name} — 按用户名查询 ACL 规则</li>
 *   <li>POST   /api/acl-rules                — 创建 ACL 规则（同步至 Broker）</li>
 *   <li>PUT    /api/acl-rules/{id}           — 更新 ACL 规则（同步至 Broker）</li>
 *   <li>DELETE /api/acl-rules/{id}           — 删除 ACL 规则（同步至 Broker）</li>
 *   <li>POST   /api/acl-rules/sync           — 全量同步 ACL 规则至 Broker</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/acl-rules")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AclController {
    private final AclService aclService;

    /**
     * 查询全部 ACL 规则
     *
     * @return 所有 ACL 规则列表
     */
    @GetMapping
    public ApiResponse<List<SysTopicAcl>> list() {
        return ApiResponse.ok(aclService.listAll());
    }

    /**
     * 按用户名查询 ACL 规则
     *
     * @param username MQTT 客户端用户名
     * @return 该用户名下的 ACL 规则列表
     */
    @GetMapping("/username/{username}")
    public ApiResponse<List<SysTopicAcl>> listByUsername(@PathVariable String username) {
        return ApiResponse.ok(aclService.listByUsername(username));
    }

    /**
     * 创建 ACL 规则
     *
     * <p>创建新规则后自动同步至 EMQX Broker，使权限立即生效。
     *
     * @param acl ACL 规则实体
     * @return 创建成功的 ACL 规则
     */
    @PostMapping
    public ApiResponse<SysTopicAcl> create(@RequestBody SysTopicAcl acl) {
        return ApiResponse.ok("ACL规则创建成功（已同步到Broker）", aclService.create(acl));
    }

    /**
     * 更新 ACL 规则
     *
     * <p>更新规则后自动同步至 EMQX Broker，覆盖 Broker 侧的旧规则。
     *
     * @param id  规则 ID
     * @param acl 更新后的 ACL 规则实体
     * @return 更新后的 ACL 规则
     */
    @PutMapping("/{id}")
    public ApiResponse<SysTopicAcl> update(@PathVariable Long id, @RequestBody SysTopicAcl acl) {
        return ApiResponse.ok("ACL规则更新成功（已同步到Broker）", aclService.update(id, acl));
    }

    /**
     * 删除 ACL 规则
     *
     * <p>删除规则后自动同步至 EMQX Broker，撤销对应的访问权限。
     *
     * @param id 规则 ID
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        aclService.delete(id);
        return ApiResponse.ok("ACL规则删除成功（已同步到Broker）", null);
    }

    /**
     * 全量同步 ACL 规则至 EMQX Broker
     *
     * <p>将数据库中所有 ACL 规则重新推送至 EMQX，适用于 Broker 侧数据与数据库不一致时手动修复。
     */
    @PostMapping("/sync")
    public ApiResponse<Void> syncToEmqx() {
        aclService.syncToEmqx();
        return ApiResponse.ok("ACL规则全量同步到Broker成功", null);
    }
}
