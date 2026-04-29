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
import java.util.List;
import java.util.Locale;

/**
 * 认证服务实现
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
        SysUser user = getRequiredUser(request.getUsername());

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BadCredentialsException("密码错误");
        }

        String domainCode = buildDomainCode(user.getDomainId());
        String token = jwtUtil.generateToken(user.getUsername(), user.getClientId(), domainCode, user.getRoleType());
        return buildLoginResponse(user, token);
    }

    @Override
    public LoginResponse refreshToken(String oldToken) {
        if (!jwtUtil.validateToken(oldToken)) {
            throw new BadCredentialsException("令牌无效或已过期");
        }

        String username = jwtUtil.getUsernameFromToken(oldToken);
        SysUser user = getRequiredUser(username);
        String domainCode = buildDomainCode(user.getDomainId());
        String newToken = jwtUtil.generateToken(user.getUsername(), user.getClientId(), domainCode, user.getRoleType());
        return buildLoginResponse(user, newToken);
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
            throw new BadCredentialsException("用户不存在: " + username);
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
            return "未知角色";
        }
        return switch (roleType.toLowerCase(Locale.ROOT)) {
            case "admin" -> "管理员";
            case "producer" -> "生产者";
            case "consumer" -> "消费者";
            default -> roleType;
        };
    }

    /**
     * 构建域路径编码
     *
     * <p>从用户所属域向上递归遍历父域，拼接编码路径。
     * 例如domainId指向"民政"域，其父域为"政务"，则返回"gov/minzheng"。</p>
     */
    private String buildDomainCode(Long domainId) {
        if (domainId == null) {
            return "all";
        }
        return String.join("/", loadDomainSegments(domainId, true));
    }

    /**
     * 构建域路径名称
     *
     * <p>与buildDomainCode逻辑相同，但拼接的是中文名称。
     * 例如返回"政务 / 民政"。</p>
     */
    private String buildDomainName(Long domainId) {
        if (domainId == null) {
            return "全域";
        }
        return String.join(" / ", loadDomainSegments(domainId, false));
    }

    /**
     * 向上遍历域层级，收集编码或名称片段
     *
     * <p>从当前domainId开始，逐级查询parentId直到根域（parentId=null），
     * 将每级片段插入列表头部以保证从根到叶的顺序。</p>
     */
    private List<String> loadDomainSegments(Long domainId, boolean useCode) {
        List<String> segments = new ArrayList<>();
        Long currentId = domainId;

        while (currentId != null) {
            SysDomain domain = domainMapper.selectById(currentId);
            if (domain == null) {
                break;
            }
            segments.add(0, useCode ? domain.getDomainCode() : domain.getDomainName());
            currentId = domain.getParentId();
        }

        return segments;
    }
}
