package com.butler.infrastructure.persistence.jpa;

import com.butler.infrastructure.persistence.po.MetricPointPO;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface MetricPointJpaRepository extends JpaRepository<MetricPointPO, Long> {
    List<MetricPointPO> findBySubSessionIdOrderByValueDateAscIdAsc(Long subSessionId);
    @org.springframework.data.jpa.repository.Query("select max(m.createdAt) from MetricPointPO m where m.subSessionId = :sid")
    Instant findLastUpdatedAt(@org.springframework.data.repository.query.Param("sid") Long sid);
    @Transactional
    void deleteBySubSessionId(Long subSessionId);
}
