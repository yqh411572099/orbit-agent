package com.butler.infrastructure.persistence.jpa;

import com.butler.infrastructure.persistence.po.PendingEventPO;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PendingEventJpaRepository extends JpaRepository<PendingEventPO, String> {
    List<PendingEventPO> findByUserIdAndScopeAndStatusOrderByCreatedAtDesc(Long userId, String scope, String status);
    List<PendingEventPO> findByUserIdAndScopeAndSubSessionIdAndStatusOrderByCreatedAtDesc(
            Long userId, String scope, Long subSessionId, String status);
    @org.springframework.data.jpa.repository.Query("select max(e.updatedAt) from PendingEventPO e where e.userId = :uid")
    java.time.Instant findLastUpdatedAt(@org.springframework.data.repository.query.Param("uid") Long uid);
}
