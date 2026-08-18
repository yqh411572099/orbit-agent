package com.butler.perception.api;

import com.butler.action.ReminderService;
import com.butler.application.AdminPurgeAppService;
import com.butler.application.MemoryAdminAppService;
import com.butler.application.KnowledgeAppService;
import com.butler.application.MemoryExtractionAppService;
import com.butler.application.OrphanTaskCleanupAppService;
import com.butler.domain.repository.UserRepository;
import com.butler.application.FocusAreaAppService;
import com.butler.domain.model.Task;
import com.butler.domain.repository.SubSessionRepository;
import com.butler.infrastructure.persistence.archive.EntityArchiveJpaRepository;
import com.butler.domain.repository.TaskRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import com.butler.infrastructure.scheduler.MemoryExtractionJob;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
public class MemoryExtractionController {

    private final MemoryExtractionAppService extractionService;
    private final MemoryExtractionJob job;
    private final ReminderService reminderService;
    private final MemoryAdminAppService memoryAdminService;
    private final AdminPurgeAppService adminPurgeAppService;
    private final KnowledgeAppService knowledgeAppService;
    private final TaskRepository taskRepository;
    private final SubSessionRepository subSessionRepository;
    private final EntityArchiveJpaRepository archiveRepository;
    private final OrphanTaskCleanupAppService orphanTaskCleanupAppService;
    private final UserRepository userRepository;
    private final FocusAreaAppService focusAreaAppService;

    public MemoryExtractionController(MemoryExtractionAppService extractionService, MemoryExtractionJob job,
                                      ReminderService reminderService,
                                      MemoryAdminAppService memoryAdminService,
                                      AdminPurgeAppService adminPurgeAppService,
                              KnowledgeAppService knowledgeAppService,
                              TaskRepository taskRepository,
                              SubSessionRepository subSessionRepository,
                              EntityArchiveJpaRepository archiveRepository,
                              OrphanTaskCleanupAppService orphanTaskCleanupAppService,
                              UserRepository userRepository,
                              FocusAreaAppService focusAreaAppService) {
        this.extractionService = extractionService;
        this.job = job;
        this.reminderService = reminderService;
        this.memoryAdminService = memoryAdminService;
        this.adminPurgeAppService = adminPurgeAppService;
        this.knowledgeAppService = knowledgeAppService;
        this.taskRepository = taskRepository;
        this.subSessionRepository = subSessionRepository;
        this.archiveRepository = archiveRepository;
        this.orphanTaskCleanupAppService = orphanTaskCleanupAppService;
        this.userRepository = userRepository;
        this.focusAreaAppService = focusAreaAppService;
    }

    /** 手动触发指定用户的增量记忆提炼（测试/运维用）。 */
    @PostMapping("/extract/{userId}")
    public int extract(@PathVariable Long userId) {
        Instant to = Instant.now();
        Instant from = to.minus(2, ChronoUnit.HOURS);
        return extractionService.runForUser(userId, from, to);
    }

    /** 触发全局定时任务（遍历所有用户）。 */
    @PostMapping("/extract/run-job")
    public String runJob() {
        job.run();
        return "ok";
    }

    /** 手动触发一次到期任务提醒推送（测试用）。 */
    @PostMapping("/reminders/run")
    public int runReminders() {
        return reminderService.triggerNow();
    }

    /** 清空某用户的全部记忆及关联（运维/调试，危险操作）。 */
    @PostMapping("/memories/clear/{userId}")
    public int clearMemories(@PathVariable Long userId) {
        return memoryAdminService.clearAllMemories(userId);
    }

    /** 清理测试用户数据（仅 userId 命中测试策略的用户，硬删除；真实用户跳过）。 */
    @PostMapping("/purge-all")
    public int purgeAll() {
        return adminPurgeAppService.purgeTestUsers();
    }

    /** 清理指定用户：真实用户先归档再删除，测试用户直接硬删。 */
    @PostMapping("/purge-user/{userId}")
    public int purgeUser(@PathVariable Long userId) {
        return adminPurgeAppService.purgeUser(userId);
    }

    /** 为所有已确认知识重建向量索引（运维/补数据用）。 */
    @PostMapping("/knowledge/reindex")
    public int reindexKnowledge() {
        return knowledgeAppService.reindexAllConfirmed();
    }

