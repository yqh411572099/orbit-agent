package com.butler.infrastructure.persistence.po;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "mission")
public class MissionPO {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Column(nullable = false)
    private String title;
    @Column(name = "scenario_type", nullable = false)
    private String scenarioType;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getScenarioType() { return scenarioType; }
    public void setScenarioType(String scenarioType) { this.scenarioType = scenarioType; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
