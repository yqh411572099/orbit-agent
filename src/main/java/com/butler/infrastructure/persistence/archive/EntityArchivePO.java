package com.butler.infrastructure.persistence.archive;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * 统一归档表：所有"用户真实数据"在物理删除前，原样快照写入本表。
 * entity_type 为原表名（如 task、raw_chat_log、user_memory）；snapshot 为原 PO 的 JSON。
 * 超过保留期的归档由定时任务清理（默认 30 天）。
 */
@Entity
@Table(name = "entity_archive", indexes = {
        @Index(name = "idx_archive_type_time", columnList = "entity_type,archived_at"),
        @Index(name = "idx_archive_user", columnList = "user_id"),
        @Index(name = "idx_archive_sub", columnList = "sub_session_id")
})
public class EntityArchivePO {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "entity_type", nullable = false, length = 64)
    private String entityType;

    @Column(name = "original_id")
    private Long originalId;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "sub_session_id")
    private Long subSessionId;

    @Lob
    @Column(name = "snapshot", nullable = false)
    private String snapshot;

    @Column(name = "reason", length = 64)
    private String reason;

    @Column(name = "archived_at", nullable = false)
    private Instant archivedAt = Instant.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }
    public Long getOriginalId() { return originalId; }
    public void setOriginalId(Long originalId) { this.originalId = originalId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getSubSessionId() { return subSessionId; }
    public void setSubSessionId(Long subSessionId) { this.subSessionId = subSessionId; }
    public String getSnapshot() { return snapshot; }
    public void setSnapshot(String snapshot) { this.snapshot = snapshot; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public Instant getArchivedAt() { return archivedAt; }
    public void setArchivedAt(Instant archivedAt) { this.archivedAt = archivedAt; }
}
