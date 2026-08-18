package com.butler.infrastructure.persistence.jpa;

import com.butler.infrastructure.persistence.po.KnowledgeEntryPO;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface KnowledgeEntryJpaRepository extends JpaRepository<KnowledgeEntryPO, Long> {
    List<KnowledgeEntryPO> findByUserId(Long userId);
    List<KnowledgeEntryPO> findByUserIdAndStatus(Long userId, String status);
    List<KnowledgeEntryPO> findByStatus(String status);
    @Query("SELECT k FROM KnowledgeEntryPO k WHERE k.userId = :userId AND k.status = 'CONFIRMED' "
            + "AND (k.subSessionId = :subSessionId OR k.subSessionId IS NULL)")
    List<KnowledgeEntryPO> findConfirmedForSearch(@Param("userId") Long userId, @Param("subSessionId") Long subSessionId);
    List<KnowledgeEntryPO> findBySubSessionId(Long subSessionId);
    boolean existsByUserIdAndSourceUrl(Long userId, String sourceUrl);
    @Transactional
    void deleteByUserId(Long userId);
    
    void deleteBySubSessionId(Long subSessionId);
}
