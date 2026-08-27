package com.butler.infrastructure.persistence.po;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "task")
public class TaskPO {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "sub_session_id", nullable = false)
    private Long subSessionId;
    @Column(nullable = false)
    private String content;
    @Column(name = "focus_area", length = 64)
    private String focusArea;
    @Column(length = 2000)
    private String detail;
    @Column(name = "module_key", length = 64)
    private String moduleKey;
    @Column(name = "milestone_key", length = 64)
    private String milestoneKey;
    @Column(length = 16)
    private String recurrence;
    @Column(name = "next_hint", length = 1000)
    private String nextHint;
    @Column(nullable = false)
    private boolean completed = false;
    @Column(name = "remind_at")
    private Instant remindAt;
    @Column(name = "due_date")
    private LocalDate dueDate;
    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean reminded = false;
    /** 到点提醒时 LLM 动态生成内容的指令；为空表示纯静态提醒。 */
    @Column(name = "ai_brief", length = 1000)
    private String aiBrief;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @PrePersist
    @PreUpdate
    void touch() { this.updatedAt = Instant.now(); }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getSubSessionId() { return subSessionId; }
    public void setSubSessionId(Long subSessionId) { this.subSessionId = subSessionId; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getFocusArea() { return focusArea; }
    public void setFocusArea(String focusArea) { this.focusArea = focusArea; }
    public boolean isCompleted() { return completed; }
    public void setCompleted(boolean completed) { this.completed = completed; }
    public Instant getRemindAt() { return remindAt; }
    public void setRemindAt(Instant remindAt) { this.remindAt = remindAt; }
    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }
    public boolean isReminded() { return reminded; }
    public void setReminded(boolean reminded) { this.reminded = reminded; }
    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }
    public String getModuleKey() { return moduleKey; }
    public void setModuleKey(String moduleKey) { this.moduleKey = moduleKey; }
    public String getMilestoneKey() { return milestoneKey; }
    public void setMilestoneKey(String milestoneKey) { this.milestoneKey = milestoneKey; }
    public String getNextHint() { return nextHint; }
    public void setNextHint(String nextHint) { this.nextHint = nextHint; }
    public String getRecurrence() { return recurrence; }
    public void setRecurrence(String recurrence) { this.recurrence = recurrence; }
    public String getAiBrief() { return aiBrief; }
    public void setAiBrief(String aiBrief) { this.aiBrief = aiBrief; }
}
