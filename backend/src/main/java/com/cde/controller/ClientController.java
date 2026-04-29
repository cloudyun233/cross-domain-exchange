package com.cde.controller;

import com.cde.dto.ApiResponse;
import com.cde.entity.SysUser;
import com.cde.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 用户/客户端管理 REST API
 *
 * <p>提供 MQTT 客户端用户的增删改查，所有接口仅限管理员访问。
 * 密码使用 BCrypt 加密存储；创建用户时自动按 "{username}_001" 模式生成 clientId。
 * 所有响应中的 passwordHash 字段均脱敏为 "***"，防止密码泄露。
 * <ul>
 *   <li>GET    /api/clients      — 查询全部用户</li>
 *   <li>GET    /api/clients/{id} — 查询单个用户</li>
 *   <li>POST   /api/clients      — 创建用户（BCrypt 加密 + 自动生成 clientId）</li>
 *   <li>PUT    /api/clients/{id} — 更新用户（仅当密码非脱敏值时才重新加密）</li>
 *   <li>DELETE /api/clients/{id} — 删除用户</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/clients")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class ClientController {

    private final SysUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    /**
     * 查询全部用户
     *
     * <p>返回所有用户列表，passwordHash 字段统一脱敏为 "***"。
     *
     * @return 用户列表（密码已脱敏）
     */
    @GetMapping
    public ApiResponse<List<SysUser>> list() {
        List<SysUser> users = userMapper.selectList(null);
        users.forEach(user -> user.setPasswordHash("***"));
        return ApiResponse.ok(users);
    }

    /**
     * 查询单个用户
     *
     * @param id 用户 ID
     * @return 用户详情（密码已脱敏），不存在时返回 null
     */
    @GetMapping("/{id}")
    public ApiResponse<SysUser> get(@PathVariable Long id) {
        SysUser user = userMapper.selectById(id);
        if (user != null) {
            user.setPasswordHash("***");
        }
        return ApiResponse.ok(user);
    }

    /**
     * 创建用户
     *
     * <p>使用 BCrypt 对明文密码加密存储，并按 "{username}_001" 模式自动生成 clientId。
     * 创建成功后 passwordHash 脱敏返回。
     *
     * @param user 用户实体（passwordHash 字段传入明文密码）
     * @return 创建成功的用户信息（密码已脱敏）
     */
    @PostMapping
    public ApiResponse<SysUser> create(@RequestBody SysUser user) {
        user.setPasswordHash(passwordEncoder.encode(user.getPasswordHash()));
        user.setClientId(user.getUsername() + "_001");
        userMapper.insert(user);
        user.setPasswordHash("***");
        return ApiResponse.ok("用户创建成功", user);
    }

    /**
     * 更新用户
     *
     * <p>若请求中 passwordHash 为非脱敏值（非 "***"），则使用 BCrypt 重新加密；
     * 若为脱敏值或 null，则不更新密码字段，避免覆盖原有密码。
     *
     * @param id   用户 ID
     * @param user 更新后的用户实体
     * @return 更新后的用户信息（密码已脱敏）
     */
    @PutMapping("/{id}")
    public ApiResponse<SysUser> update(@PathVariable Long id, @RequestBody SysUser user) {
        user.setId(id);
        if (user.getPasswordHash() != null && !user.getPasswordHash().equals("***")) {
            user.setPasswordHash(passwordEncoder.encode(user.getPasswordHash()));
        } else {
            user.setPasswordHash(null);
        }
        userMapper.updateById(user);
        SysUser updated = userMapper.selectById(id);
        updated.setPasswordHash("***");
        return ApiResponse.ok("用户更新成功", updated);
    }

    /**
     * 删除用户
     *
     * @param id 用户 ID
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        userMapper.deleteById(id);
        return ApiResponse.ok("用户删除成功", null);
    }
}
