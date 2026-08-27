package com.butler.infrastructure.persistence.po;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "metric_point",
        uniqueConstraints = @UniqueConstraint(name = "uk_metric_sub_key_date",
                columnNames = {"sub_session_id", "metric_key", "value_date"}))
public class MetricPointPO {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "sub_session_id", nullable = false)
    private Long subSessionId;
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Column(name = "metric_key", nullable = false, length = 64)
    private String metricKey;
    @Column(length = 64)
    private String label;
    @Column(name = "metric_value")
    private Double value;
    @Column(length = 24)
    private String unit;
    @Column(name = "value_date", nullable = false)
    private LocalDate valueDate;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getSubSessionId() { return subSessionId; }
    public void setSubSessionId(Long subSessionId) { this.subSessionId = subSessionId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getMetricKey() { return metricKey; }
    public void setMetricKey(String metricKey) { this.metricKey = metricKey; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public Double getValue() { return value; }
    public void setValue(Double value) { this.value = value; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public LocalDate getValueDate() { return valueDate; }
    public void setValueDate(LocalDate valueDate) { this.valueDate = valueDate; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
