package com.cde.service.auth;

import com.cde.mapper.SysTopicAclMapper;
import com.cde.entity.SysTopicAcl;
import com.cde.security.JwtUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class JwtAuthPlugin implements AuthPlugin {
    private final JwtUtil jwtUtil;
    private final SysTopicAclMapper aclMapper;

    @Override
    public boolean authenticate(String clientId, String credentials) {
        return jwtUtil.validateToken(credentials)
                && clientId.equals(jwtUtil.getClientIdFromToken(credentials));
    }

    @Override
    public List<String> getAuthorizedTopics(String clientId) {
        return aclMapper.selectList(
                new LambdaQueryWrapper<SysTopicAcl>()
                        .eq(SysTopicAcl::getClientId, clientId)
                        .eq(SysTopicAcl::getAccessType, "allow"))
                .stream()
                .map(SysTopicAcl::getTopicFilter)
                .collect(Collectors.toList());
    }
}
