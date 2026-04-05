package com.cde.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cde.dto.LoginRequest;
import com.cde.dto.LoginResponse;
import com.cde.entity.SysDomain;
import com.cde.entity.SysUser;
import com.cde.mapper.SysDomainMapper;
import com.cde.mapper.SysUserMapper;
import com.cde.security.JwtUtil;
import com.cde.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 鉴权服务实现 (论文4.5.3: AuthServiceImpl)
 * 核心职责: JWT签发、用户信息管理
 * ACL校验由EMQX全权负责，后端不重复验证
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final SysUserMapper userMapper;
    private final SysDomainMapper domainMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @Override
    public LoginResponse login(LoginRequest request) {
        SysUser user = userMapper.selectOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, request.getUsername()));

        if (user == null) {
            throw new BadCredentialsException("用户不存在: " + request.getUsername());
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BadCredentialsException("密码错误");
        }

        SysDomain domain = domainMapper.selectById(user.getDomainId());
        String domainCode = domain != null ? domain.getDomainCode() : "unknown";
        String domainName = domain != null ? domain.getDomainName() : "未知域";

        String token = jwtUtil.generateToken(user.getUsername(), user.getClientId(), domainCode, user.getRoleType());

        return LoginResponse.builder()
                .token(token)
                .expires(jwtUtil.getExpirationMs() / 1000)
                .username(user.getUsername())
                .roleType(user.getRoleType())
                .domainCode(domainCode)
                .domainName(domainName)
                .clientId(user.getClientId())
                .build();
    }

    @Override
    public LoginResponse refreshToken(String oldToken) {
        if (!jwtUtil.validateToken(oldToken)) {
            throw new BadCredentialsException("令牌无效或已过期");
        }
        String username = jwtUtil.getUsernameFromToken(oldToken);
        String domainCode = jwtUtil.getDomainCodeFromToken(oldToken);
        String roleType = jwtUtil.getRoleTypeFromToken(oldToken);

        SysUser user = userMapper.selectOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username));

        String clientId = user != null ? user.getClientId() : jwtUtil.getClientIdFromToken(oldToken);
        String newToken = jwtUtil.generateToken(username, clientId, domainCode, roleType);
        return LoginResponse.builder()
                .token(newToken)
                .expires(jwtUtil.getExpirationMs() / 1000)
                .username(username)
                .roleType(roleType)
                .domainCode(domainCode)
                .clientId(clientId)
                .build();
    }

    @Override
    public SysUser getUserByUsername(String username) {
        return userMapper.selectOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username));
    }
}
