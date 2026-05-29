package com.cde.service.impl;

import com.cde.dto.LoginRequest;
import com.cde.entity.SysDomain;
import com.cde.entity.SysUser;
import com.cde.mapper.SysDomainMapper;
import com.cde.mapper.SysUserMapper;
import com.cde.security.JwtUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private SysUserMapper userMapper;

    @Mock
    private SysDomainMapper domainMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtUtil jwtUtil;

    @InjectMocks
    private AuthServiceImpl authService;

    @Test
    void loginBuildsProfileWithDomainPathAndToken() {
        SysUser user = user("producer", 2L);
        when(userMapper.selectOne(any())).thenReturn(user);
        when(passwordEncoder.matches("secret", "hash")).thenReturn(true);
        when(domainMapper.selectById(2L)).thenReturn(domain(2L, 1L, "tax", "Tax", 1));
        when(domainMapper.selectById(1L)).thenReturn(domain(1L, null, "gov", "Government", 1));
        when(jwtUtil.generateToken("alice", "alice_001", "gov/tax", "producer")).thenReturn("jwt");
        when(jwtUtil.getExpirationMs()).thenReturn(60000L);

        var response = authService.login(loginRequest("alice", "secret"));

        assertThat(response.getToken()).isEqualTo("jwt");
        assertThat(response.getExpires()).isEqualTo(60);
        assertThat(response.getRoleName()).isEqualTo("Producer");
        assertThat(response.getDomainCode()).isEqualTo("gov/tax");
        assertThat(response.getDomainName()).isEqualTo("Government / Tax");
    }

    @Test
    void adminProfileUsesAllDomainAndKnownRoleName() {
        SysUser admin = user("admin", null);
        admin.setUsername("root");
        when(userMapper.selectOne(any())).thenReturn(admin);
        when(jwtUtil.getExpirationMs()).thenReturn(1000L);

        var response = authService.getCurrentUserProfile("root", "token");

        assertThat(response.getRoleName()).isEqualTo("Administrator");
        assertThat(response.getDomainCode()).isEqualTo("all");
        assertThat(response.getDomainName()).isEqualTo("All");
        assertThat(response.getToken()).isEqualTo("token");
    }

    @Test
    void loginRejectsMissingUserAndBadPassword() {
        when(userMapper.selectOne(any())).thenReturn(null);
        assertThatThrownBy(() -> authService.login(loginRequest("missing", "pw")))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("missing");

        SysUser user = user("consumer", 1L);
        when(userMapper.selectOne(any())).thenReturn(user);
        when(passwordEncoder.matches("bad", "hash")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(loginRequest("alice", "bad")))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Bad username or password");
    }

    @Test
    void profileRejectsMissingDisabledOrCyclicDomain() {
        SysUser user = user("consumer", 9L);
        when(userMapper.selectOne(any())).thenReturn(user);
        when(domainMapper.selectById(9L)).thenReturn(null);
        assertThatThrownBy(() -> authService.getCurrentUserProfile("alice", "token"))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("User domain does not exist");

        when(domainMapper.selectById(9L)).thenReturn(domain(9L, null, "x", "X", 0));
        assertThatThrownBy(() -> authService.getCurrentUserProfile("alice", "token"))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("User domain is disabled");

        when(domainMapper.selectById(9L)).thenReturn(domain(9L, 10L, "a", "A", 1));
        when(domainMapper.selectById(10L)).thenReturn(domain(10L, 9L, "b", "B", 1));
        assertThatThrownBy(() -> authService.getCurrentUserProfile("alice", "token"))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("User domain contains a cycle");
    }

    @Test
    void getUserByUsernameDelegatesToMapper() {
        SysUser user = user("custom", null);
        when(userMapper.selectOne(any())).thenReturn(user);

        assertThat(authService.getUserByUsername("alice")).isSameAs(user);
        verify(userMapper).selectOne(any());
    }

    private SysUser user(String roleType, Long domainId) {
        SysUser user = new SysUser();
        user.setUsername("alice");
        user.setPasswordHash("hash");
        user.setClientId("alice_001");
        user.setRoleType(roleType);
        user.setDomainId(domainId);
        return user;
    }

    private LoginRequest loginRequest(String username, String password) {
        LoginRequest request = new LoginRequest();
        request.setUsername(username);
        request.setPassword(password);
        return request;
    }

    private SysDomain domain(Long id, Long parentId, String code, String name, int status) {
        SysDomain domain = new SysDomain();
        domain.setId(id);
        domain.setParentId(parentId);
        domain.setDomainCode(code);
        domain.setDomainName(name);
        domain.setStatus(status);
        return domain;
    }
}
