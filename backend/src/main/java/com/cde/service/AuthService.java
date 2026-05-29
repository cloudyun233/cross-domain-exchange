package com.cde.service;

import com.cde.dto.LoginRequest;
import com.cde.dto.LoginResponse;
import com.cde.entity.SysUser;

public interface AuthService {

    LoginResponse login(LoginRequest request);

    SysUser getUserByUsername(String username);

    LoginResponse getCurrentUserProfile(String username, String token);
}
