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
 * - 子对话：只读取与自身 subSessionId 绑定的记忆（目标内工作记忆）。
 * - 主对话（日常对话）：只读【未绑定到任何子对话】的全局记忆——用户偏好、用户档案、跨目标事实；
 *   已绑定到某子对话的记忆属于该目标的进度/目标内事实，不注入主对话上下文（避免闲聊时串出某个目标的数据）。
 * - 主对话（全局复盘）：可读全部记忆，见 {@link #readableMemoriesForReview}。
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

    /** 主对话全局复盘：读取该用户全部有效记忆（含绑定到各子对话的目标进度）。 */
    public List<UserMemory> readableMemoriesForReview(Long userId) {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
        return userMemoryRepository.findByUserId(userId).stream()
                .filter(m -> m.isValidOn(today))
                .toList();
    }

    public List<UserMemory> readableMemories(Long userId, SessionType sessionType, Long subSessionId) {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
        if (sessionType == SessionType.MAIN) {
            java.util.Set<Long> bound = relRepository.findAll().stream()
                    .map(MemorySessionRel::getMemoryId)
                    .collect(java.util.stream.Collectors.toSet());
            return userMemoryRepository.findByUserId(userId).stream()
                    .filter(m -> m.isValidOn(today))
                    .filter(m -> !bound.contains(m.getId()))
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
