package com.butler.domain.repository;

import com.butler.domain.model.PendingEvent;
import java.util.List;
import java.util.Optional;

public interface PendingEventRepository {
    PendingEvent save(PendingEvent event);
    Optional<PendingEvent> findById(String id);
    java.util.Optional<java.time.Instant> findLastUpdatedAt(Long userId);
    List<PendingEvent> findPendingByUser(Long userId, PendingEvent.Scope scope, Long subSessionId);
    Optional<PendingEvent> findLatestPendingByUser(Long userId, PendingEvent.Scope scope, Long subSessionId);
}
