package com.cde.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cde.dto.ApiResponse;
import com.cde.entity.SysUser;
import com.cde.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/clients")
@RequiredArgsConstructor
public class ClientController {
    private final SysUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    @GetMapping
    public ApiResponse<List<SysUser>> list() {
        List<SysUser> users = userMapper.selectList(null);
        users.forEach(u -> u.setPasswordHash("***")); // 脱敏
        return ApiResponse.ok(users);
    }

    @GetMapping("/{id}")
    public ApiResponse<SysUser> get(@PathVariable Long id) {
        SysUser user = userMapper.selectById(id);
        if (user != null) user.setPasswordHash("***");
        return ApiResponse.ok(user);
    }

    @PostMapping
    public ApiResponse<SysUser> create(@RequestBody SysUser user) {
        user.setPasswordHash(passwordEncoder.encode(user.getPasswordHash()));
        user.setCreateTime(LocalDateTime.now());
        userMapper.insert(user);
        user.setPasswordHash("***");
        return ApiResponse.ok("客户端创建成功", user);
    }

    @PutMapping("/{id}")
    public ApiResponse<SysUser> update(@PathVariable Long id, @RequestBody SysUser user) {
        user.setId(id);
        if (user.getPasswordHash() != null && !user.getPasswordHash().equals("***")) {
            user.setPasswordHash(passwordEncoder.encode(user.getPasswordHash()));
        } else {
            user.setPasswordHash(null); // 不更新密码
        }
        userMapper.updateById(user);
        SysUser updated = userMapper.selectById(id);
        updated.setPasswordHash("***");
        return ApiResponse.ok("客户端更新成功", updated);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        userMapper.deleteById(id);
        return ApiResponse.ok("客户端删除成功", null);
    }
}
