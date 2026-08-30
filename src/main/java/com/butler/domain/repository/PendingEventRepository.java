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
    /** 某会话下、某类事件、已关联到消息的全部事件（含已采纳/未采纳/待确认），用于对话内变更溯源。 */
    List<PendingEvent> findMessageLinked(Long userId, PendingEvent.Scope scope, Long subSessionId, String eventType);
}
