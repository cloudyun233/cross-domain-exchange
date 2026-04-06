package com.cde.controller;

import com.cde.dto.ApiResponse;
import com.cde.entity.SysUser;
import com.cde.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/clients")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class ClientController {

    private final SysUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    @GetMapping
    public ApiResponse<List<SysUser>> list() {
        List<SysUser> users = userMapper.selectList(null);
        users.forEach(user -> user.setPasswordHash("***"));
        return ApiResponse.ok(users);
    }

    @GetMapping("/{id}")
    public ApiResponse<SysUser> get(@PathVariable Long id) {
        SysUser user = userMapper.selectById(id);
        if (user != null) {
            user.setPasswordHash("***");
        }
        return ApiResponse.ok(user);
    }

    @PostMapping
    public ApiResponse<SysUser> create(@RequestBody SysUser user) {
        user.setPasswordHash(passwordEncoder.encode(user.getPasswordHash()));
        user.setClientId(user.getUsername() + "_001");
        userMapper.insert(user);
        user.setPasswordHash("***");
        return ApiResponse.ok("用户创建成功", user);
    }

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

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        userMapper.deleteById(id);
        return ApiResponse.ok("用户删除成功", null);
    }
}
