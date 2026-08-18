package com.butler.infrastructure.persistence.adapter;

import com.butler.domain.model.MemorySessionRel;
import com.butler.domain.repository.MemorySessionRelRepository;
import com.butler.infrastructure.persistence.archive.ArchiveRecorder;
import com.butler.infrastructure.persistence.jpa.MemorySessionRelJpaRepository;
import com.butler.infrastructure.persistence.po.MemorySessionRelPO;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class MemorySessionRelRepositoryAdapter implements MemorySessionRelRepository {
    private final MemorySessionRelJpaRepository jpa;
    private final ArchiveRecorder archiveRecorder;

    public MemorySessionRelRepositoryAdapter(MemorySessionRelJpaRepository jpa, ArchiveRecorder archiveRecorder) {
        this.jpa = jpa;
        this.archiveRecorder = archiveRecorder;
    }

    @Override
    public MemorySessionRel save(MemorySessionRel r) {
        MemorySessionRelPO po = new MemorySessionRelPO();
        po.setId(r.getId());
        po.setMemoryId(r.getMemoryId());
        po.setSubSessionId(r.getSubSessionId());
        po.setBindTime(r.getBindTime());
        MemorySessionRelPO saved = jpa.save(po);
        return new MemorySessionRel(saved.getId(), saved.getMemoryId(), saved.getSubSessionId(), saved.getBindTime());
    }

    @Override
    public List<MemorySessionRel> findByMemoryId(Long memoryId) {
        return jpa.findByMemoryId(memoryId).stream()
                .map(po -> new MemorySessionRel(po.getId(), po.getMemoryId(), po.getSubSessionId(), po.getBindTime()))
                .toList();
    }

    @Override
    public List<MemorySessionRel> findBySubSessionId(Long subSessionId) {
        return jpa.findBySubSessionId(subSessionId).stream()
                .map(po -> new MemorySessionRel(po.getId(), po.getMemoryId(), po.getSubSessionId(), po.getBindTime()))
                .toList();
    }

    @Override
    public boolean existsByMemoryIdAndSubSessionId(Long memoryId, Long subSessionId) {
        return jpa.existsByMemoryIdAndSubSessionId(memoryId, subSessionId);
    }

    @Override
    public void deleteBySubSessionId(Long subSessionId) {
        jpa.deleteBySubSessionId(subSessionId);
    }

    @Override
    public void deleteByMemoryId(Long memoryId) {
        jpa.deleteByMemoryId(memoryId);
    }

    @Override
    public void archiveAndDeleteBySubSessionId(Long subSessionId, Long userId, String reason) {
        List<MemorySessionRelPO> rows = jpa.findBySubSessionId(subSessionId);
        archiveRecorder.archiveAll("memory_session_rel", rows, userId, subSessionId, reason);
        jpa.deleteBySubSessionId(subSessionId);
    }

    @Override
    public void archiveAndDeleteByMemoryId(Long memoryId, Long userId, String reason) {
        List<MemorySessionRelPO> rows = jpa.findByMemoryId(memoryId);
        archiveRecorder.archiveAll("memory_session_rel", rows, userId, null, reason);
        jpa.deleteByMemoryId(memoryId);
    }
}
