package com.butler.infrastructure.persistence.po;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "raw_chat_log", indexes = {
        @Index(name = "idx_user_time", columnList = "user_id,created_at")
})
public class RawChatLogPO {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Column(name = "session_type", nullable = false)
    private String sessionType;
    @Column(name = "sub_session_id")
    private Long subSessionId;
    @Column(nullable = false)
    private String role;
    @Lob @Column(nullable = false)
    private String content;
    @Lob @Column(name="reasoning")
    private String reasoning;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getSessionType() { return sessionType; }
    public void setSessionType(String sessionType) { this.sessionType = sessionType; }
    public Long getSubSessionId() { return subSessionId; }
    public void setSubSessionId(Long subSessionId) { this.subSessionId = subSessionId; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getReasoning() { return reasoning; }
    public void setReasoning(String reasoning) { this.reasoning = reasoning; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
