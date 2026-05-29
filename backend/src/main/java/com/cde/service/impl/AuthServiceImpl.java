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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

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
        SysUser user = getRequiredUser(request.getUsername());

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BadCredentialsException("Bad username or password");
        }

        String domainCode = buildDomainCode(user.getDomainId());
        String token = jwtUtil.generateToken(user.getUsername(), user.getClientId(), domainCode, user.getRoleType());
        return buildLoginResponse(user, token);
    }

    @Override
    public SysUser getUserByUsername(String username) {
        return userMapper.selectOne(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username));
    }

    @Override
    public LoginResponse getCurrentUserProfile(String username, String token) {
        SysUser user = getRequiredUser(username);
        return buildLoginResponse(user, token);
    }

    private SysUser getRequiredUser(String username) {
        SysUser user = getUserByUsername(username);
        if (user == null) {
            throw new BadCredentialsException("User does not exist: " + username);
        }
        return user;
    }

    private LoginResponse buildLoginResponse(SysUser user, String token) {
        return LoginResponse.builder()
                .token(token)
                .expires(jwtUtil.getExpirationMs() / 1000)
                .username(user.getUsername())
                .roleType(user.getRoleType())
                .roleName(resolveRoleName(user.getRoleType()))
                .domainCode(buildDomainCode(user.getDomainId()))
                .domainName(buildDomainName(user.getDomainId()))
                .clientId(user.getClientId())
                .build();
    }

    private String resolveRoleName(String roleType) {
        if (roleType == null) {
            return "Unknown";
        }
        return switch (roleType.toLowerCase(Locale.ROOT)) {
            case "admin" -> "Administrator";
            case "producer" -> "Producer";
            case "consumer" -> "Consumer";
            default -> roleType;
        };
    }

    private String buildDomainCode(Long domainId) {
        if (domainId == null) {
            return "all";
        }
        return String.join("/", loadDomainSegments(domainId, true));
    }

    private String buildDomainName(Long domainId) {
        if (domainId == null) {
            return "All";
        }
        return String.join(" / ", loadDomainSegments(domainId, false));
    }

    private List<String> loadDomainSegments(Long domainId, boolean useCode) {
        List<String> segments = new ArrayList<>();
        Set<Long> visited = new HashSet<>();
        Long currentId = domainId;

        while (currentId != null) {
            if (!visited.add(currentId)) {
                throw new BadCredentialsException("User domain contains a cycle");
            }
            SysDomain domain = domainMapper.selectById(currentId);
            if (domain == null) {
                throw new BadCredentialsException("User domain does not exist");
            }
            if (!Objects.equals(domain.getStatus(), 1)) {
                throw new BadCredentialsException("User domain is disabled");
            }
            segments.add(0, useCode ? domain.getDomainCode() : domain.getDomainName());
            currentId = domain.getParentId();
        }

        return segments;
    }
}
