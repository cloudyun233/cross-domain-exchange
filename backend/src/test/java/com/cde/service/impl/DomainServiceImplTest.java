package com.cde.service.impl;

import com.cde.entity.SysDomain;
import com.cde.exception.BusinessException;
import com.cde.mapper.SysDomainMapper;
import com.cde.mapper.SysUserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DomainServiceImplTest {

    @Mock
    private SysDomainMapper domainMapper;

    @Mock
    private SysUserMapper userMapper;

    @InjectMocks
    private DomainServiceImpl service;

    @Test
    void buildDomainTreeIncludesOnlyEnabledDomainsAndTopicPaths() {
        SysDomain root = domain(1L, null, "gov", "Government", 1);
        SysDomain child = domain(2L, 1L, "tax", "Tax", 1);
        SysDomain disabled = domain(3L, null, "disabled", "Disabled", 0);
        when(domainMapper.selectList(any())).thenReturn(List.of(child, disabled, root));

        var tree = service.buildDomainTree();

        assertThat(tree).hasSize(1);
        assertThat(tree.get(0).getKey()).isEqualTo("cross_domain");
        assertThat(tree.get(0).getChildren()).hasSize(1);
        var gov = tree.get(0).getChildren().get(0);
        assertThat(gov.getTopicPath()).isEqualTo("cross_domain/gov");
        assertThat(gov.getSubscribeTopic()).isEqualTo("cross_domain/gov/#");
        assertThat(gov.getChildren().get(0).getTopicPath()).isEqualTo("cross_domain/gov/tax");
    }

    @Test
    void createRejectsUnsafeDomainCodeAndMissingParent() {
        assertThatThrownBy(() -> service.create(domain(null, null, "gov/#", "Bad", 1)))
                .isInstanceOf(BusinessException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.BAD_REQUEST);

        SysDomain child = domain(null, 9L, "child", "Child", 1);
        when(domainMapper.selectById(9L)).thenReturn(null);

        assertThatThrownBy(() -> service.create(child))
                .isInstanceOf(BusinessException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void updateRejectsSelfParentDescendantCycleAndMissingRows() {
        SysDomain existing = domain(1L, null, "root", "Root", 1);
        when(domainMapper.selectById(1L)).thenReturn(existing);

        assertThatThrownBy(() -> service.update(1L, domain(1L, 1L, "root", "Root", 1)))
                .isInstanceOf(BusinessException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.BAD_REQUEST);

        SysDomain child = domain(2L, 1L, "child", "Child", 1);
        when(domainMapper.selectById(2L)).thenReturn(child);

        assertThatThrownBy(() -> service.update(1L, domain(1L, 2L, "root", "Root", 1)))
                .isInstanceOf(BusinessException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void updateReturnsPersistedDomainAndDeleteValidatesReferences() {
        SysDomain existing = domain(1L, null, "root", "Root", 1);
        SysDomain updated = domain(1L, null, "root2", "Root 2", 1);
        when(domainMapper.selectById(1L)).thenReturn(existing, updated);
        when(domainMapper.updateById(any(SysDomain.class))).thenReturn(1);

        assertThat(service.update(1L, updated)).isSameAs(updated);

        when(domainMapper.selectById(2L)).thenReturn(domain(2L, null, "leaf", "Leaf", 1));
        when(domainMapper.countByParentId(2L)).thenReturn(0);
        when(userMapper.countByDomainId(2L)).thenReturn(0);
        when(domainMapper.deleteById(2L)).thenReturn(1);

        service.delete(2L);
        verify(domainMapper).deleteById(2L);
    }

    @Test
    void deleteRejectsChildrenUsersAndMissingRows() {
        when(domainMapper.selectById(1L)).thenReturn(domain(1L, null, "root", "Root", 1));
        when(domainMapper.countByParentId(1L)).thenReturn(1);
        assertThatThrownBy(() -> service.delete(1L)).isInstanceOf(BusinessException.class);

        when(domainMapper.selectById(2L)).thenReturn(domain(2L, null, "leaf", "Leaf", 1));
        when(domainMapper.countByParentId(2L)).thenReturn(0);
        when(userMapper.countByDomainId(2L)).thenReturn(1);
        assertThatThrownBy(() -> service.delete(2L)).isInstanceOf(BusinessException.class);

        when(domainMapper.selectById(3L)).thenReturn(null);
        assertThatThrownBy(() -> service.delete(3L))
                .isInstanceOf(BusinessException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.NOT_FOUND);
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
