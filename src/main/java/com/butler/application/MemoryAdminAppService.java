package com.butler.application;

import com.butler.domain.model.MemorySessionRel;
import com.butler.domain.model.UserMemory;
import com.butler.domain.repository.MemorySessionRelRepository;
import com.butler.domain.repository.RawChatLogRepository;
import com.butler.domain.repository.UserMemoryRepository;
import jakarta.transaction.Transactional;
import java.util.List;
import org.springframework.stereotype.Service;

/** 记忆数据管理（运维/调试用）：真实用户数据删除前先归档，测试用户直接硬删。 */
@Service
public class MemoryAdminAppService {

    private final UserMemoryRepository userMemoryRepository;
    private final MemorySessionRelRepository relRepository;
    private final RawChatLogRepository rawChatLogRepository;

    public MemoryAdminAppService(UserMemoryRepository userMemoryRepository,
                                 MemorySessionRelRepository relRepository,
                                 RawChatLogRepository rawChatLogRepository) {
        this.userMemoryRepository = userMemoryRepository;
        this.relRepository = relRepository;
        this.rawChatLogRepository = rawChatLogRepository;
    }

    @Transactional
    public int clearAllMemories(Long userId) {
        List<UserMemory> existing = userMemoryRepository.findByUserId(userId);
        for (UserMemory m : existing) {
            relRepository.archiveAndDeleteByMemoryId(m.getId(), userId, "CLEAR_MEMORY");
        }
        int memoryCount = userMemoryRepository.archiveAndDeleteByUserId(userId, "CLEAR_MEMORY");
        int chatCount = rawChatLogRepository.archiveAndDeleteByUserId(userId, "CLEAR_MEMORY");
        return memoryCount + chatCount;
    }

    @Transactional
    public int deleteMemoriesByIds(List<Long> memoryIds) {
        int removed = 0;
        List<UserMemory> targets = userMemoryRepository.findByIdIn(memoryIds);
        for (UserMemory m : targets) {
            relRepository.archiveAndDeleteByMemoryId(m.getId(), m.getUserId(), "DELETE_MEMORY");
            removed += userMemoryRepository.archiveAndDeleteMemoryById(m.getId(), "DELETE_MEMORY");
        }
        return removed;
    }
}
