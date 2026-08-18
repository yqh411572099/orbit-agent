package com.butler.application;

import com.butler.domain.attribute.Attribute;
import com.butler.domain.model.PendingEvent;
import com.butler.domain.repository.PendingEventRepository;
import com.butler.infrastructure.llm.LlmPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * 子对话中“待确认的变更提案”。持久化到 pending_event（eventType=CHANGE_PROPOSAL），
 * 重启/刷新不丢失；TTL 到期失效。确认/放弃改状态。
 */
@Component
public class PendingProposalStore {

    public static final String EVENT_TYPE = "CHANGE_PROPOSAL";
    private static final long TTL_SECONDS = 600;

    private final PendingEventRepository repository;
    private final ObjectMapper objectMapper;

    public PendingProposalStore(PendingEventRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public StoredProposal put(Long userId, Long subSessionId, String scenarioType, String message,
                              Map<String, String> newCollected, List<String> effectiveFocus,
                              Map<String, String> customFocusLabels,
                              List<Attribute> memoryUpserts, List<String> completedKeywords,
                              List<LlmPort.TaskItem> plannedDynamicTasks) {
        String id = UUID.randomUUID().toString();
        Instant now = Instant.now();
        Instant expires = now.plusSeconds(TTL_SECONDS);
        StoredProposal proposal = new StoredProposal(id, userId, subSessionId, scenarioType, message,
                newCollected == null ? Map.of() : Map.copyOf(newCollected),
                effectiveFocus == null ? List.of() : List.copyOf(effectiveFocus),
                customFocusLabels == null ? Map.of() : Map.copyOf(customFocusLabels),
                memoryUpserts == null ? List.of() : List.copyOf(memoryUpserts),
                completedKeywords == null ? List.of() : List.copyOf(completedKeywords),
                plannedDynamicTasks == null ? List.of() : List.copyOf(plannedDynamicTasks),
                expires);
        try {
            repository.save(new PendingEvent(id, userId, PendingEvent.Scope.SUB, subSessionId,
                    EVENT_TYPE, objectMapper.writeValueAsString(proposal),
                    PendingEvent.Status.PENDING, expires, now, now));
        } catch (Exception e) {
            throw new IllegalStateException("保存待确认变更提案失败", e);
        }
        return proposal;
    }

    public StoredProposal get(String proposalId) {
        if (proposalId == null) return null;
        return repository.findById(proposalId)
                .filter(e -> EVENT_TYPE.equals(e.getEventType()))
                .filter(e -> e.getStatus() == PendingEvent.Status.PENDING)
                .filter(e -> !Instant.now().isAfter(e.getExpiresAt()))
                .map(this::toStored).orElse(null);
    }

    public StoredProposal findLatestByUser(Long userId, Long subSessionId) {
        return repository.findLatestPendingByUser(userId, PendingEvent.Scope.SUB, subSessionId)
                .filter(e -> EVENT_TYPE.equals(e.getEventType()))
                .map(this::toStored).orElse(null);
    }

    public void remove(String proposalId) {
        if (proposalId == null) return;
        repository.findById(proposalId).ifPresent(e -> {
            e.setStatus(PendingEvent.Status.APPLIED);
            repository.save(e);
        });
    }

    /** 把给前端展示用的变更预览（ChangePreview JSON）随提案一起持久化，刷新后可恢复弹窗。 */
    public void attachPreview(String proposalId, String previewJson) {
        if (proposalId == null) return;
        repository.findById(proposalId).ifPresent(e -> {
            e.setPreview(previewJson);
            repository.save(e);
        });
    }

    public String getPreview(String proposalId) {
        return repository.findById(proposalId).map(PendingEvent::getPreview).orElse(null);
    }

    public void discard(String proposalId) {
        if (proposalId == null) return;
        repository.findById(proposalId).ifPresent(e -> {
            e.setStatus(PendingEvent.Status.DISCARDED);
            repository.save(e);
        });
    }

    private StoredProposal toStored(PendingEvent e) {
        try {
            StoredProposal p = objectMapper.readValue(e.getPayload(), StoredProposal.class);
            return new StoredProposal(e.getId(), p.userId(), p.subSessionId(), p.scenarioType(), p.message(),
                    p.newCollected(), p.effectiveFocus(), p.customFocusLabels(), p.memoryUpserts(),
                    p.completedKeywords(), p.plannedDynamicTasks(), e.getExpiresAt());
        } catch (Exception ex) {
            return null;
        }
    }

    public record StoredProposal(
            String id,
            Long userId,
            Long subSessionId,
            String scenarioType,
            String message,
            Map<String, String> newCollected,
            List<String> effectiveFocus,
            Map<String, String> customFocusLabels,
            List<Attribute> memoryUpserts,
            List<String> completedKeywords,
            List<LlmPort.TaskItem> plannedDynamicTasks,
            Instant expiresAt
    ) {}
}
