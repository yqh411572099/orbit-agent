package com.butler.infrastructure.persistence.po;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "app_user", uniqueConstraints = @UniqueConstraint(columnNames = "username"),
        indexes = @Index(name = "idx_user_type", columnList = "user_type"))
public class UserPO {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 64)
    private String username;
    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;
    @Column(length = 64)
    private String nickname;
    @Column(name = "user_type", nullable = false, length = 16)
    private String userType = "NORMAL";
    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public String getUserType() { return userType; }
    public void setUserType(String userType) { this.userType = userType; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
