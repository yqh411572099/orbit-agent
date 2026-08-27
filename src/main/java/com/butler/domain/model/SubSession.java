package com.butler.domain.model;

import java.time.Instant;

public class SubSession {
    private final Long id;
    private final Long userId;
    private final Long missionId;
    private final String scenarioType;
    private String sessionDesc;
    private String collectedInfo;
    private String customFocusLabels;
    private String studyMaterials;
    /** 可视化指标卡定义 JSON：[{key,label,unit,chartType}]，由 LLM 在建目标/对话中声明。 */
    private String metricDefs;
    private SubSessionStatus status;
    private final Instant createdAt;
    private Instant updatedAt;

    public SubSession(Long id, Long userId, Long missionId, String scenarioType,
                      String sessionDesc, SubSessionStatus status, Instant createdAt) {
        this(id, userId, missionId, scenarioType, sessionDesc, null, status, createdAt);
    }

    public SubSession(Long id, Long userId, Long missionId, String scenarioType,
                      String sessionDesc, String collectedInfo, SubSessionStatus status, Instant createdAt) {
        this.id = id;
        this.userId = userId;
        this.missionId = missionId;
        this.scenarioType = scenarioType;
        this.sessionDesc = sessionDesc;
        this.collectedInfo = collectedInfo;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Long getMissionId() { return missionId; }
    public String getScenarioType() { return scenarioType; }
    public String getSessionDesc() { return sessionDesc; }
    public String getCollectedInfo() { return collectedInfo; }
    public void setCollectedInfo(String collectedInfo) { this.collectedInfo = collectedInfo; }
    public String getCustomFocusLabels() { return customFocusLabels; }
    public void setCustomFocusLabels(String customFocusLabels) { this.customFocusLabels = customFocusLabels; }
    public String getStudyMaterials() { return studyMaterials; }
    public void setStudyMaterials(String studyMaterials) { this.studyMaterials = studyMaterials; }
    public String getMetricDefs() { return metricDefs; }
    public void setMetricDefs(String metricDefs) { this.metricDefs = metricDefs; }
    public SubSessionStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public void archive() {
        this.status = SubSessionStatus.ARCHIVED;
    }
}
