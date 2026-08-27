package com.butler.application;

import com.butler.domain.model.Mission;
import com.butler.domain.model.PendingEvent;
import com.butler.domain.model.RawChatLog;
import com.butler.domain.model.SessionType;
import com.butler.domain.model.SubSession;
import com.butler.domain.repository.PendingEventRepository;
import com.butler.domain.repository.MissionRepository;
import com.butler.domain.repository.RawChatLogRepository;
import com.butler.domain.repository.SubSessionRepository;
import com.butler.domain.repository.TaskRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * 按“每个可更新点一个版本时间戳”做差异同步，避免前端轮询全量重绘。
 * 更新点：messages(消息) / sessions(左侧子对话列表) / tasks(待办) / sub(子对话信息) / pending(待确认卡片)。
 */
@Service
public class SyncAppService {

    private static final int PAGE_SIZE = 20;

    private final RawChatLogRepository rawChatLogRepository;
    private final SubSessionRepository subSessionRepository;
    private final TaskRepository taskRepository;
    private final TaskQueryAppService taskQueryAppService;
    private final ConversationAppService conversationAppService;
    private final SubSessionAppService subSessionAppService;
    private final PendingGoalProposalStore pendingGoalProposalStore;
    private final PendingProposalStore pendingProposalStore;
    private final PendingEventRepository pendingEventRepository;
    private final MissionRepository missionRepository;
    private final MetricAppService metricAppService;
    private final ObjectMapper objectMapper;

    public SyncAppService(RawChatLogRepository rawChatLogRepository,
                          SubSessionRepository subSessionRepository,
                          TaskRepository taskRepository,
                          TaskQueryAppService taskQueryAppService,
                          ConversationAppService conversationAppService,
                          SubSessionAppService subSessionAppService,
                          PendingGoalProposalStore pendingGoalProposalStore,
                          PendingProposalStore pendingProposalStore,
                          PendingEventRepository pendingEventRepository,
                          MissionRepository missionRepository,
                          MetricAppService metricAppService,
                          ObjectMapper objectMapper) {
        this.rawChatLogRepository = rawChatLogRepository;
        this.subSessionRepository = subSessionRepository;
        this.taskRepository = taskRepository;
        this.taskQueryAppService = taskQueryAppService;
        this.conversationAppService = conversationAppService;
        this.subSessionAppService = subSessionAppService;
        this.pendingGoalProposalStore = pendingGoalProposalStore;
        this.pendingProposalStore = pendingProposalStore;
        this.pendingEventRepository = pendingEventRepository;
        this.missionRepository = missionRepository;
        this.metricAppService = metricAppService;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> sync(Long userId, SessionType type, Long subSessionId,
                                    Long afterMsgId, Instant sessionsAt, Instant tasksAt,
                                    Instant subAt, Instant pendingAt) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("messages", syncMessages(userId, type, subSessionId, afterMsgId));
        out.put("sessions", syncSessions(userId, sessionsAt));
        if (type == SessionType.SUB && subSessionId != null) {
            out.put("tasks", syncTasks(subSessionId, tasksAt));
            out.put("sub", syncSub(subSessionId, subAt));
        }
        out.put("pending", syncPending(userId, type, subSessionId, pendingAt));
        return out;
    }

