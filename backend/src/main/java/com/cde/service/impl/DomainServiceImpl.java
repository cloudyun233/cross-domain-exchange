package com.cde.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cde.dto.DomainTreeNode;
import com.cde.entity.SysDomain;
import com.cde.exception.BusinessException;
import com.cde.mapper.SysDomainMapper;
import com.cde.mapper.SysUserMapper;
import com.cde.service.DomainService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DomainServiceImpl implements DomainService {

    private final SysDomainMapper domainMapper;
    private final SysUserMapper userMapper;

    @Override
    public List<SysDomain> listAll() {
        return domainMapper.selectList(new LambdaQueryWrapper<SysDomain>().orderByAsc(SysDomain::getId));
    }

    @Override
    public List<DomainTreeNode> buildDomainTree() {
        List<SysDomain> domains = listAll().stream()
                .filter(domain -> Objects.equals(domain.getStatus(), 1))
                .toList();

        List<SysDomain> rootDomains = domains.stream()
                .filter(domain -> domain.getParentId() == null)
                .sorted(Comparator.comparing(SysDomain::getId))
                .toList();

        Map<Long, List<SysDomain>> childrenByParentId = domains.stream()
                .filter(domain -> domain.getParentId() != null)
                .collect(Collectors.groupingBy(
                        SysDomain::getParentId,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        childrenByParentId.values().forEach(children -> children.sort(Comparator.comparing(SysDomain::getId)));

        List<DomainTreeNode> rootChildren = rootDomains.stream()
                .map(domain -> buildNode(domain, childrenByParentId, domain.getDomainCode(), domain.getDomainName()))
                .toList();

        DomainTreeNode root = DomainTreeNode.builder()
                .key("/cross_domain")
                .title("跨域交换")
                .pathName("跨域交换")
                .topicPath("/cross_domain")
                .subscribeTopic("/cross_domain/#")
                .isLeaf(rootChildren.isEmpty())
                .children(rootChildren)
                .build();

        return List.of(root);
    }

    private DomainTreeNode buildNode(
            SysDomain domain,
            Map<Long, List<SysDomain>> childrenByParentId,
            String codePath,
            String namePath
    ) {
        List<DomainTreeNode> children = new ArrayList<>();
        for (SysDomain child : childrenByParentId.getOrDefault(domain.getId(), List.of())) {
            children.add(buildNode(
                    child,
                    childrenByParentId,
                    codePath + "/" + child.getDomainCode(),
                    namePath + " / " + child.getDomainName()
            ));
        }

        String topicPath = "/cross_domain/" + codePath;

        return DomainTreeNode.builder()
                .key(topicPath)
                .title(domain.getDomainName())
                .domainId(domain.getId())
                .domainCode(domain.getDomainCode())
                .domainName(domain.getDomainName())
                .pathName(namePath)
                .topicPath(topicPath)
                .subscribeTopic(topicPath + "/#")
                .isLeaf(children.isEmpty())
                .children(children)
                .build();
    }

    @Override
    public SysDomain getById(Long id) {
        return domainMapper.selectById(id);
    }

    @Override
    public SysDomain create(SysDomain domain) {
        domainMapper.insert(domain);
        return domain;
    }

    @Override
    public SysDomain update(Long id, SysDomain domain) {
        domain.setId(id);
        domainMapper.updateById(domain);
        return domainMapper.selectById(id);
    }

    @Override
    public void delete(Long id) {
        int childCount = domainMapper.countByParentId(id);
        if (childCount > 0) {
            throw new BusinessException("该安全域下存在子域，无法删除");
        }
        int userCount = userMapper.countByDomainId(id);
        if (userCount > 0) {
            throw new BusinessException("该安全域下存在用户，无法删除");
        }
        domainMapper.deleteById(id);
    }
}
