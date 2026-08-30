package com.butler.infrastructure.persistence.po;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "pending_event", indexes = {
        @Index(name = "idx_pending_user", columnList = "user_id,scope,sub_session_id,status")
})
public class PendingEventPO {
    @Id
    @Column(length = 64)
    private String id;
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Column(nullable = false, length = 16)
    private String scope;
    @Column(name = "sub_session_id")
    private Long subSessionId;
    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;
    @Lob
    @Column(nullable = false)
    private String payload;
    @Lob
    @Column(name = "preview")
    private String preview;
    @Column(name = "message_id")
    private Long messageId;
    @Column(nullable = false, length = 16)
    private String status;
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getScope() { return scope; }
    public void setScope(String scope) { this.scope = scope; }
    public Long getSubSessionId() { return subSessionId; }
    public void setSubSessionId(Long subSessionId) { this.subSessionId = subSessionId; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }
    public String getPreview() { return preview; }
    public void setPreview(String preview) { this.preview = preview; }
    public Long getMessageId() { return messageId; }
    public void setMessageId(Long messageId) { this.messageId = messageId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
