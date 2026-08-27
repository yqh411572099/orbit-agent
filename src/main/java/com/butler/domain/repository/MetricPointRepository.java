package com.butler.domain.repository;

import com.butler.domain.model.MetricPoint;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface MetricPointRepository {
    MetricPoint save(MetricPoint point);
    List<MetricPoint> findBySubSessionId(Long subSessionId);
    Optional<Instant> findLastUpdatedAt(Long subSessionId);
    int deleteBySubSessionId(Long subSessionId);
    int archiveAndDeleteBySubSessionId(Long subSessionId, Long userId, String reason);
}
