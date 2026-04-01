package com.cde.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cde.entity.SysDomain;
import java.util.List;

public interface DomainService {
    List<SysDomain> listAll();
    SysDomain getById(Long id);
    SysDomain create(SysDomain domain);
    SysDomain update(Long id, SysDomain domain);
    void delete(Long id);
}
