package com.butler.infrastructure.persistence.jpa;

import com.butler.infrastructure.persistence.po.SubSessionPO;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubSessionJpaRepository extends JpaRepository<SubSessionPO, Long> {
    List<SubSessionPO> findByUserIdAndStatus(Long userId, String status);
    List<SubSessionPO> findByUserIdOrderByIdDesc(Long userId);
    @org.springframework.data.jpa.repository.Query("select max(s.updatedAt) from SubSessionPO s where s.userId = :uid")
    java.time.Instant findLastUpdatedAt(@org.springframework.data.repository.query.Param("uid") Long uid);
}
