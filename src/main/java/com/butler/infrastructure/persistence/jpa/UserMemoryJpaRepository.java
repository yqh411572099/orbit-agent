package com.butler.infrastructure.persistence.jpa;

import com.butler.infrastructure.persistence.po.UserMemoryPO;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface UserMemoryJpaRepository extends JpaRepository<UserMemoryPO, Long> {
    List<UserMemoryPO> findByUserId(Long userId);
    List<UserMemoryPO> findByIdIn(List<Long> ids);
    @Transactional
    int deleteByUserId(Long userId);
    @Transactional
    int deleteMemoryById(Long id);
}
