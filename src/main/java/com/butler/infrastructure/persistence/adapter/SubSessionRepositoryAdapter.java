package com.butler.infrastructure.persistence.adapter;

import com.butler.domain.model.SubSession;
import com.butler.domain.model.SubSessionStatus;
import com.butler.domain.repository.SubSessionRepository;
import com.butler.infrastructure.persistence.archive.ArchiveRecorder;
import com.butler.infrastructure.persistence.jpa.SubSessionJpaRepository;
import com.butler.infrastructure.persistence.po.SubSessionPO;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class SubSessionRepositoryAdapter implements SubSessionRepository {
    private final SubSessionJpaRepository jpa;
    private final ArchiveRecorder archiveRecorder;

    public SubSessionRepositoryAdapter(SubSessionJpaRepository jpa, ArchiveRecorder archiveRecorder) {
        this.jpa = jpa;
        this.archiveRecorder = archiveRecorder;
    }

    @Override
    public SubSession save(SubSession s) {
        SubSessionPO po = new SubSessionPO();
        po.setId(s.getId());
        po.setUserId(s.getUserId());
        po.setMissionId(s.getMissionId());
        po.setScenarioType(s.getScenarioType());
        po.setSessionDesc(s.getSessionDesc());
        po.setCollectedInfo(s.getCollectedInfo());
        po.setCustomFocusLabels(s.getCustomFocusLabels());
        po.setStudyMaterials(s.getStudyMaterials());
        po.setMetricDefs(s.getMetricDefs());
        po.setStatus(s.getStatus().name());
        po.setCreatedAt(s.getCreatedAt());
        SubSessionPO saved = jpa.save(po);
        return toDomain(saved);
    }

    @Override
    public List<SubSession> findByUserId(Long userId) {
        return jpa.findByUserIdOrderByIdDesc(userId).stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<java.time.Instant> findLastUpdatedAt(Long userId) {
        return Optional.ofNullable(jpa.findLastUpdatedAt(userId));
    }

    @Override
    public Optional<SubSession> findById(Long id) {
        return jpa.findById(id).map(this::toDomain);
    }

    @Override
    public void deleteById(Long id) {
        jpa.deleteById(id);
    }

    @Override
    public void archiveAndDeleteById(Long id, String reason) {
        jpa.findById(id).ifPresent(po ->
                archiveRecorder.archive("sub_session", po, po.getUserId(), id, reason));
        jpa.deleteById(id);
    }

    @Override
    public List<SubSession> findByUserIdAndStatus(Long userId, SubSessionStatus status) {
        return jpa.findByUserIdAndStatus(userId, status.name()).stream().map(this::toDomain).toList();
    }

    private SubSession toDomain(SubSessionPO po) {
        SubSession s = new SubSession(po.getId(), po.getUserId(), po.getMissionId(), po.getScenarioType(),
                po.getSessionDesc(), po.getCollectedInfo(), SubSessionStatus.valueOf(po.getStatus()), po.getCreatedAt());
        s.setCustomFocusLabels(po.getCustomFocusLabels());
        s.setStudyMaterials(po.getStudyMaterials());
        s.setMetricDefs(po.getMetricDefs());
        s.setUpdatedAt(po.getUpdatedAt());
        return s;
    }
}
