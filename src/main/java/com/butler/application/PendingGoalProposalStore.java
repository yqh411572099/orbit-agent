package com.butler.application;

import com.butler.domain.model.PendingEvent;
import com.butler.domain.repository.PendingEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * 主对话中“待用户确认后再创建目标”的提案。
 * 持久化到 pending_event（eventType=GOAL_PROPOSAL），重启/刷新不丢失；TTL 到期失效。
 */
@Component
public class PendingGoalProposalStore {

    public static final String EVENT_TYPE = "GOAL_PROPOSAL";
    private static final long TTL_SECONDS = 900;

    private final PendingEventRepository repository;
    private final ObjectMapper objectMapper;

    public PendingGoalProposalStore(PendingEventRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public StoredProposal put(Long userId, GoalProposal proposal) {
        String id = UUID.randomUUID().toString();
        Instant now = Instant.now();
        Instant expires = now.plusSeconds(TTL_SECONDS);
        try {
            String payload = objectMapper.writeValueAsString(proposal);
            PendingEvent saved = repository.save(new PendingEvent(id, userId, PendingEvent.Scope.MAIN, null,
                    EVENT_TYPE, payload, PendingEvent.Status.PENDING, expires, now, now));
            return new StoredProposal(saved.getId(), userId, proposal, saved.getExpiresAt());
        } catch (Exception e) {
            throw new IllegalStateException("保存待确认提案失败", e);
        }
    }

    public StoredProposal get(String id) {
        if (id == null) return null;
        return repository.findById(id)
                .filter(e -> EVENT_TYPE.equals(e.getEventType()))
                .filter(e -> e.getStatus() == PendingEvent.Status.PENDING)
                .filter(e -> !Instant.now().isAfter(e.getExpiresAt()))
                .map(this::toStored).orElse(null);
    }

    public void remove(String id) {
        if (id == null) return;
        repository.findById(id).ifPresent(e -> {
            e.setStatus(PendingEvent.Status.APPLIED);
            repository.save(e);
        });
    }

    public StoredProposal findLatestByUser(Long userId) {
        return repository.findLatestPendingByUser(userId, PendingEvent.Scope.MAIN, null)
                .filter(e -> EVENT_TYPE.equals(e.getEventType()))
                .map(this::toStored).orElse(null);
    }

    private StoredProposal toStored(PendingEvent e) {
        try {
            GoalProposal p = objectMapper.readValue(e.getPayload(), GoalProposal.class);
            return new StoredProposal(e.getId(), e.getUserId(), p, e.getExpiresAt());
        } catch (Exception ex) {
            return null;
        }
    }

    public record StoredProposal(String id, Long userId, GoalProposal proposal, Instant expiresAt) {}

    public record GoalProposal(
            String scenarioType,
            String title,
            String goalText,
            Map<String, String> collected,
            List<String> focusAreas,
            Map<String, String> focusLabels,
            List<Section> sections,
            List<StudyMaterial> materials
    ) {
        public GoalProposal {
            collected = collected == null ? Map.of() : Map.copyOf(collected);
            focusAreas = focusAreas == null ? List.of() : List.copyOf(focusAreas);
            focusLabels = focusLabels == null ? Map.of() : Map.copyOf(focusLabels);
            sections = sections == null ? List.of() : List.copyOf(sections);
            materials = materials == null ? List.of() : List.copyOf(materials);
        }
    }

    public record StudyMaterial(String title, String url) {}

    public record Section(String icon, String title, List<Row> rows) {
        public Section { rows = rows == null ? List.of() : List.copyOf(rows); }
    }

    public record Row(String label, String value, boolean uncertain) {}
}
