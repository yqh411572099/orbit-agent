package com.butler.domain.model;

import com.butler.domain.attribute.Attribute;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * 一条“提炼后的长期记忆”。
 *
 * <p>外层只保留通用字段（分类、主体、时间、地点、有效期、置信度）；
 * 场景特有结构放在 {@link #attributes}（集合），每个元素是某个域定义的强类型 Attribute，
 * LLM 可在其中追加未定义字段（透传，不丢失）。</p>
 */
public class UserMemory {

    private final Long id;
    private final Long userId;
    private final MemoryCategory category;
    private final String content;

    /** 主体角色标识，如 self/partner。 */
    private final String subject;
    /** 主体画像，如 {"role":"准爸爸","relatedParty":"孕妇"}。 */
    private final String subjectProfile;
    private final LocalDate eventDate;
    private final LocalDate validFrom;
    private final LocalDate validTo;
    private final String location;
    private final Double confidence;

    /** 场景自定义结构化属性集合（可多个，多态序列化，带 type 字段）。 */
    private final List<Attribute> attributes;

    private final Long sourceRawLogId;
    private final Instant createdAt;

    public UserMemory(Long id, Long userId, MemoryCategory category, String content,
                      Long sourceRawLogId, Instant createdAt) {
        this(id, userId, category, content, null, null, null, null, null, null, null,
                List.of(), sourceRawLogId, createdAt);
    }

    public UserMemory(Long id, Long userId, MemoryCategory category, String content,
                      String subject, String subjectProfile, LocalDate eventDate,
                      LocalDate validFrom, LocalDate validTo, String location, Double confidence,
                      List<Attribute> attributes, Long sourceRawLogId, Instant createdAt) {
        this.id = id;
        this.userId = userId;
        this.category = category == null ? MemoryCategory.CONTEXT : category;
        this.content = content;
        this.subject = subject;
        this.subjectProfile = subjectProfile;
        this.eventDate = eventDate;
        this.validFrom = validFrom;
        this.validTo = validTo;
        this.location = location;
        this.confidence = confidence;
        this.attributes = attributes == null ? List.of() : attributes;
        this.sourceRawLogId = sourceRawLogId;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public MemoryCategory getCategory() { return category; }
    public String getContent() { return content; }
    public String getSubject() { return subject; }
    public String getSubjectProfile() { return subjectProfile; }
    public LocalDate getEventDate() { return eventDate; }
    public LocalDate getValidFrom() { return validFrom; }
    public LocalDate getValidTo() { return validTo; }
    public String getLocation() { return location; }
    public Double getConfidence() { return confidence; }
    public List<Attribute> getAttributes() { return attributes; }
    public Long getSourceRawLogId() { return sourceRawLogId; }
    public Instant getCreatedAt() { return createdAt; }

    public boolean isValidOn(LocalDate today) {
        if (validFrom != null && today.isBefore(validFrom)) return false;
        if (validTo != null && today.isAfter(validTo)) return false;
        return true;
    }
}
