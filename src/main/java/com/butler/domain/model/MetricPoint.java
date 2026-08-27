package com.butler.domain.model;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 子对话“可视化插件/指标卡”的一个数据点。
 *
 * <p>由 LLM 在用户汇报数值时增量写入（如“今天体重 78.5kg”“这次模考 128 分”）。
 * 子对话头部展示每个指标的最新值；点击后按 metricDef 声明的 chartType 渲染历史图。</p>
 */
public class MetricPoint {
    private final Long id;
    private final Long subSessionId;
    private final Long userId;
    /** 指标 key，如 weight / bodyFat / mockScore；同一子对话内唯一标识一条序列。 */
    private final String metricKey;
    private final String label;
    /** 数值；按 valueDate 升序构成一条时间序列。 */
    private final Double value;
    private final String unit;
    /** 该点归属日期（用户汇报的“今天/某日”）。 */
    private final LocalDate valueDate;
    private final Instant createdAt;

    public MetricPoint(Long id, Long subSessionId, Long userId, String metricKey, String label,
                       Double value, String unit, LocalDate valueDate, Instant createdAt) {
        this.id = id;
        this.subSessionId = subSessionId;
        this.userId = userId;
        this.metricKey = metricKey;
        this.label = label;
        this.value = value;
        this.unit = unit;
        this.valueDate = valueDate;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public Long getSubSessionId() { return subSessionId; }
    public Long getUserId() { return userId; }
    public String getMetricKey() { return metricKey; }
    public String getLabel() { return label; }
    public Double getValue() { return value; }
    public String getUnit() { return unit; }
    public LocalDate getValueDate() { return valueDate; }
    public Instant getCreatedAt() { return createdAt; }
}
