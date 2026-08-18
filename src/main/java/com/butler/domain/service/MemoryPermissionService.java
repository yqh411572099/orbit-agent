package com.butler.domain.service;

import com.butler.domain.model.MemorySessionRel;
import com.butler.domain.model.SessionType;
import com.butler.domain.model.UserMemory;
import com.butler.domain.repository.MemorySessionRelRepository;
import com.butler.domain.repository.UserMemoryRepository;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 记忆读取隔离规则：
 * - 主对话：读取该用户全部记忆
 * - 子对话：只读取与自身 subSessionId 绑定的记忆
 */
@Service
public class MemoryPermissionService {

    private final UserMemoryRepository userMemoryRepository;
    private final MemorySessionRelRepository relRepository;

    public MemoryPermissionService(UserMemoryRepository userMemoryRepository,
                                   MemorySessionRelRepository relRepository) {
        this.userMemoryRepository = userMemoryRepository;
        this.relRepository = relRepository;
    }

    public List<UserMemory> readableMemories(Long userId, SessionType sessionType, Long subSessionId) {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
        if (sessionType == SessionType.MAIN) {
            return userMemoryRepository.findByUserId(userId).stream()
                    .filter(m -> m.isValidOn(today))
                    .toList();
        }
        if (subSessionId == null) {
            return Collections.emptyList();
        }
        List<Long> memoryIds = relRepository.findBySubSessionId(subSessionId).stream()
                .map(MemorySessionRel::getMemoryId)
                .toList();
        if (memoryIds.isEmpty()) {
            return Collections.emptyList();
        }
        return userMemoryRepository.findByIdIn(memoryIds).stream()
                .filter(m -> m.getUserId().equals(userId))
                .filter(m -> m.isValidOn(today))
                .toList();
    }
}
