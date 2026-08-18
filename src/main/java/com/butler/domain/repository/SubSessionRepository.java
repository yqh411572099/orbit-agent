package com.butler.domain.repository;

import com.butler.domain.model.SubSession;
import com.butler.domain.model.SubSessionStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SubSessionRepository {
    SubSession save(SubSession session);
    Optional<SubSession> findById(Long id);
    List<SubSession> findByUserIdAndStatus(Long userId, SubSessionStatus status);
    List<SubSession> findByUserId(Long userId);
    Optional<Instant> findLastUpdatedAt(Long userId);
    void deleteById(Long id);
    void archiveAndDeleteById(Long id, String reason);
}
