package com.butler.application;

import com.butler.domain.model.MainSession;
import com.butler.domain.model.SubSession;
import com.butler.domain.repository.MainSessionRepository;
import com.butler.domain.repository.SubSessionRepository;
import com.butler.infrastructure.persistence.archive.DataDeletionPolicy;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 运维/调试数据清理。
 *
 * <p>{@link #purgeAll} 只清理测试用户（见 {@link DataDeletionPolicy}），供自测脚本使用，
 * 绝不触碰真实用户数据；真实用户的清理走 {@link #purgeUser}，会先归档再删除。</p>
 */
@Service
public class AdminPurgeAppService {

    private static final Logger log = LoggerFactory.getLogger(AdminPurgeAppService.class);

    private final MainSessionRepository mainSessionRepository;
    private final MemoryAdminAppService memoryAdminAppService;
    private final SubSessionAppService subSessionAppService;
    private final SubSessionRepository subSessionRepository;
    private final KnowledgeAppService knowledgeAppService;
    private final DataDeletionPolicy deletionPolicy;

    public AdminPurgeAppService(MainSessionRepository mainSessionRepository,
                                MemoryAdminAppService memoryAdminAppService,
                                SubSessionAppService subSessionAppService,
                                SubSessionRepository subSessionRepository,
                                KnowledgeAppService knowledgeAppService,
                                DataDeletionPolicy deletionPolicy) {

        this.mainSessionRepository = mainSessionRepository;
        this.memoryAdminAppService = memoryAdminAppService;
        this.subSessionAppService = subSessionAppService;
        this.subSessionRepository = subSessionRepository;
        this.knowledgeAppService = knowledgeAppService;
        this.deletionPolicy = deletionPolicy;
    }

    /** 清理所有测试用户的数据（硬删除，不归档）。真实用户被跳过，避免误删。 */
    @Transactional
    public int purgeTestUsers() {
        int removed = 0;
        for (MainSession ms : mainSessionRepository.findAll()) {
            if (!deletionPolicy.isTestUser(ms.getUserId())) {
                log.info("purgeTestUsers 跳过真实用户 userId={}", ms.getUserId());
                continue;
            }
            removed += purgeUser(ms.getUserId());
        }
        return removed;
    }

    /** 清理指定用户：真实用户先归档再删除，测试用户直接硬删。 */
    @Transactional
    public int purgeUser(Long userId) {
        int removed = 0;
        for (SubSession sub : subSessionRepository.findByUserId(userId)) {
            subSessionAppService.purge(sub.getId());
        }
        removed += memoryAdminAppService.clearAllMemories(userId);
        knowledgeAppService.deleteForUser(userId);
        return removed;
    }
}
