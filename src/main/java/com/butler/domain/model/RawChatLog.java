package com.butler.domain.model;

import java.time.Instant;

public class RawChatLog {
    private final Long id;
    private final Long userId;
    private final SessionType sessionType;
    private final Long subSessionId;
    private final String role;
    private final String content;
    private final String reasoning;
    private final Instant createdAt;

    public RawChatLog(Long id, Long userId, SessionType sessionType, Long subSessionId,
                      String role, String content, String reasoning, Instant createdAt) {
        this.id = id;
        this.userId = userId;
        this.sessionType = sessionType;
        this.subSessionId = subSessionId;
        this.role = role;
        this.content = content;
        this.reasoning = reasoning;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public SessionType getSessionType() { return sessionType; }
    public Long getSubSessionId() { return subSessionId; }
    public String getRole() { return role; }
    public String getContent() { return content; }
    public String getReasoning() { return reasoning; }
    public Instant getCreatedAt() { return createdAt; }
}
