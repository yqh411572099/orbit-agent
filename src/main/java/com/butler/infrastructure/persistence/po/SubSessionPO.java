package com.butler.infrastructure.persistence.po;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "sub_session")
public class SubSessionPO {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Column(name = "mission_id")
    private Long missionId;
    @Column(name = "scenario_type", nullable = false)
    private String scenarioType;
    @Lob @Column(name = "session_desc")
    private String sessionDesc;
    @Lob @Column(name = "collected_info")
    private String collectedInfo;
    @Lob @Column(name = "custom_focus_labels")
    private String customFocusLabels;
    @Lob @Column(name = "study_materials")
    private String studyMaterials;
    @Lob @Column(name = "metric_defs")
    private String metricDefs;
    @Column(nullable = false)
    private String status = "ACTIVE";
    @Column(name = "info_source_mode", length = 32)
    private String infoSourceMode;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @PrePersist
    @PreUpdate
    void touch() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        this.updatedAt = now;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getMissionId() { return missionId; }
    public void setMissionId(Long missionId) { this.missionId = missionId; }
    public String getScenarioType() { return scenarioType; }
    public void setScenarioType(String scenarioType) { this.scenarioType = scenarioType; }
    public String getSessionDesc() { return sessionDesc; }
    public void setSessionDesc(String sessionDesc) { this.sessionDesc = sessionDesc; }
    public String getCollectedInfo() { return collectedInfo; }
    public void setCollectedInfo(String collectedInfo) { this.collectedInfo = collectedInfo; }
    public String getCustomFocusLabels() { return customFocusLabels; }
    public void setCustomFocusLabels(String customFocusLabels) { this.customFocusLabels = customFocusLabels; }
    public String getStudyMaterials() { return studyMaterials; }
    public void setStudyMaterials(String studyMaterials) { this.studyMaterials = studyMaterials; }
    public String getMetricDefs() { return metricDefs; }
    public void setMetricDefs(String metricDefs) { this.metricDefs = metricDefs; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getInfoSourceMode() { return infoSourceMode; }
    public void setInfoSourceMode(String infoSourceMode) { this.infoSourceMode = infoSourceMode; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
