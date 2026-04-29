package com.cde.service;

import com.cde.dto.DomainTreeNode;
import com.cde.entity.SysDomain;

import java.util.List;

/**
 * 安全域服务接口
 *
 * <p>提供安全域的CRUD操作和树形结构构建。删除操作执行安全检查：
 * 存在子域或关联用户时拒绝删除，防止数据孤立。</p>
 */
public interface DomainService {
    List<SysDomain> listAll();

    List<DomainTreeNode> buildDomainTree();

    SysDomain getById(Long id);

    SysDomain create(SysDomain domain);

    SysDomain update(Long id, SysDomain domain);

    void delete(Long id);
}