    /** 按 ID 批量物理删除记忆及其关联（运维用）。 */
    @PostMapping("/memories/delete")
    public Map<String, Object> deleteMemories(@RequestBody List<Long> memoryIds) {
        int removed = memoryAdminService.deleteMemoriesByIds(memoryIds);
        return Map.of("removed", removed);
    }

    /** 删除某子对话下指定关注项的全部任务（含动态/周期任务）；真实用户先归档。 */
    @PostMapping("/sub-sessions/{subSessionId}/tasks/delete-by-focus/{focusArea}")
    public Map<String, Object> deleteTasksByFocus(@PathVariable Long subSessionId,
                                                  @PathVariable String focusArea) {
        Long userId = subSessionRepository.findById(subSessionId)
                .map(com.butler.domain.model.SubSession::getUserId).orElse(null);
        int removed = taskRepository.archiveAndDeleteBySubSessionIdAndFocusArea(
                subSessionId, focusArea, userId, "DELETE_FOCUS_AREA");
        return Map.of("removed", removed);
    }

    /** 更新关注项（移除/新增），触发时间轴 resync。 */
    @PostMapping("/sub-sessions/{subSessionId}/focus")
    public String updateFocus(@PathVariable Long subSessionId, @RequestBody List<String> focusAreas) {
        return focusAreaAppService.updateFocusAreas(subSessionId, focusAreas);
    }

    /** 写入/补全自定义关注项的中文 label（数据修复用）。 */
    @PostMapping("/sub-sessions/{subSessionId}/custom-focus")
    public Map<String, Object> putCustomLabels(@PathVariable Long subSessionId,
                                               @RequestBody java.util.Map<String, String> labels) {
        focusAreaAppService.putCustomLabels(subSessionId, labels);
        return Map.of("ok", true);
    }


    /**
     * 数据修复：把“今天应到点、却被旧逻辑提前滚动到未来”的周期任务拉回今天（标记已提醒）。
     * 仅用于修复提醒后自动滚动导致的脏数据。
     */
    @PostMapping("/sub-sessions/{subSessionId}/repair-recurring-to-today")
    public Map<String, Object> repairRecurring(@PathVariable Long subSessionId) {
        ZoneId zone = ZoneId.of("Asia/Shanghai");
        LocalDate today = LocalDate.now(zone);
        java.time.Instant now = java.time.Instant.now();
        int repaired = 0;
        for (Task t : taskRepository.findBySubSessionId(subSessionId)) {
            if (!t.isRecurring() || t.isCompleted() || t.getDueDate() == null) continue;
            if (!t.getDueDate().isAfter(today)) continue;
            LocalTime time = t.getRemindTime() != null ? t.getRemindTime() : LocalTime.of(9, 0);
            java.time.Instant todaysRemindAt = today.atTime(time).atZone(zone).toInstant();
            if (!now.isAfter(todaysRemindAt)) continue; // 今天的时刻还没到，不需要回拉
            Task repairedTask = Task.createScheduled(subSessionId, t.getContent(), t.getDetail(),
                    t.getNextHint(), t.getModuleKey(), t.getFocusArea(),
                    today, today, t.getMilestoneKey(), t.getRecurrence(), time);
            repairedTask.markReminded();
            taskRepository.delete(t);
            taskRepository.save(repairedTask);
            repaired++;
        }
        return Map.of("repaired", repaired);
    }


    /** 查看归档快照数量（验证/审计用）。 */
    @GetMapping("/archives/count")
    public Map<String, Object> archiveCount(@RequestParam(required = false) Long userId,
                                            @RequestParam(required = false) String entityType) {
        long total = archiveRepository.count();
        return Map.of("total", total, "note", "按 userId/entityType 过滤可后续扩展");
    }

    /** 按用户名预览/清理“关注项已取消但任务残留”的孤儿任务。 */
    @GetMapping("/orphan-tasks")
    public Map<String, Object> previewOrphans(@RequestParam String username) {
        Long uid = userRepository.findByUsername(username)
                .map(com.butler.domain.model.User::getId)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在: " + username));
        return Map.of("username", username, "userId", uid,
                "orphans", orphanTaskCleanupAppService.preview(uid));
    }

    @PostMapping("/orphan-tasks/cleanup")
    public Map<String, Object> cleanupOrphans(@RequestParam String username) {
        Long uid = userRepository.findByUsername(username)
                .map(com.butler.domain.model.User::getId)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在: " + username));
        return Map.of("username", username, "userId", uid,
                "cleaned", orphanTaskCleanupAppService.cleanup(uid));
    }
}
