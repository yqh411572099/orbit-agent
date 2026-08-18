package com.butler.domain.repository;

import com.butler.domain.model.KnowledgeEntry;
import com.butler.domain.model.KnowledgeStatus;
import java.util.List;
import java.util.Optional;

public interface KnowledgeEntryRepository {
    KnowledgeEntry save(KnowledgeEntry entry);
    Optional<KnowledgeEntry> findById(Long id);
    List<KnowledgeEntry> findByUserId(Long userId);
    List<KnowledgeEntry> findByUserIdAndStatus(Long userId, KnowledgeStatus status);
    List<KnowledgeEntry> findByStatus(KnowledgeStatus status);
    List<KnowledgeEntry> findConfirmedForSearch(Long userId, Long subSessionId);
    List<KnowledgeEntry> findBySubSessionId(Long subSessionId);
    boolean existsByUserIdAndSourceUrl(Long userId, String sourceUrl);
    int deleteByUserId(Long userId);
    int deleteBySubSessionId(Long subSessionId);

    int archiveAndDeleteByUserId(Long userId, String reason);
    int archiveAndDeleteBySubSessionId(Long subSessionId, Long userId, String reason);
}
