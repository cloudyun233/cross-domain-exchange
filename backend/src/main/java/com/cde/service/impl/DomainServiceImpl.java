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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;

/**
 * 安全域服务实现
 *
 * <p>树构建算法：筛选启用域(status=1) → 按parentId分组 → 递归buildNode构建树形结构。
 * 删除操作执行安全检查：存在子域或关联用户时抛出BusinessException拒绝删除。</p>
 */
@Service
@RequiredArgsConstructor
public class DomainServiceImpl implements DomainService {

    private static final String DOMAIN_CODE_PATTERN = "^[A-Za-z0-9_-]+$";

    private final SysDomainMapper domainMapper;
    private final SysUserMapper userMapper;

    @Override
    public List<SysDomain> listAll() {
        return domainMapper.selectList(new LambdaQueryWrapper<SysDomain>().orderByAsc(SysDomain::getId));
    }

    /**
     * 构建安全域树形结构
     *
     * <p>根节点为虚拟节点"跨域交换"（key=cross_domain），其children为所有顶级域。
     * 每个节点携带topicPath（如cross_domain/gov/minzheng）和subscribeTopic（叶节点精确匹配，
     * 非叶节点通配符匹配如cross_domain/gov/#）。</p>
     */
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
                .key("cross_domain")
                .title("跨域交换")
                .pathName("跨域交换")
                .topicPath("cross_domain")
                .subscribeTopic("cross_domain/#")
                .isLeaf(rootChildren.isEmpty())
                .children(rootChildren)
                .build();

        return List.of(root);
    }

    /**
     * 递归构建域树节点
     *
     * <p>codePath和namePath在递归过程中逐级累积：
     * 每进入子域，codePath追加"/子域编码"，namePath追加" / 子域名称"，
     * 形成从根到当前节点的完整路径。</p>
     */
    private DomainTreeNode buildNode(
            SysDomain domain,
            Map<Long, List<SysDomain>> childrenByParentId,
            String codePath,
            String namePath
    ) {
        List<DomainTreeNode> children = new ArrayList<>();
        for (SysDomain child : childrenByParentId.getOrDefault(domain.getId(), List.of())) {
            // 递归时累加路径：父路径 + "/" + 子域编码/名称
            children.add(buildNode(
                    child,
                    childrenByParentId,
                    codePath + "/" + child.getDomainCode(),
                    namePath + " / " + child.getDomainName()
            ));
        }

        String topicPath = "cross_domain/" + codePath;

        return DomainTreeNode.builder()
                .key(topicPath)
                .title(domain.getDomainName())
                .domainId(domain.getId())
                .domainCode(domain.getDomainCode())
                .domainName(domain.getDomainName())
                .pathName(namePath)
                .topicPath(topicPath)
                .subscribeTopic(children.isEmpty() ? topicPath : topicPath + "/#")
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
        validateDomain(domain, null);
        domainMapper.insert(domain);
        return domain;
    }

    @Override
    public SysDomain update(Long id, SysDomain domain) {
        SysDomain existing = requireDomain(id);
        validateDomain(domain, id);
        domain.setId(id);
        int updated = domainMapper.updateById(domain);
        if (updated == 0) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "安全域不存在");
        }
        return domainMapper.selectById(existing.getId());
    }

    @Override
    public void delete(Long id) {
        requireDomain(id);
        int childCount = domainMapper.countByParentId(id);
        if (childCount > 0) {
            throw new BusinessException("该安全域下存在子域，无法删除");
        }
        int userCount = userMapper.countByDomainId(id);
        if (userCount > 0) {
            throw new BusinessException("该安全域下存在用户，无法删除");
        }
        int deleted = domainMapper.deleteById(id);
        if (deleted == 0) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "安全域不存在");
        }
    }

    private SysDomain requireDomain(Long id) {
        SysDomain domain = domainMapper.selectById(id);
        if (domain == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "安全域不存在");
        }
        return domain;
    }

    private void validateDomain(SysDomain domain, Long editingId) {
        if (domain.getDomainCode() == null || !domain.getDomainCode().matches(DOMAIN_CODE_PATTERN)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "域编码只能包含字母、数字、下划线和中划线");
        }
        if (domain.getParentId() == null) {
            return;
        }
        if (Objects.equals(domain.getParentId(), editingId)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "安全域不能选择自己作为父域");
        }
        SysDomain parent = domainMapper.selectById(domain.getParentId());
        if (parent == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "父安全域不存在");
        }
        if (editingId != null && isDescendantOrSelf(domain.getParentId(), editingId)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "安全域父子关系不能成环");
        }
    }

    private boolean isDescendantOrSelf(Long candidateParentId, Long editingId) {
        Set<Long> visited = new HashSet<>();
        Long currentId = candidateParentId;
        while (currentId != null) {
            if (!visited.add(currentId)) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "安全域父子关系已存在环");
            }
            if (Objects.equals(currentId, editingId)) {
                return true;
            }
            SysDomain domain = domainMapper.selectById(currentId);
            currentId = domain == null ? null : domain.getParentId();
        }
        return false;
    }
}
