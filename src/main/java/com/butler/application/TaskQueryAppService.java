package com.butler.application;

import com.butler.domain.model.RawChatLog;
import com.butler.domain.model.SessionType;
import com.butler.domain.model.SubSession;
import com.butler.domain.model.Task;
import com.butler.domain.repository.RawChatLogRepository;
import com.butler.domain.repository.SubSessionRepository;
import com.butler.domain.repository.TaskRepository;
import com.butler.domain.scenario.ScenarioDomain;
import com.butler.domain.scenario.ScenarioRegistry;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class TaskQueryAppService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private final TaskRepository taskRepository;
    private final SubSessionRepository subSessionRepository;
    private final ScenarioRegistry scenarioRegistry;
    private final RawChatLogRepository rawChatLogRepository;

    public TaskQueryAppService(TaskRepository taskRepository,
                               SubSessionRepository subSessionRepository,
                               ScenarioRegistry scenarioRegistry,
                               RawChatLogRepository rawChatLogRepository) {
        this.taskRepository = taskRepository;
        this.subSessionRepository = subSessionRepository;
        this.scenarioRegistry = scenarioRegistry;
        this.rawChatLogRepository = rawChatLogRepository;
    }

    public List<Task> listTasks(Long subSessionId) {
        return taskRepository.findBySubSessionId(subSessionId);
    }

    public Task complete(Long taskId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("任务不存在: " + taskId));
        if (task.isCompleted()) {
            return task;
        }
        task.markCompleted();
        Task saved = taskRepository.save(task);
        // 周期任务：本次完成后生成下一周期任务（未完成状态），避免提醒一推送就跳到下一天。
        if (task.isRecurring()) {
            LocalDate today = LocalDate.now(ZONE);
            Task next = task.rollToNextOccurrence(today);
            if (next != null) taskRepository.save(next);
        }
        pushNextHint(saved);
        return saved;
    }

    /** 删除单个待办（真实用户先归档）；仅任务所属用户可操作。 */
    public void deleteTask(Long taskId, Long userId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("任务不存在: " + taskId));
        Long owner = subSessionRepository.findById(task.getSubSessionId())
                .map(SubSession::getUserId).orElse(null);
        if (owner == null || !owner.equals(userId)) {
            throw new IllegalArgumentException("无权删除该任务");
        }
        taskRepository.archiveAndDelete(task, userId, "USER_DELETE_TASK");
    }

    /**
     * 任务完成后若定义了“下一步提示”，写一条助手消息进子对话，推进时间轴到下一节点。
     */
    private void pushNextHint(Task task) {
        if (task.getNextHint() == null || task.getNextHint().isBlank()) {
            return;
        }
        Long userId = subSessionRepository.findById(task.getSubSessionId())
                .map(com.butler.domain.model.SubSession::getUserId).orElse(null);
        String message = "✅ 已完成：" + task.getContent()
                + "\n\n➡️ 下一步：" + task.getNextHint();
        rawChatLogRepository.save(new RawChatLog(
                null, userId, SessionType.SUB, task.getSubSessionId(),
                "assistant", message, null, Instant.now()));
    }

    /**
     * 按关注项聚合并裁剪：每个关注项直接展示"最近一天"的全部未完成任务；
     * 其余未来任务归入 upcomingCollapsed，已过期任务归入 history，各自独立折叠。
     */
    public GroupedTasks listGrouped(Long subSessionId) {
        List<Task> all = taskRepository.findBySubSessionId(subSessionId).stream()
                .filter(t -> !t.isCompleted())
                .toList();
        LocalDate today = LocalDate.now(ZONE);

        Map<String, String> focusLabels = focusLabels(subSessionId);
        Map<String, List<Task>> grouped = new LinkedHashMap<>();
        java.util.Set<String> builtin = builtinFocusKeys(subSessionId);
        for (Task t : all) {
            String key = t.getFocusArea() == null || t.getFocusArea().isBlank() ? "_general" : t.getFocusArea();
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(t);
        }

        List<FocusGroup> groups = new ArrayList<>();
        for (Map.Entry<String, List<Task>> e : grouped.entrySet()) {
            java.util.Map<Long, LocalDate> eff = new java.util.HashMap<>();
            e.getValue().forEach(t -> eff.put(t.getId(), t.effectiveDueDate(today)));
            List<Task> sorted = e.getValue().stream()
                    // 按时间升序：执行日(dueDate)优先，再按提醒开始日(remindAt)；空值排最后
                    .sorted(Comparator.comparing((Task t) -> eff.get(t.getId()), Comparator.nullsLast(Comparator.naturalOrder()))
                            .thenComparing(t -> remindDate(t), Comparator.nullsLast(Comparator.naturalOrder())))
                    .toList();
            List<Task> upcoming = sorted.stream()
                    .filter(t -> {
                        LocalDate d = eff.get(t.getId());
                        return d == null || !d.isBefore(today);
                    })
                    .toList();
            List<Task> overdue = sorted.stream()
                    .filter(t -> {
                        LocalDate d = eff.get(t.getId());
                        return d != null && d.isBefore(today);
                    })
                    .toList();
            LocalDate anchor = upcoming.stream()
                    .map(t -> eff.get(t.getId()))
                    .filter(java.util.Objects::nonNull)
                    .min(LocalDate::compareTo)
                    .orElse(overdue.stream().map(t -> eff.get(t.getId()))
                            .filter(java.util.Objects::nonNull)
                            .min(LocalDate::compareTo).orElse(null));

            List<Task> recent = sorted.stream()
                    .filter(t -> anchor != null && anchor.equals(eff.get(t.getId())))
                    .toList();
            List<Task> upcomingCollapsed;
            List<Task> history;
            if (upcoming.isEmpty()) {
                // 全部已过期：最近一天作为直接展示，其余过期进 history，没有未来折叠。
                upcomingCollapsed = List.of();
                history = sorted.stream()
                        .filter(t -> !recent.contains(t))
                        .toList();
            } else {
                upcomingCollapsed = upcoming.stream()
                        .filter(t -> !recent.contains(t))
                        .toList();
                history = overdue;
            }
            String label = e.getKey().equals("_general") ? "通用" : focusLabels.getOrDefault(e.getKey(), e.getKey());
            int overdueCount = overdue.size();
            LocalDate remindAnchor = recent.stream()
                    .map(this::remindDate)
                    .filter(java.util.Objects::nonNull)
                    .min(LocalDate::compareTo)
                    .orElse(null);
            groups.add(new FocusGroup(e.getKey(), label, recent, upcomingCollapsed, history,
                    e.getValue().size(), overdueCount, anchor, remindAnchor,
                    !builtin.contains(e.getKey())));
        }
        // 自定义关注项排在内置关注项前面；各自内部按“该组第一个展示的待办时间”升序。
        List<FocusGroup> ordered = groups.stream()
                .sorted(Comparator.comparing((FocusGroup g) -> builtin.contains(g.key()) ? 1 : 0)
                        .thenComparing(FocusGroup::anchor,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(FocusGroup::remindAnchor,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        return new GroupedTasks(ordered);
    }

    private LocalDate remindDate(Task t) {
        if (t.getRemindAt() == null) return null;
        return t.getRemindAt().atZone(ZONE).toLocalDate();
    }

    private Map<String, String> focusLabels(Long subSessionId) {
        Optional<SubSession> opt = subSessionRepository.findById(subSessionId);
        if (opt.isEmpty()) return Map.of();
        SubSession sub = opt.get();
        if (!scenarioRegistry.supports(sub.getScenarioType())) return Map.of();
        ScenarioDomain domain = scenarioRegistry.get(sub.getScenarioType());
        Map<String, String> labels = new LinkedHashMap<>();
        for (ScenarioDomain.FocusArea f : domain.focusAreas()) {
            labels.put(f.key(), f.label());
        }
        labels.putAll(CustomFocusLabels.read(sub));
        return labels;
    }

    private java.util.Set<String> builtinFocusKeys(Long subSessionId) {
        return subSessionRepository.findById(subSessionId)
                .map(SubSession::getScenarioType)
                .filter(scenarioRegistry::supports)
                .map(t -> scenarioRegistry.get(t).focusAreas().stream()
                        .map(ScenarioDomain.FocusArea::key)
                        .collect(java.util.stream.Collectors.toSet()))
                .orElse(java.util.Set.of());
    }

    public record GroupedTasks(List<FocusGroup> groups) {}
    public record FocusGroup(String key, String label, List<Task> tasks,
                             List<Task> upcomingCollapsed, List<Task> history,
                             int total, int overdueCount, LocalDate anchor, LocalDate remindAnchor,
                             boolean custom) {
        public FocusGroup(String key, String label, List<Task> tasks,
                          List<Task> upcomingCollapsed, List<Task> history,
                          int total, int overdueCount) {
            this(key, label, tasks, upcomingCollapsed, history, total, overdueCount, null, null, false);
        }
    }
}
