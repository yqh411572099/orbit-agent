package com.butler.application;

import com.butler.domain.model.SubSession;
import com.butler.domain.model.Task;
import com.butler.domain.repository.TaskRepository;
import com.butler.domain.scenario.ScenarioDomain;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TimelineAppService {
    private static final Logger log = LoggerFactory.getLogger(TimelineAppService.class);
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private final TaskRepository taskRepository;

    public TimelineAppService(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    @Transactional
    public String resync(SubSession sub, ScenarioDomain domain,
                         Map<String, String> collected, List<String> focusAreas, String note) {
        TimelineDiff diff = previewDiff(sub, domain, collected, focusAreas);

        int created = 0, updated = 0, removed = 0;
        for (ScenarioDomain.PlannedTask planned : diff.added()) {
            taskRepository.save(Task.createScheduled(sub.getId(), planned.title(), planned.detail(),
                    planned.nextHint(), planned.moduleKey(), planned.focusArea(),
                    planned.remindDate(), planned.dueDate(), planned.milestoneKey()));
            created++;
        }
        for (Map.Entry<Task, ScenarioDomain.PlannedTask> e : diff.updated().entrySet()) {
            Task current = e.getKey();
            ScenarioDomain.PlannedTask planned = e.getValue();
            taskRepository.delete(current);
            taskRepository.save(Task.createScheduled(sub.getId(), planned.title(), planned.detail(),
                    planned.nextHint(), planned.moduleKey(), planned.focusArea(),
                    planned.remindDate(), planned.dueDate(), planned.milestoneKey()));
            updated++;
        }
        for (Task task : diff.removed()) {
            taskRepository.archiveAndDelete(task, sub.getUserId(), "FOCUS_AREA_DISABLED");
            removed++;
        }

        // 清理“孤儿动态任务”：moduleKey=null 的非周期任务中，其 focusArea 已不在当前生效关注项集合里
        // （常见于场景域内置关注项重构后，旧版本 LLM 自动生成的一次性任务残留在已废弃的 focusArea 分组下）。
        // 周期任务（用户显式创建的每日/每周习惯提醒）一律保留，不因 focusArea 变化而删除。
        int orphanRemoved = cleanupOrphanDynamicTasks(sub, domain, focusAreas);
        removed += orphanRemoved;

        log.info("场景时间轴同步 sub={} scenario={} created={} updated={} removed={}",
                sub.getId(), domain.type(), created, updated, removed);
        return buildNote(note, created, updated, removed);
    }

    private int cleanupOrphanDynamicTasks(SubSession sub, ScenarioDomain domain, List<String> focusAreas) {
        Set<String> validFocus = new java.util.HashSet<>(
                ScenarioDomain.resolveEffectiveFocusAreas(domain.focusAreas(), focusAreas));
        int count = 0;
        for (Task t : taskRepository.findBySubSessionId(sub.getId())) {
            if (t.getModuleKey() != null) continue;
            if (t.isCompleted()) continue;
            if (t.getRecurrence() != null && !t.getRecurrence().isBlank()) continue;
            String fa = t.getFocusArea();
            if (fa == null || fa.isBlank() || validFocus.contains(fa)) continue;
            taskRepository.archiveAndDelete(t, sub.getUserId(), "STALE_FOCUS_AREA_TASK");
            count++;
        }
        return count;
    }

    /**
     * 不写库，只计算“按 collected+focus 重新规划后”与现有时间轴任务的差异，供变更预览使用。
     */
    public TimelineDiff previewDiff(SubSession sub, ScenarioDomain domain,
                                    Map<String, String> collected, List<String> focusAreas) {
        LocalDate today = LocalDate.now(ZONE);
        List<ScenarioDomain.PlannedTask> desired = domain.plannedTasks(
                collected == null ? Map.of() : collected,
                focusAreas == null ? List.of() : focusAreas,
                today);

        List<Task> existing = taskRepository.findBySubSessionId(sub.getId());
        Map<String, Task> timelineByTitle = existing.stream()
                .filter(t -> t.getModuleKey() != null)
                .collect(Collectors.toMap(Task::getContent, t -> t, (a, b) -> a, LinkedHashMap::new));

        List<ScenarioDomain.PlannedTask> added = new ArrayList<>();
        Map<Task, ScenarioDomain.PlannedTask> updated = new LinkedHashMap<>();
        for (ScenarioDomain.PlannedTask planned : desired) {
            Task current = timelineByTitle.get(planned.title());
            if (current == null) {
                added.add(planned);
            } else if (!current.isCompleted() && needsUpdate(current, planned)) {
                updated.put(current, planned);
            }
        }

        Set<String> desiredTitles = desired.stream()
                .map(ScenarioDomain.PlannedTask::title)
                .collect(Collectors.toSet());
        List<Task> removed = new ArrayList<>();
        for (Task task : timelineByTitle.values()) {
            if (!desiredTitles.contains(task.getContent()) && !task.isCompleted()) {
                removed.add(task);
            }
        }
        return new TimelineDiff(added, updated, removed);
    }

    private boolean needsUpdate(Task current, ScenarioDomain.PlannedTask planned) {
        // 只比较用户可见字段；milestoneKey 是内部标识，其差异（如旧数据为 null）不应产生“改期”噪音。
        return !nullSafeEquals(current.getDueDate(), planned.dueDate())
                || !nullSafeEquals(current.getRemindAt(), remindAt(planned))
                || !nullSafeEquals(current.getDetail(), planned.detail())
                || !nullSafeEquals(current.getNextHint(), planned.nextHint())
                || !nullSafeEquals(current.getFocusArea(), planned.focusArea());
    }

    /** 时间轴 dry-run 差异：新增、更新（旧任务→新规划）、移除。 */
    public record TimelineDiff(
            List<ScenarioDomain.PlannedTask> added,
            Map<Task, ScenarioDomain.PlannedTask> updated,
            List<Task> removed) {
        public boolean isEmpty() {
            return added.isEmpty() && updated.isEmpty() && removed.isEmpty();
        }
    }

    private String buildNote(String note, int created, int updated, int removed) {
        StringBuilder sb = new StringBuilder();
        if (note != null && !note.isBlank()) sb.append("🔄 ").append(note);
        if (updated > 0) sb.append(sb.isEmpty() ? "" : "；").append("已按新信息重排 ").append(updated).append(" 项待办");
        if (created > 0) sb.append(sb.isEmpty() ? "" : "；").append("新增 ").append(created).append(" 项待办");
        if (removed > 0) sb.append(sb.isEmpty() ? "" : "；").append("移除 ").append(removed).append(" 项不再关注的待办");
        if (sb.isEmpty()) return "";
        return sb.append("。").toString();
    }

    private java.time.Instant remindAt(ScenarioDomain.PlannedTask planned) {
        LocalDate remindOn = planned.remindDate() != null ? planned.remindDate() : planned.dueDate();
        return remindOn == null ? null : remindOn.atStartOfDay(ZONE).plusHours(9).toInstant();
    }

    private boolean nullSafeEquals(Object a, Object b) {
        return a == null ? b == null : a.equals(b);
    }
}
