package com.cde.controller;

import com.cde.dto.ApiResponse;
import com.cde.dto.LoginRequest;
import com.cde.dto.LoginResponse;
import com.cde.entity.SysUser;
import com.cde.security.JwtUtil;
import com.cde.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final JwtUtil jwtUtil;

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@RequestBody LoginRequest request) {
        try {
            LoginResponse response = authService.login(request);
            return ApiResponse.ok("登录成功", response);
        } catch (Exception e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    @PostMapping("/refresh")
    public ApiResponse<LoginResponse> refresh(@RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.replace("Bearer ", "");
            LoginResponse response = authService.refreshToken(token);
            return ApiResponse.ok("令牌刷新成功", response);
        } catch (Exception e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    @GetMapping("/me")
    public ApiResponse<SysUser> getCurrentUser(@RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.replace("Bearer ", "");
            String username = jwtUtil.getUsernameFromToken(token);
            SysUser user = authService.getUserByUsername(username);
            return ApiResponse.ok("获取用户信息成功", user);
        } catch (Exception e) {
            return ApiResponse.fail(e.getMessage());
        }
    }
}
