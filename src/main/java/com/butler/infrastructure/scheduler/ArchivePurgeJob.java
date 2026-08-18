package com.butler.infrastructure.scheduler;

import com.butler.infrastructure.persistence.archive.ArchiveRecorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 每天清理超过保留期（默认 30 天）的归档快照。 */
@Component
public class ArchivePurgeJob {

    private static final Logger log = LoggerFactory.getLogger(ArchivePurgeJob.class);

    private final ArchiveRecorder archiveRecorder;
    private final int retentionDays;

    public ArchivePurgeJob(ArchiveRecorder archiveRecorder,
                           @Value("${butler.archive.retention-days:30}") int retentionDays) {
        this.archiveRecorder = archiveRecorder;
        this.retentionDays = retentionDays;
    }

    @Scheduled(cron = "${butler.archive.purge-cron:0 30 3 * * *}")
    public void run() {
        int removed = archiveRecorder.purgeExpired(retentionDays);
        if (removed > 0) {
            log.info("清理过期归档 {} 条（保留 {} 天）", removed, retentionDays);
        }
    }
}
