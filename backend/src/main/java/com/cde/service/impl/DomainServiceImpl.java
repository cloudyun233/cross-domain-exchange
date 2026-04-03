package com.cde.service.impl;

import com.cde.entity.SysDomain;
import com.cde.mapper.SysDomainMapper;
import com.cde.service.DomainService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DomainServiceImpl implements DomainService {
    private final SysDomainMapper domainMapper;

    @Override
    public List<SysDomain> listAll() { return domainMapper.selectList(null); }

    @Override
    public SysDomain getById(Long id) { return domainMapper.selectById(id); }

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
    public void delete(Long id) { domainMapper.deleteById(id); }
}
