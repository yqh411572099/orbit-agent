package com.butler.infrastructure.persistence.adapter;

import com.butler.domain.model.MetricPoint;
import com.butler.domain.repository.MetricPointRepository;
import com.butler.infrastructure.persistence.archive.ArchiveRecorder;
import com.butler.infrastructure.persistence.jpa.MetricPointJpaRepository;
import com.butler.infrastructure.persistence.po.MetricPointPO;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class MetricPointRepositoryAdapter implements MetricPointRepository {
    private final MetricPointJpaRepository jpa;
    private final ArchiveRecorder archiveRecorder;

    public MetricPointRepositoryAdapter(MetricPointJpaRepository jpa, ArchiveRecorder archiveRecorder) {
        this.jpa = jpa;
        this.archiveRecorder = archiveRecorder;
    }

    @Override
    public MetricPoint save(MetricPoint p) {
        MetricPointPO po = new MetricPointPO();
        po.setId(p.getId());
        po.setSubSessionId(p.getSubSessionId());
        po.setUserId(p.getUserId());
        po.setMetricKey(p.getMetricKey());
        po.setLabel(p.getLabel());
        po.setValue(p.getValue());
        po.setUnit(p.getUnit());
        po.setValueDate(p.getValueDate());
        if (p.getCreatedAt() != null) po.setCreatedAt(p.getCreatedAt());
        return toDomain(jpa.save(po));
    }

    @Override
    public void delete(MetricPoint p) {
        if (p.getId() != null) jpa.deleteById(p.getId());
    }

    @Override
    public List<MetricPoint> findBySubSessionId(Long subSessionId) {
        return jpa.findBySubSessionIdOrderByValueDateAscIdAsc(subSessionId).stream()
                .map(this::toDomain).toList();
    }

    @Override
    public Optional<Instant> findLastUpdatedAt(Long subSessionId) {
        return Optional.ofNullable(jpa.findLastUpdatedAt(subSessionId));
    }

    @Override
    public int deleteBySubSessionId(Long subSessionId) {
        List<MetricPointPO> rows = jpa.findBySubSessionIdOrderByValueDateAscIdAsc(subSessionId);
        jpa.deleteBySubSessionId(subSessionId);
        return rows.size();
    }

    @Override
    public int archiveAndDeleteBySubSessionId(Long subSessionId, Long userId, String reason) {
        List<MetricPointPO> rows = jpa.findBySubSessionIdOrderByValueDateAscIdAsc(subSessionId);
        archiveRecorder.archiveAll("metric_point", rows, userId, subSessionId, reason);
        jpa.deleteBySubSessionId(subSessionId);
        return rows.size();
    }

    private MetricPoint toDomain(MetricPointPO po) {
        return new MetricPoint(po.getId(), po.getSubSessionId(), po.getUserId(), po.getMetricKey(),
                po.getLabel(), po.getValue(), po.getUnit(), po.getValueDate(), po.getCreatedAt());
    }
}
