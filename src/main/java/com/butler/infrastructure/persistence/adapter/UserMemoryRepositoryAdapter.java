package com.butler.infrastructure.persistence.adapter;

import com.butler.domain.attribute.Attribute;
import com.butler.domain.model.MemoryCategory;
import com.butler.domain.model.UserMemory;
import com.butler.domain.repository.UserMemoryRepository;
import com.butler.infrastructure.persistence.archive.ArchiveRecorder;
import com.butler.infrastructure.persistence.jpa.UserMemoryJpaRepository;
import com.butler.infrastructure.persistence.po.UserMemoryPO;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class UserMemoryRepositoryAdapter implements UserMemoryRepository {
    private final UserMemoryJpaRepository jpa;
    private final ArchiveRecorder archiveRecorder;

    public UserMemoryRepositoryAdapter(UserMemoryJpaRepository jpa, ArchiveRecorder archiveRecorder) {
        this.jpa = jpa;
        this.archiveRecorder = archiveRecorder;
    }

    @Override
    public UserMemory save(UserMemory m) {
        UserMemoryPO po = new UserMemoryPO();
        po.setId(m.getId());
        po.setUserId(m.getUserId());
        po.setCategory(m.getCategory().name());
        po.setContent(m.getContent());
        po.setSubject(m.getSubject());
        po.setSubjectProfile(m.getSubjectProfile());
        po.setEventDate(m.getEventDate());
        po.setValidFrom(m.getValidFrom());
        po.setValidTo(m.getValidTo());
        po.setLocation(m.getLocation());
        po.setConfidence(m.getConfidence());
        po.setAttributes(m.getAttributes() == null ? List.of() : new ArrayList<>(m.getAttributes()));
        po.setSourceRawLogId(m.getSourceRawLogId());
        po.setCreatedAt(m.getCreatedAt());
        return toDomain(jpa.save(po));
    }

    @Override
    public List<UserMemory> findByUserId(Long userId) {
        return jpa.findByUserId(userId).stream().map(this::toDomain).toList();
    }

    @Override
    public List<UserMemory> findByIdIn(List<Long> ids) {
        return jpa.findByIdIn(ids).stream().map(this::toDomain).toList();
    }

    @Override
    public int deleteMemoryById(Long id) {
        return jpa.deleteMemoryById(id);
    }

    @Override
    public int deleteByUserId(Long userId) {
        return jpa.deleteByUserId(userId);
    }

    @Override
    public int archiveAndDeleteByUserId(Long userId, String reason) {
        List<UserMemoryPO> rows = jpa.findByUserId(userId);
        archiveRecorder.archiveAll("user_memory", rows, userId, null, reason);
        return jpa.deleteByUserId(userId);
    }

    @Override
    public int archiveAndDeleteMemoryById(Long id, String reason) {
        Optional<UserMemoryPO> opt = jpa.findById(id);
        if (opt.isEmpty()) return 0;
        UserMemoryPO po = opt.get();
        archiveRecorder.archive("user_memory", po, po.getUserId(), null, reason);
        return jpa.deleteMemoryById(id);
    }

    private UserMemory toDomain(UserMemoryPO po) {
        List<Attribute> attrs = po.getAttributes() == null ? List.of() : po.getAttributes();
        return new UserMemory(po.getId(), po.getUserId(),
                MemoryCategory.safeValueOf(po.getCategory()), po.getContent(),
                po.getSubject(), po.getSubjectProfile(), po.getEventDate(),
                po.getValidFrom(), po.getValidTo(), po.getLocation(), po.getConfidence(),
                attrs, po.getSourceRawLogId(), po.getCreatedAt());
    }
}
