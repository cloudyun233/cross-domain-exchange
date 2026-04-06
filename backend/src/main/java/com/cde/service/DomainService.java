package com.cde.service;

import com.cde.dto.DomainTreeNode;
import com.cde.entity.SysDomain;

import java.util.List;

public interface DomainService {
    List<SysDomain> listAll();

    List<DomainTreeNode> buildDomainTree();

    SysDomain getById(Long id);

    SysDomain create(SysDomain domain);

    SysDomain update(Long id, SysDomain domain);

    void delete(Long id);
}
