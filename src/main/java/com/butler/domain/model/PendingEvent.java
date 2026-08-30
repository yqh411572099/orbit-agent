package com.butler.domain.model;

import java.time.Instant;

/**
 * 对话过程中产生的、需要用户确认的定制事件（建目标确认卡、变更确认卡等）。
 * 持久化存储，避免重启/刷新丢失；确认/放弃后改状态而非物理删除。
 */
public class PendingEvent {
    public enum Scope { MAIN, SUB }
    public enum Status { PENDING, APPLIED, DISCARDED, EXPIRED }

    private final String id;
    private final Long userId;
    private final Scope scope;
    private final Long subSessionId;
    private final String eventType;
    private final String payload;
    private String preview;
    /** 触发该事件的助手消息 ID（用于在对话里挂载只读变更溯源卡）。 */
    private Long messageId;
    private Status status;
    private final Instant expiresAt;
    private final Instant createdAt;
    private Instant updatedAt;

    public PendingEvent(String id, Long userId, Scope scope, Long subSessionId, String eventType,
                        String payload, Status status, Instant expiresAt, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.userId = userId;
        this.scope = scope;
        this.subSessionId = subSessionId;
        this.eventType = eventType;
        this.payload = payload;
        this.status = status;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getId() { return id; }
    public Long getUserId() { return userId; }
    public Scope getScope() { return scope; }
    public Long getSubSessionId() { return subSessionId; }
    public String getEventType() { return eventType; }
    public String getPayload() { return payload; }
    public String getPreview() { return preview; }
    public void setPreview(String preview) { this.preview = preview; this.updatedAt = Instant.now(); }
    public Long getMessageId() { return messageId; }
    public void setMessageId(Long messageId) { this.messageId = messageId; this.updatedAt = Instant.now(); }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; this.updatedAt = Instant.now(); }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
