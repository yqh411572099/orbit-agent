package com.butler.infrastructure.persistence.adapter;

import com.butler.domain.model.RawChatLog;
import com.butler.domain.model.SessionType;
import com.butler.domain.repository.RawChatLogRepository;
import com.butler.infrastructure.persistence.archive.ArchiveRecorder;
import com.butler.infrastructure.persistence.jpa.RawChatLogJpaRepository;
import com.butler.infrastructure.persistence.po.RawChatLogPO;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class RawChatLogRepositoryAdapter implements RawChatLogRepository {
    private final RawChatLogJpaRepository jpa;
    private final ArchiveRecorder archiveRecorder;

    public RawChatLogRepositoryAdapter(RawChatLogJpaRepository jpa, ArchiveRecorder archiveRecorder) {
        this.jpa = jpa;
        this.archiveRecorder = archiveRecorder;
    }

    @Override
    public RawChatLog save(RawChatLog l) {
        RawChatLogPO po = new RawChatLogPO();
        po.setId(l.getId());
        po.setUserId(l.getUserId());
        po.setSessionType(l.getSessionType().name());
        po.setSubSessionId(l.getSubSessionId());
        po.setRole(l.getRole());
        po.setContent(l.getContent());
        po.setReasoning(l.getReasoning());
        po.setCreatedAt(l.getCreatedAt());
        RawChatLogPO saved = jpa.save(po);
        return new RawChatLog(saved.getId(), saved.getUserId(), SessionType.valueOf(saved.getSessionType()),
                saved.getSubSessionId(), saved.getRole(), saved.getContent(), saved.getReasoning(), saved.getCreatedAt());
    }

    @Override
    public int deleteBySubSessionId(Long subSessionId) {
        return jpa.deleteBySubSessionId(subSessionId);
    }

    @Override
    public int deleteByUserId(Long userId) {
        return jpa.deleteByUserId(userId);
    }

    @Override
    public int archiveAndDeleteBySubSessionId(Long subSessionId, Long userId, String reason) {
        List<RawChatLogPO> rows = jpa.findBySubSessionId(subSessionId);
        archiveRecorder.archiveAll("raw_chat_log", rows, userId, subSessionId, reason);
        return jpa.deleteBySubSessionId(subSessionId);
    }

    @Override
    public int archiveAndDeleteByUserId(Long userId, String reason) {
        List<RawChatLogPO> rows = jpa.findByUserId(userId);
        archiveRecorder.archiveAll("raw_chat_log", rows, userId, null, reason);
        return jpa.deleteByUserId(userId);
    }

    @Override
    public List<RawChatLog> findByUserIdAndCreatedAtBetween(Long userId, Instant from, Instant to) {
        return jpa.findByUserIdAndCreatedAtBetween(userId, from, to).stream()
                .map(po -> new RawChatLog(po.getId(), po.getUserId(), SessionType.valueOf(po.getSessionType()),
                        po.getSubSessionId(), po.getRole(), po.getContent(), po.getReasoning(), po.getCreatedAt()))
                .toList();
    }

    private RawChatLog map(RawChatLogPO po) {
        return new RawChatLog(po.getId(), po.getUserId(), SessionType.valueOf(po.getSessionType()),
                po.getSubSessionId(), po.getRole(), po.getContent(), po.getReasoning(), po.getCreatedAt());
    }

    @Override
    public List<RawChatLog> findRecent(Long userId, SessionType type, Long subSessionId, int limit) {
        List<RawChatLogPO> rows = jpa.findRecent(userId, type.name(), subSessionId,
                org.springframework.data.domain.PageRequest.of(0, limit));
        List<RawChatLog> out = rows.stream().map(this::map)
                .sorted(java.util.Comparator.comparing(RawChatLog::getId)).toList();
        return out;
    }

    @Override
    public List<RawChatLog> findNewer(Long userId, SessionType type, Long subSessionId, Long afterId) {
        return jpa.findNewer(userId, type.name(), subSessionId, afterId).stream().map(this::map).toList();
    }

    @Override
    public List<RawChatLog> findOlder(Long userId, SessionType type, Long subSessionId, Long beforeId, int limit) {
        return jpa.findOlder(userId, type.name(), subSessionId, beforeId,
                org.springframework.data.domain.PageRequest.of(0, limit)).stream().map(this::map).toList();
    }
}
