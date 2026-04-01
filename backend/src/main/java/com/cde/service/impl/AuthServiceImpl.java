package com.cde.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cde.dto.LoginRequest;
import com.cde.dto.LoginResponse;
import com.cde.entity.SysDomain;
import com.cde.entity.SysTopicAcl;
import com.cde.entity.SysUser;
import com.cde.mapper.SysDomainMapper;
import com.cde.mapper.SysTopicAclMapper;
import com.cde.mapper.SysUserMapper;
import com.cde.security.JwtUtil;
import com.cde.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 鉴权服务实现 (论文4.5.3: AuthServiceImpl)
 * 核心职责: JWT签发、ACL校验
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final SysUserMapper userMapper;
    private final SysDomainMapper domainMapper;
    private final SysTopicAclMapper aclMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @Override
    public LoginResponse login(LoginRequest request) {
        SysUser user = userMapper.selectOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getClientId, request.getUsername()));

        if (user == null) {
            throw new BadCredentialsException("用户不存在: " + request.getUsername());
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BadCredentialsException("密码错误");
        }

        SysDomain domain = domainMapper.selectById(user.getDomainId());
        String domainCode = domain != null ? domain.getDomainCode() : "unknown";
        String domainName = domain != null ? domain.getDomainName() : "未知域";

        String token = jwtUtil.generateToken(user.getClientId(), domainCode, user.getRoleType());

        return LoginResponse.builder()
                .token(token)
                .expires(jwtUtil.getExpirationMs() / 1000)
                .clientId(user.getClientId())
                .roleType(user.getRoleType())
                .domainCode(domainCode)
                .domainName(domainName)
                .build();
    }

    @Override
    public LoginResponse refreshToken(String oldToken) {
        if (!jwtUtil.validateToken(oldToken)) {
            throw new BadCredentialsException("令牌无效或已过期");
        }
        String clientId = jwtUtil.getClientIdFromToken(oldToken);
        String domainCode = jwtUtil.getDomainCodeFromToken(oldToken);
        String roleType = jwtUtil.getRoleTypeFromToken(oldToken);

        String newToken = jwtUtil.generateToken(clientId, domainCode, roleType);
        return LoginResponse.builder()
                .token(newToken)
                .expires(jwtUtil.getExpirationMs() / 1000)
                .clientId(clientId)
                .roleType(roleType)
                .domainCode(domainCode)
                .build();
    }

    /**
     * ACL权限校验 (论文4.2.4: 递进式校验流程)
     * 1. 超级管理员直接放行
     * 2. 检查黑名单(deny)规则
     * 3. 检查白名单(allow)精确/通配符匹配
     * 4. 默认兜底拒绝
     */
    @Override
    public boolean checkACL(String clientId, String topic, String action) {
        // 1. admin超级角色直接放行
        SysUser user = userMapper.selectOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getClientId, clientId));
        if (user != null && "admin".equals(user.getRoleType())) {
            return true;
        }

        // 获取该客户端所有ACL规则 + 全局规则(*)
        List<SysTopicAcl> rules = aclMapper.selectList(
                new LambdaQueryWrapper<SysTopicAcl>()
                        .in(SysTopicAcl::getClientId, clientId, "*"));

        // 2. 检查deny规则
        for (SysTopicAcl rule : rules) {
            if ("deny".equals(rule.getAccessType()) && matchesAction(rule.getAction(), action)
                    && matchesTopic(rule.getTopicFilter(), topic)) {
                // 非全局兜底*的deny规则 → 黑名单命中
                if (!"*".equals(rule.getClientId()) || !"#".equals(rule.getTopicFilter())) {
                    log.warn("ACL黑名单命中: client={}, topic={}, action={}", clientId, topic, action);
                    return false;
                }
            }
        }

        // 3. 检查allow规则 (精确匹配 + 通配符匹配)
        for (SysTopicAcl rule : rules) {
            if ("allow".equals(rule.getAccessType()) && matchesAction(rule.getAction(), action)
                    && matchesTopic(rule.getTopicFilter(), topic)) {
                return true;
            }
        }

        // 4. 默认兜底拒绝
        log.warn("ACL默认拒绝: client={}, topic={}, action={}", clientId, topic, action);
        return false;
    }

    private boolean matchesAction(String ruleAction, String requestAction) {
        return "all".equals(ruleAction) || ruleAction.equals(requestAction);
    }

    /**
     * MQTT主题通配符匹配
     * + 匹配单层, # 匹配多层
     */
    private boolean matchesTopic(String filter, String topic) {
        if ("#".equals(filter)) return true;
        if (filter.equals(topic)) return true;

        String[] filterParts = filter.split("/");
        String[] topicParts = topic.split("/");

        for (int i = 0; i < filterParts.length; i++) {
            if ("#".equals(filterParts[i])) return true;
            if (i >= topicParts.length) return false;
            if (!"+".equals(filterParts[i]) && !filterParts[i].equals(topicParts[i])) return false;
        }
        return filterParts.length == topicParts.length;
    }
}