    private Map<String, Object> syncMessages(Long userId, SessionType type, Long subSessionId, Long afterId) {
        Map<String, Object> block = new LinkedHashMap<>();
        List<Map<String, Object>> msgs = new ArrayList<>();
        List<RawChatLog> rows;
        boolean hasMore = false;
        if (afterId != null && afterId > 0) {
            rows = rawChatLogRepository.findNewer(userId, type, subSessionId, afterId);
        } else {
            List<RawChatLog> recent = rawChatLogRepository.findRecent(userId, type, subSessionId, PAGE_SIZE + 1);
            hasMore = recent.size() > PAGE_SIZE;
            if (hasMore) recent = recent.subList(0, PAGE_SIZE);
            // findRecent 内部已按 id 正序返回
            rows = recent;
        }
        Long lastId = afterId == null ? 0L : afterId;
        for (RawChatLog r : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.getId());
            m.put("role", r.getRole());
            m.put("content", r.getContent());
            m.put("reasoning", r.getReasoning());
            m.put("createdAt", r.getCreatedAt() == null ? null : r.getCreatedAt().toString());
            msgs.add(m);
            if (r.getId() != null && r.getId() > lastId) lastId = r.getId();
        }
        if (afterId == null || afterId == 0) {
            Long oldestId = rows.isEmpty() ? null : rows.get(0).getId();
            if (oldestId != null) {
                hasMore = !rawChatLogRepository.findOlder(userId, type, subSessionId, oldestId, 1).isEmpty();
            }
        }
        block.put("changed", !msgs.isEmpty() || (afterId == null || afterId == 0));
        block.put("messages", msgs);
        block.put("lastMsgId", lastId);
        block.put("hasMore", hasMore);
        return block;
    }

    /** 加载更早的一页消息（在 beforeId 之前）。 */
    public Map<String, Object> loadOlder(Long userId, SessionType type, Long subSessionId, Long beforeId) {
        Map<String, Object> block = new LinkedHashMap<>();
        List<RawChatLog> older = rawChatLogRepository.findOlder(userId, type, subSessionId,
                beforeId == null ? Long.MAX_VALUE : beforeId, PAGE_SIZE + 1);
        boolean hasMore = older.size() > PAGE_SIZE;
        if (hasMore) older = older.subList(0, PAGE_SIZE);
        List<Map<String, Object>> msgs = new ArrayList<>();
        // findOlder 是倒序，渲染时需要正序
        for (int i = older.size() - 1; i >= 0; i--) {
            RawChatLog r = older.get(i);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.getId());
            m.put("role", r.getRole());
            m.put("content", r.getContent());
            m.put("reasoning", r.getReasoning());
            msgs.add(m);
        }
        block.put("messages", msgs);
        block.put("hasMore", hasMore);
        block.put("oldestMsgId", msgs.isEmpty() ? beforeId : msgs.get(0).get("id"));
        return block;
    }

    private Map<String, Object> syncSessions(Long userId, Instant since) {
        Instant ver = subSessionRepository.findLastUpdatedAt(userId).orElse(Instant.EPOCH);
        Map<String, Object> block = new LinkedHashMap<>();
        boolean changed = since == null || ver.isAfter(since);
        block.put("changed", changed);
        block.put("at", ver.toString());
        if (changed) {
            List<Map<String, Object>> items = new ArrayList<>();
            for (SubSession s : subSessionRepository.findByUserId(userId)) {
                Map<String, Object> it = new LinkedHashMap<>();
                it.put("id", s.getId());
                it.put("missionId", s.getMissionId());
                it.put("scenarioType", s.getScenarioType());
                it.put("sessionDesc", s.getSessionDesc());
                it.put("status", s.getStatus() == null ? null : s.getStatus().name());
                it.put("title", missionRepository.findById(s.getMissionId())
                        .map(Mission::getTitle).orElse(""));
                items.add(it);
            }
            block.put("items", items);
        }
        return block;
    }

    private Map<String, Object> syncTasks(Long subSessionId, Instant since) {
        Instant ver = taskRepository.findLastUpdatedAt(subSessionId).orElse(Instant.EPOCH);
        Map<String, Object> block = new LinkedHashMap<>();
        boolean changed = since == null || ver.isAfter(since);
        block.put("changed", changed);
        block.put("at", ver.toString());
        if (changed) {
            block.put("data", taskQueryAppService.listGrouped(subSessionId));
        }
        return block;
    }

    private Map<String, Object> syncSub(Long subSessionId, Instant since) {
        SubSession s = subSessionRepository.findById(subSessionId).orElse(null);
        Instant ver = s == null || s.getUpdatedAt() == null ? Instant.EPOCH : s.getUpdatedAt();
        Map<String, Object> block = new LinkedHashMap<>();
        boolean changed = since == null || ver.isAfter(since);
        block.put("changed", changed);
        block.put("at", ver.toString());
        if (changed && s != null) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("scenarioType", s.getScenarioType());
            data.put("collected", conversationAppService.getCollected(subSessionId));
            data.put("state", conversationAppService.getState(subSessionId));
            data.put("customFocus", conversationAppService.getCustomFocusLabels(subSessionId));
            data.put("materials", subSessionAppService.getStudyMaterials(subSessionId));
            data.put("metrics", metricAppService.latest(subSessionId));
            block.put("data", data);
        }
        return block;
    }

    private Map<String, Object> syncPending(Long userId, SessionType type, Long subSessionId, Instant since) {
        Instant ver = pendingEventRepository.findLastUpdatedAt(userId).orElse(Instant.EPOCH);
        Map<String, Object> block = new LinkedHashMap<>();
        Map<String, Object> data = null;
        if (type == SessionType.MAIN) {
            PendingGoalProposalStore.StoredProposal p = pendingGoalProposalStore.findLatestByUser(userId);
            if (p != null) {
                data = new LinkedHashMap<>();
                data.put("kind", "GOAL_PROPOSAL");
                data.put("proposalId", p.id());
                data.put("payload", p.proposal());
                ver = p.expiresAt();
            }
        } else if (subSessionId != null) {
            PendingProposalStore.StoredProposal p = pendingProposalStore.findLatestByUser(userId, subSessionId);
            if (p != null) {
                data = new LinkedHashMap<>();
                data.put("kind", "CHANGE_PROPOSAL");
                data.put("proposalId", p.id());
                String previewJson = pendingProposalStore.getPreview(p.id());
                if (previewJson != null && !previewJson.isBlank()) {
                    try {
                        data.put("payload", objectMapper.readTree(previewJson));
                    } catch (Exception e) {
                        data.put("payload", null);
                    }
                } else {
                    data.put("payload", null);
                }
                ver = p.expiresAt();
            }
        }
        boolean changed = since == null || ver.isAfter(since);
        block.put("changed", changed);
        block.put("at", ver.toString());
        if (changed) block.put("data", data);
        return block;
    }

}
