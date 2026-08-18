package com.butler.perception.api;

import com.butler.application.SyncAppService;
import com.butler.application.TaskQueryAppService;
import com.butler.domain.model.SessionType;
import com.butler.domain.model.Task;
import com.butler.infrastructure.auth.CurrentUser;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class SyncController {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private final SyncAppService syncAppService;

    public SyncController(SyncAppService syncAppService) {
        this.syncAppService = syncAppService;
    }

    @GetMapping("/sync")
    public Map<String, Object> sync(@RequestParam String sessionType,
                                    @RequestParam(required = false) Long subSessionId,
                                    @RequestParam(required = false) Long afterMsgId,
                                    @RequestParam(required = false) Instant sessionsAt,
                                    @RequestParam(required = false) Instant tasksAt,
                                    @RequestParam(required = false) Instant subAt,
                                    @RequestParam(required = false) Instant pendingAt) {
        Long userId = CurrentUser.userId();
        SessionType type = SessionType.valueOf(sessionType.toUpperCase());
        Map<String, Object> result = syncAppService.sync(userId, type, subSessionId, afterMsgId,
                sessionsAt, tasksAt, subAt, pendingAt);
        Object tasksBlock = result.get("tasks");
        if (tasksBlock instanceof Map<?, ?> tb && Boolean.TRUE.equals(tb.get("changed"))
                && tb.get("data") instanceof TaskQueryAppService.GroupedTasks g) {
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> b = (java.util.Map<String, Object>) tb;
            b.put("data", java.util.Map.of("groups", toGroupViews(g)));
        }
        return result;
    }

    @GetMapping("/messages/older")
    public Map<String, Object> older(@RequestParam String sessionType,
                                     @RequestParam(required = false) Long subSessionId,
                                     @RequestParam(required = false) Long beforeId) {
        Long userId = CurrentUser.userId();
        SessionType type = SessionType.valueOf(sessionType.toUpperCase());
        return syncAppService.loadOlder(userId, type, subSessionId, beforeId);
    }

    private List<Map<String, Object>> toGroupViews(TaskQueryAppService.GroupedTasks g) {
        List<Map<String, Object>> groups = new ArrayList<>();
        for (TaskQueryAppService.FocusGroup gr : g.groups()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("key", gr.key());
            m.put("label", gr.label());
            m.put("total", gr.total());
            m.put("overdueCount", gr.overdueCount());
            m.put("custom", gr.custom());
            m.put("tasks", gr.tasks().stream().map(this::toView).toList());
            m.put("upcomingCollapsed", gr.upcomingCollapsed().stream().map(this::toView).toList());
            m.put("history", gr.history().stream().map(this::toView).toList());
            groups.add(m);
        }
        return groups;
    }

    private Map<String, Object> toView(Task t) {
        LocalDate today = LocalDate.now(ZONE);
        LocalDate effDue = t.effectiveDueDate(today);
        String status = t.isCompleted() ? "COMPLETED"
                : (effDue != null && effDue.isBefore(today)) ? "OVERDUE" : "PENDING";
        String remindDate = t.getRemindAt() == null ? null
                : t.getRemindAt().atZone(ZONE).toLocalDate().toString();
        String remindTime = t.getRecurrence() != null && !t.getRecurrence().isBlank() && t.getRemindTime() != null
                ? t.getRemindTime().format(DateTimeFormatter.ofPattern("HH:mm")) : null;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", t.getId());
        m.put("content", t.getContent());
        m.put("detail", t.getDetail());
        m.put("nextHint", t.getNextHint());
        m.put("focusArea", t.getFocusArea());
        m.put("dueDate", effDue == null ? null : effDue.toString());
        m.put("status", status);
        m.put("completed", t.isCompleted());
        m.put("reminded", t.isReminded());
        m.put("remindAt", t.getRemindAt() == null ? null : t.getRemindAt().toString());
        m.put("remindDate", remindDate);
        m.put("recurrence", t.getRecurrence());
        m.put("remindTime", remindTime);
        return m;
    }
}
