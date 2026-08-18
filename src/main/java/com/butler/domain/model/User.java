package com.butler.domain.model;

import java.time.Instant;

public class User {
    private final Long id;
    private final String username;
    private final String passwordHash;
    private final String nickname;
    private final UserType userType;
    private final Instant createdAt;

    public User(Long id, String username, String passwordHash, String nickname,
                UserType userType, Instant createdAt) {
        this.id = id;
        this.username = username;
        this.passwordHash = passwordHash;
        this.nickname = nickname;
        this.userType = userType == null ? UserType.NORMAL : userType;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public String getNickname() { return nickname; }
    public UserType getUserType() { return userType; }
    public Instant getCreatedAt() { return createdAt; }
}
