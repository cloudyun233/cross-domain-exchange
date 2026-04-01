package com.cde.controller;

import com.cde.dto.ApiResponse;
import com.cde.dto.LoginRequest;
import com.cde.dto.LoginResponse;
import com.cde.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

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
}
