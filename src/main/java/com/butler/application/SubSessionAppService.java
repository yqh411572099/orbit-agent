package com.butler.application;

import com.butler.domain.model.SubSession;
import com.butler.domain.model.SubSessionStatus;
import com.butler.domain.repository.MemorySessionRelRepository;
import com.butler.domain.repository.RawChatLogRepository;
import com.butler.domain.repository.SubSessionRepository;
import com.butler.domain.repository.TaskRepository;
import jakarta.transaction.Transactional;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * 子对话生命周期管理。
 * 默认采用“归档”语义：从列表/活跃场景中移除，但保留对话、任务与记忆，支持历史复盘。
 */
@Service
public class SubSessionAppService {

    private final SubSessionRepository subSessionRepository;
    private final TaskRepository taskRepository;
    private final MemorySessionRelRepository relRepository;
    private final RawChatLogRepository rawChatLogRepository;
    private final KnowledgeAppService knowledgeAppService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SubSessionAppService(SubSessionRepository subSessionRepository,
                                TaskRepository taskRepository,
                                MemorySessionRelRepository relRepository,
                                RawChatLogRepository rawChatLogRepository,
                                KnowledgeAppService knowledgeAppService) {

        this.subSessionRepository = subSessionRepository;
        this.taskRepository = taskRepository;
        this.relRepository = relRepository;
        this.rawChatLogRepository = rawChatLogRepository;
        this.knowledgeAppService = knowledgeAppService;
    }

    @Transactional
    public SubSession archive(Long subSessionId) {
        SubSession sub = subSessionRepository.findById(subSessionId)
                .orElseThrow(() -> new IllegalArgumentException("子对话不存在: " + subSessionId));
        sub.archive();
        return subSessionRepository.save(sub);
    }

    /** 子对话的学习资料链接（调研时联网核实），无则空列表。 */
    public List<Map<String, String>> getStudyMaterials(Long subSessionId) {
        SubSession sub = subSessionRepository.findById(subSessionId).orElse(null);
        if (sub == null || sub.getStudyMaterials() == null || sub.getStudyMaterials().isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(sub.getStudyMaterials(),
                    new TypeReference<List<Map<String, String>>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    /**
     * 删除子对话及其全部相关数据。
     * 真实用户：任务/对话/关联/知识先快照到 entity_archive 再物理删除（子对话元数据本身也归档）；
     * 测试用户：直接物理删除。记忆（user_memory）是全局中立的，不在此删除。
     */
    @Transactional
    public void purge(Long subSessionId) {
        SubSession sub = subSessionRepository.findById(subSessionId)
                .orElseThrow(() -> new IllegalArgumentException("子对话不存在: " + subSessionId));
        Long userId = sub.getUserId();
        taskRepository.archiveAndDeleteBySubSessionId(subSessionId, userId, "PURGE_SUB_SESSION");
        relRepository.archiveAndDeleteBySubSessionId(subSessionId, userId, "PURGE_SUB_SESSION");
        knowledgeAppService.deleteForSubSession(subSessionId, userId);
        rawChatLogRepository.archiveAndDeleteBySubSessionId(subSessionId, userId, "PURGE_SUB_SESSION");
        subSessionRepository.archiveAndDeleteById(subSessionId, "PURGE_SUB_SESSION");
    }
}
