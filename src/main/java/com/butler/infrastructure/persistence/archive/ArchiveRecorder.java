package com.butler.infrastructure.persistence.archive;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.lang.reflect.Method;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 归档记录器：删除真实用户数据前，把原 PO 以 JSON 快照写入 entity_archive。
 * 测试用户（见 {@link DataDeletionPolicy}）不写归档，直接物理删除即可。
 */
@Component
public class ArchiveRecorder {

    private static final Logger log = LoggerFactory.getLogger(ArchiveRecorder.class);

    private final EntityArchiveJpaRepository archiveRepository;
    private final DataDeletionPolicy policy;
    private final ObjectMapper objectMapper;

    public ArchiveRecorder(EntityArchiveJpaRepository archiveRepository,
                           DataDeletionPolicy policy,
                           ObjectMapper objectMapper) {
        this.archiveRepository = archiveRepository;
        this.policy = policy;
        // 归档快照需要序列化 Instant/LocalDate，使用自带 JSR310 的独立 mapper，避免影响 Spring AI 复用的全局 mapper。
        ObjectMapper m = objectMapper != null ? objectMapper.copy() : new ObjectMapper();
        m.registerModule(new JavaTimeModule());
        m.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.objectMapper = m;
    }

    /** 判断该用户是否需要归档（测试用户直接硬删）。 */
    public boolean shouldArchive(Long userId) {
        return !policy.isTestUser(userId);
    }

    public void archive(String entityType, Object po, Long userId, Long subSessionId, String reason) {
        if (po == null || !shouldArchive(userId)) return;
        try {
            EntityArchivePO row = new EntityArchivePO();
            row.setEntityType(entityType);
            row.setOriginalId(readId(po));
            row.setUserId(userId);
            row.setSubSessionId(subSessionId);
            row.setSnapshot(objectMapper.writeValueAsString(po));
            row.setReason(reason);
            row.setArchivedAt(Instant.now());
            archiveRepository.save(row);
        } catch (Exception e) {
            log.error("归档失败 entityType={} po={} err={}", entityType, po.getClass().getSimpleName(), e.getMessage());
            throw new IllegalStateException("数据归档失败，已中止删除: " + e.getMessage(), e);
        }
    }

    public void archiveAll(String entityType, List<?> pos, Long userId, Long subSessionId, String reason) {
        if (pos == null || pos.isEmpty() || !shouldArchive(userId)) return;
        for (Object po : pos) {
            archive(entityType, po, userId, subSessionId, reason);
        }
    }

    /** 清理超过保留天数的归档（定时任务每日调用）。 */
    public int purgeExpired(int retentionDays) {
        Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
        return archiveRepository.deleteByArchivedAtBefore(cutoff);
    }

    private Long readId(Object po) {
        try {
            Method m = po.getClass().getMethod("getId");
            Object v = m.invoke(po);
            return v == null ? null : ((Number) v).longValue();
        } catch (Exception e) {
            return null;
        }
    }
}
