package com.butler.infrastructure.scheduler;

import com.butler.application.MemoryExtractionAppService;
import com.butler.domain.repository.MainSessionRepository;
import com.butler.domain.repository.SubSessionRepository;
import com.butler.domain.model.MainSession;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 全局唯一定时任务：每 2 小时对每个用户执行增量记忆提炼。 */
@Component
public class MemoryExtractionJob {

    private static final Logger log = LoggerFactory.getLogger(MemoryExtractionJob.class);

    private final MemoryExtractionAppService extractionService;
    private final MainSessionRepository mainSessionRepository;

    public MemoryExtractionJob(MemoryExtractionAppService extractionService,
                               MainSessionRepository mainSessionRepository) {
        this.extractionService = extractionService;
        this.mainSessionRepository = mainSessionRepository;
    }

    @Scheduled(cron = "${butler.memory.cron:0 0 */2 * * *}")
    public void run() {
        Instant to = Instant.now();
        Instant from = to.minus(2, ChronoUnit.HOURS);
        log.info("开始增量记忆提炼窗口 [{} ~ {}]", from, to);
        mainSessionRepository.findAll().forEach(ms -> runOne(ms, from, to));
    }

    public void runOne(Long userId) {
        Instant to = Instant.now();
        Instant from = to.minus(2, ChronoUnit.HOURS);
        mainSessionRepository.findByUserId(userId).ifPresent(ms -> runOne(ms, from, to));
    }

    private void runOne(MainSession ms, Instant from, Instant to) {
        if (ms == null) return;
        try {
            extractionService.runForUser(ms.getUserId(), from, to);
        } catch (Exception e) {
            log.warn("用户记忆提炼失败，等待下一轮重试 userId={} err={}", ms.getUserId(), e.getMessage());
        }
    }
}
