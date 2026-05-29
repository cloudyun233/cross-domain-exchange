package com.cde.controller;

import com.cde.entity.SysDomain;
import com.cde.entity.SysUser;
import com.cde.exception.BusinessException;
import com.cde.mapper.SysDomainMapper;
import com.cde.mapper.SysUserMapper;
import com.cde.service.AclService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClientControllerTest {

    @Mock
    private SysUserMapper userMapper;

    @Mock
    private SysDomainMapper domainMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AclService aclService;

    @InjectMocks
    private ClientController controller;

    @Test
    void listGetAndCreateMaskPasswordAndValidateDomain() {
        SysUser user = user(1L, "alice", "producer", 2L);
        when(userMapper.selectList(null)).thenReturn(List.of(user));
        assertThat(controller.list().getData().get(0).getPasswordHash()).isEqualTo("***");

        SysUser one = user(1L, "alice", "producer", 2L);
        when(userMapper.selectById(1L)).thenReturn(one);
        assertThat(controller.get(1L).getData().getPasswordHash()).isEqualTo("***");

        SysUser created = user(null, "bob", "producer", 2L);
        created.setPasswordHash("plain");
        when(domainMapper.selectById(2L)).thenReturn(domain(2L, 1));
        when(passwordEncoder.encode("plain")).thenReturn("encoded");
        assertThat(controller.create(created).getData().getClientId()).isEqualTo("bob_001");
        assertThat(created.getPasswordHash()).isEqualTo("***");
        verify(userMapper).insert(created);
    }

    @Test
    void createAndUpdateRejectInvalidRolesDomainsAndIdentityChanges() {
        assertThatThrownBy(() -> controller.create(user(null, "bad", "guest", null)))
                .isInstanceOf(BusinessException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.BAD_REQUEST);

        assertThatThrownBy(() -> controller.create(user(null, "producer", "producer", null)))
                .isInstanceOf(BusinessException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.BAD_REQUEST);

        when(domainMapper.selectById(2L)).thenReturn(domain(2L, 0));
        assertThatThrownBy(() -> controller.create(user(null, "producer", "producer", 2L)))
                .isInstanceOf(BusinessException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.BAD_REQUEST);

        SysUser existing = user(1L, "alice", "producer", 2L);
        existing.setClientId("alice_001");
        when(userMapper.selectById(1L)).thenReturn(existing);
        SysUser rename = user(1L, "other", "producer", 2L);
        assertThatThrownBy(() -> controller.update(1L, rename))
                .isInstanceOf(BusinessException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.BAD_REQUEST);

        SysUser clientChange = user(1L, "alice", "producer", 2L);
        clientChange.setClientId("changed");
        assertThatThrownBy(() -> controller.update(1L, clientChange))
                .isInstanceOf(BusinessException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void updateDeleteAndMissingRowsReturnNotFoundStyleErrors() {
        SysUser existing = user(1L, "alice", "producer", 2L);
        existing.setClientId("alice_001");
        SysUser updated = user(1L, "alice", "consumer", 2L);
        updated.setClientId("alice_001");
        updated.setPasswordHash("***");
        when(userMapper.selectById(1L)).thenReturn(existing, updated);
        when(domainMapper.selectById(2L)).thenReturn(domain(2L, 1));
        when(userMapper.updateById(any())).thenReturn(1);

        assertThat(controller.update(1L, updated).getData().getPasswordHash()).isEqualTo("***");

        when(userMapper.selectById(9L)).thenReturn(null);
        assertThatThrownBy(() -> controller.update(9L, user(9L, "none", "admin", null)))
                .isInstanceOf(BusinessException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.NOT_FOUND);

        SysUser deleteMe = user(3L, "delete-me", "consumer", 2L);
        when(userMapper.selectById(3L)).thenReturn(deleteMe);
        when(userMapper.deleteById(3L)).thenReturn(1);
        controller.delete(3L);
        verify(aclService).deleteByUsername("delete-me");
    }

    private SysUser user(Long id, String username, String roleType, Long domainId) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setUsername(username);
        user.setClientId(username + "_001");
        user.setPasswordHash("hash");
        user.setRoleType(roleType);
        user.setDomainId(domainId);
        return user;
    }

    private SysDomain domain(Long id, int status) {
        SysDomain domain = new SysDomain();
        domain.setId(id);
        domain.setStatus(status);
        return domain;
    }
}
