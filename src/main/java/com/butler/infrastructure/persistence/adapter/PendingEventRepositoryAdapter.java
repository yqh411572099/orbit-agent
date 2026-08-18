package com.butler.infrastructure.persistence.adapter;

import com.butler.domain.model.PendingEvent;
import com.butler.domain.repository.PendingEventRepository;
import com.butler.infrastructure.persistence.jpa.PendingEventJpaRepository;
import com.butler.infrastructure.persistence.po.PendingEventPO;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class PendingEventRepositoryAdapter implements PendingEventRepository {
    private final PendingEventJpaRepository jpa;

    public PendingEventRepositoryAdapter(PendingEventJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public PendingEvent save(PendingEvent e) {
        PendingEventPO po = new PendingEventPO();
        po.setId(e.getId());
        po.setUserId(e.getUserId());
        po.setScope(e.getScope().name());
        po.setSubSessionId(e.getSubSessionId());
        po.setEventType(e.getEventType());
        po.setPayload(e.getPayload());
        po.setPreview(e.getPreview());
        po.setStatus(e.getStatus().name());
        po.setExpiresAt(e.getExpiresAt());
        po.setCreatedAt(e.getCreatedAt() == null ? Instant.now() : e.getCreatedAt());
        po.setUpdatedAt(e.getUpdatedAt() == null ? Instant.now() : e.getUpdatedAt());
        return toDomain(jpa.save(po));
    }

    @Override
    public Optional<PendingEvent> findById(String id) {
        return jpa.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<Instant> findLastUpdatedAt(Long userId) {
        return Optional.ofNullable(jpa.findLastUpdatedAt(userId));
    }

    @Override
    public List<PendingEvent> findPendingByUser(Long userId, PendingEvent.Scope scope, Long subSessionId) {
        Instant now = Instant.now();
        List<PendingEventPO> rows = subSessionId == null
                ? jpa.findByUserIdAndScopeAndStatusOrderByCreatedAtDesc(userId, scope.name(), PendingEvent.Status.PENDING.name())
                : jpa.findByUserIdAndScopeAndSubSessionIdAndStatusOrderByCreatedAtDesc(
                        userId, scope.name(), subSessionId, PendingEvent.Status.PENDING.name());
        return rows.stream().map(this::toDomain).filter(e -> !now.isAfter(e.getExpiresAt())).toList();
    }

    @Override
    public Optional<PendingEvent> findLatestPendingByUser(Long userId, PendingEvent.Scope scope, Long subSessionId) {
        return findPendingByUser(userId, scope, subSessionId).stream().findFirst();
    }

    private PendingEvent toDomain(PendingEventPO po) {
        PendingEvent e = new PendingEvent(po.getId(), po.getUserId(), PendingEvent.Scope.valueOf(po.getScope()),
                po.getSubSessionId(), po.getEventType(), po.getPayload(),
                PendingEvent.Status.valueOf(po.getStatus()), po.getExpiresAt(), po.getCreatedAt(), po.getUpdatedAt());
        e.setPreview(po.getPreview());
        return e;
    }
}
