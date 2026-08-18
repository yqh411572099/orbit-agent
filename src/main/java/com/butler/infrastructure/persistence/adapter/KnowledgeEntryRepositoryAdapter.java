package com.butler.infrastructure.persistence.adapter;

import com.butler.domain.model.KnowledgeEntry;
import com.butler.domain.model.KnowledgeSource;
import com.butler.domain.model.KnowledgeStatus;
import com.butler.domain.repository.KnowledgeEntryRepository;
import com.butler.infrastructure.persistence.archive.ArchiveRecorder;
import com.butler.infrastructure.persistence.jpa.KnowledgeEntryJpaRepository;
import com.butler.infrastructure.persistence.po.KnowledgeEntryPO;
import java.util.List;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class KnowledgeEntryRepositoryAdapter implements KnowledgeEntryRepository {

    private final KnowledgeEntryJpaRepository jpa;
    private final ArchiveRecorder archiveRecorder;

    public KnowledgeEntryRepositoryAdapter(KnowledgeEntryJpaRepository jpa, ArchiveRecorder archiveRecorder) {
        this.jpa = jpa;
        this.archiveRecorder = archiveRecorder;
    }

    @Override
    public KnowledgeEntry save(KnowledgeEntry entry) {
        KnowledgeEntryPO po = new KnowledgeEntryPO();
        po.setId(entry.getId());
        po.setUserId(entry.getUserId());
        po.setSubSessionId(entry.getSubSessionId());
        po.setSource(entry.getSource().name());
        po.setStatus(entry.getStatus().name());
        po.setTitle(entry.getTitle());
        po.setContent(entry.getContent());
        po.setSourceUrl(entry.getSourceUrl());
        po.setQuery(entry.getQuery());
        po.setValidFrom(entry.getValidFrom());
        po.setValidTo(entry.getValidTo());
        Instant now = Instant.now();
        po.setCreatedAt(entry.getCreatedAt() == null ? now : entry.getCreatedAt());
        po.setUpdatedAt(now);
        KnowledgeEntryPO saved = jpa.save(po);
        return toDomain(saved);
    }

    @Override
    public Optional<KnowledgeEntry> findById(Long id) {
        return jpa.findById(id).map(this::toDomain);
    }

    @Override
    public List<KnowledgeEntry> findByUserId(Long userId) {
        return jpa.findByUserId(userId).stream().map(this::toDomain).toList();
    }

    @Override
    public List<KnowledgeEntry> findByUserIdAndStatus(Long userId, KnowledgeStatus status) {
        return jpa.findByUserIdAndStatus(userId, status.name()).stream().map(this::toDomain).toList();
    }

    @Override
    public List<KnowledgeEntry> findByStatus(KnowledgeStatus status) {
        return jpa.findByStatus(status.name()).stream().map(this::toDomain).toList();
    }

    @Override
    public List<KnowledgeEntry> findConfirmedForSearch(Long userId, Long subSessionId) {
        return jpa.findConfirmedForSearch(userId, subSessionId).stream().map(this::toDomain).toList();
    }

    @Override
    public List<KnowledgeEntry> findBySubSessionId(Long subSessionId) {
        return jpa.findBySubSessionId(subSessionId).stream().map(this::toDomain).toList();
    }

    @Override
    public boolean existsByUserIdAndSourceUrl(Long userId, String sourceUrl) {
        if (sourceUrl == null || sourceUrl.isBlank()) return false;
        return jpa.existsByUserIdAndSourceUrl(userId, sourceUrl);
    }

    @Override
    public int deleteByUserId(Long userId) {
        List<KnowledgeEntryPO> all = jpa.findByUserId(userId);
        jpa.deleteByUserId(userId);
        return all.size();
    }

    @Override
    public int deleteBySubSessionId(Long subSessionId) {
        List<KnowledgeEntryPO> all = jpa.findBySubSessionId(subSessionId);
        jpa.deleteBySubSessionId(subSessionId);
        return all.size();
    }

    @Override
    public int archiveAndDeleteByUserId(Long userId, String reason) {
        List<KnowledgeEntryPO> all = jpa.findByUserId(userId);
        archiveRecorder.archiveAll("knowledge_entry", all, userId, null, reason);
        jpa.deleteByUserId(userId);
        return all.size();
    }

    @Override
    public int archiveAndDeleteBySubSessionId(Long subSessionId, Long userId, String reason) {
        List<KnowledgeEntryPO> all = jpa.findBySubSessionId(subSessionId);
        archiveRecorder.archiveAll("knowledge_entry", all, userId, subSessionId, reason);
        jpa.deleteBySubSessionId(subSessionId);
        return all.size();
    }

    private KnowledgeEntry toDomain(KnowledgeEntryPO po) {
        return new KnowledgeEntry(po.getId(), po.getUserId(), po.getSubSessionId(),
                KnowledgeSource.valueOf(po.getSource()), KnowledgeStatus.valueOf(po.getStatus()),
                po.getTitle(), po.getContent(), po.getSourceUrl(), po.getQuery(),
                po.getValidFrom(), po.getValidTo(), po.getCreatedAt(), po.getUpdatedAt());
    }
}
