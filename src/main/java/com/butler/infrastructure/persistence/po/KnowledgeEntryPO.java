package com.butler.infrastructure.persistence.po;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "knowledge_entry",
        indexes = {
            @Index(name = "idx_know_user", columnList = "user_id"),
            @Index(name = "idx_know_user_status", columnList = "user_id,status"),
            @Index(name = "idx_know_url", columnList = "source_url")
        })
public class KnowledgeEntryPO {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Column(name = "sub_session_id")
    private Long subSessionId;
    @Column(name = "source", nullable = false, length = 32)
    private String source;
    @Column(name = "status", nullable = false, length = 16)
    private String status;
    @Column(name = "title", length = 500)
    private String title;
    @Lob
    @Column(name = "content")
    private String content;
    @Column(name = "source_url", length = 1000)
    private String sourceUrl;
    @Column(name = "query", length = 500)
    private String query;
    @Column(name = "valid_from")
    private LocalDate validFrom;
    @Column(name = "valid_to")
    private LocalDate validTo;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getSubSessionId() { return subSessionId; }
    public void setSubSessionId(Long subSessionId) { this.subSessionId = subSessionId; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getSourceUrl() { return sourceUrl; }
    public void setSourceUrl(String sourceUrl) { this.sourceUrl = sourceUrl; }
    public String getQuery() { return query; }
    public void setQuery(String query) { this.query = query; }
    public LocalDate getValidFrom() { return validFrom; }
    public void setValidFrom(LocalDate validFrom) { this.validFrom = validFrom; }
    public LocalDate getValidTo() { return validTo; }
    public void setValidTo(LocalDate validTo) { this.validTo = validTo; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
