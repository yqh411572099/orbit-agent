package com.butler.infrastructure.persistence.jpa;

import com.butler.infrastructure.persistence.po.MemorySessionRelPO;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface MemorySessionRelJpaRepository extends JpaRepository<MemorySessionRelPO, Long> {
    List<MemorySessionRelPO> findByMemoryId(Long memoryId);
    List<MemorySessionRelPO> findBySubSessionId(Long subSessionId);
    @Override
    List<MemorySessionRelPO> findAll();
    boolean existsByMemoryIdAndSubSessionId(Long memoryId, Long subSessionId);
    @Transactional
    void deleteBySubSessionId(Long subSessionId);
    @Transactional
    void deleteByMemoryId(Long memoryId);
}
