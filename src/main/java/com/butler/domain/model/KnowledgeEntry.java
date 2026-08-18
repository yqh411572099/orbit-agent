package com.butler.domain.model;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 一条“可沉淀的知识”。联网检索/用户文档等来源的内容先以 PENDING 保存，
 * 用户确认后变为 CONFIRMED，供 {@code KnowledgeSearchTool} 优先检索；拒绝则 REJECTED。
 * 内置知识（BUILT_IN）直接可用，不经过确认流程。
 */
public class KnowledgeEntry {

    private final Long id;
    private final Long userId;
    /** 可选：该知识所属子对话；为空表示用户级通用知识。 */
    private final Long subSessionId;
    private final KnowledgeSource source;
    private final KnowledgeStatus status;
    private final String title;
    private final String content;
    private final String sourceUrl;
    private final String query;
    private final LocalDate validFrom;
    private final LocalDate validTo;
    private final Instant createdAt;
    private final Instant updatedAt;

    public KnowledgeEntry(Long id, Long userId, Long subSessionId, KnowledgeSource source, KnowledgeStatus status,
                          String title, String content, String sourceUrl, String query,
                          LocalDate validFrom, LocalDate validTo, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.userId = userId;
        this.subSessionId = subSessionId;
        this.source = source == null ? KnowledgeSource.MANUAL : source;
        this.status = status == null ? KnowledgeStatus.PENDING : status;
        this.title = title;
        this.content = content;
        this.sourceUrl = sourceUrl;
        this.query = query;
        this.validFrom = validFrom;
        this.validTo = validTo;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Long getSubSessionId() { return subSessionId; }
    public KnowledgeSource getSource() { return source; }
    public KnowledgeStatus getStatus() { return status; }
    public String getTitle() { return title; }
    public String getContent() { return content; }
    public String getSourceUrl() { return sourceUrl; }
    public String getQuery() { return query; }
    public LocalDate getValidFrom() { return validFrom; }
    public LocalDate getValidTo() { return validTo; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public KnowledgeEntry withStatus(KnowledgeStatus newStatus) {
        return new KnowledgeEntry(id, userId, subSessionId, source, newStatus, title, content, sourceUrl, query,
                validFrom, validTo, createdAt, Instant.now());
    }

    public KnowledgeEntry withId(Long newId) {
        return new KnowledgeEntry(newId, userId, subSessionId, source, status, title, content, sourceUrl, query,
                validFrom, validTo, createdAt, updatedAt);
    }

    public boolean isValidOn(LocalDate today) {
        if (validFrom != null && today.isBefore(validFrom)) return false;
        if (validTo != null && today.isAfter(validTo)) return false;
        return true;
    }
}
