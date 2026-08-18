package com.butler.domain.model;

import java.time.Instant;

public class Mission {
    private final Long id;
    private final Long userId;
    private final String title;
    private final String scenarioType;
    private final Instant createdAt;

    public Mission(Long id, Long userId, String title, String scenarioType, Instant createdAt) {
        this.id = id;
        this.userId = userId;
        this.title = title;
        this.scenarioType = scenarioType;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getTitle() { return title; }
    public String getScenarioType() { return scenarioType; }
    public Instant getCreatedAt() { return createdAt; }
}
