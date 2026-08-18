package com.butler.infrastructure.persistence.po;

import com.butler.infrastructure.persistence.converter.AttributeListConverter;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "user_memory", indexes = {
        @Index(name = "idx_user_memory", columnList = "user_id")
})
public class UserMemoryPO {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Column(nullable = false, length = 20, columnDefinition = "varchar(20) default 'CONTEXT'")
    private String category = "CONTEXT";
    @Lob @Column(nullable = false)
    private String content;

    @Column(length = 32) private String subject;
    @Column(name = "subject_profile") private String subjectProfile;
    @Column(name = "event_date") private LocalDate eventDate;
    @Column(name = "valid_from") private LocalDate validFrom;
    @Column(name = "valid_to") private LocalDate validTo;
    private String location;
    private Double confidence;

    @Lob
    @Convert(converter = AttributeListConverter.class)
    @Column(name = "attributes")
    private java.util.List<com.butler.domain.attribute.Attribute> attributes;

    @Column(name = "source_raw_log_id")
    private Long sourceRawLogId;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }
    public String getSubjectProfile() { return subjectProfile; }
    public void setSubjectProfile(String subjectProfile) { this.subjectProfile = subjectProfile; }
    public LocalDate getEventDate() { return eventDate; }
    public void setEventDate(LocalDate eventDate) { this.eventDate = eventDate; }
    public LocalDate getValidFrom() { return validFrom; }
    public void setValidFrom(LocalDate validFrom) { this.validFrom = validFrom; }
    public LocalDate getValidTo() { return validTo; }
    public void setValidTo(LocalDate validTo) { this.validTo = validTo; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public Double getConfidence() { return confidence; }
    public void setConfidence(Double confidence) { this.confidence = confidence; }
    public java.util.List<com.butler.domain.attribute.Attribute> getAttributes() { return attributes; }
    public void setAttributes(java.util.List<com.butler.domain.attribute.Attribute> attributes) { this.attributes = attributes; }
    public Long getSourceRawLogId() { return sourceRawLogId; }
    public void setSourceRawLogId(Long sourceRawLogId) { this.sourceRawLogId = sourceRawLogId; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
