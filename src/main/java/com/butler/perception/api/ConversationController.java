package com.butler.perception.api;

import com.butler.application.ConversationAppService;
import com.butler.application.GoalAppService;
import com.butler.application.TaskQueryAppService;
import com.butler.domain.attribute.AttributeRenderer;
import com.butler.domain.model.*;
import com.butler.memory.MemoryQueryService;
import com.butler.infrastructure.auth.CurrentUser;
import com.butler.perception.api.dto.*;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class ConversationController {

    private final ConversationAppService conversationAppService;
    private final GoalAppService goalAppService;
    private final TaskQueryAppService taskQueryAppService;
    private final MemoryQueryService memoryQueryService;

    public ConversationController(ConversationAppService conversationAppService,
                                  GoalAppService goalAppService,
                                  TaskQueryAppService taskQueryAppService,
                                  MemoryQueryService memoryQueryService) {
        this.conversationAppService = conversationAppService;
        this.goalAppService = goalAppService;
        this.taskQueryAppService = taskQueryAppService;
        this.memoryQueryService = memoryQueryService;
    }

    /** 主对话创建新目标。 */
    @PostMapping("/goals")
    public GoalResponse createGoal(@Valid @RequestBody CreateGoalRequest req) {
        Long userId = CurrentUser.userId();
        SubSession sub = goalAppService.createGoal(userId, req.scenarioType(), req.title(), req.goal(),
                req.collected() == null ? java.util.Map.of() : req.collected(),
                req.focusAreas() == null ? java.util.List.of() : req.focusAreas());
        return new GoalResponse(sub.getId(), sub.getMissionId(), sub.getScenarioType(), sub.getSessionDesc());
    }

    /** 主对话/子对话发送消息（原始日志统一归档；子对话触发任务调整）。 */
    @PostMapping("/messages")
    public Long sendMessage(@Valid @RequestBody MessageRequest req) {
        Long userId = CurrentUser.userId();
        SessionType type = SessionType.valueOf(req.sessionType().toUpperCase());
        RawChatLog log = conversationAppService.ingest(userId, type, req.subSessionId(), req.content());
        return log.getId();
    }

    @GetMapping("/sub-sessions/{subSessionId}/tasks")
    public GroupedTasksView listTasks(@PathVariable Long subSessionId) {
        TaskQueryAppService.GroupedTasks g = taskQueryAppService.listGrouped(subSessionId);
        List<GroupView> groups = g.groups().stream()
                .map(gr -> new GroupView(gr.key(), gr.label(), gr.total(), gr.overdueCount(),
                        gr.custom(),
                        gr.tasks().stream().map(this::toView).toList(),
                        gr.upcomingCollapsed().stream().map(this::toView).toList(),
                        gr.history().stream().map(this::toView).toList()))
                .toList();
        return new GroupedTasksView(groups);
    }

    @PostMapping("/tasks/{taskId}/complete")
    public TaskView completeTask(@PathVariable Long taskId) {
        return toView(taskQueryAppService.complete(taskId));
    }

    /** 删除单个待办（真实用户先归档）。 */
    @DeleteMapping("/tasks/{taskId}")
    public Map<String, Object> deleteTask(@PathVariable Long taskId) {
        taskQueryAppService.deleteTask(taskId, CurrentUser.userId());
        return Map.of("ok", true);
    }

    private TaskView toView(com.butler.domain.model.Task t) {
        LocalDate today = LocalDate.now(java.time.ZoneId.of("Asia/Shanghai"));
        LocalDate effDue = t.effectiveDueDate(today);
        String status = t.isCompleted() ? "COMPLETED"
                : (effDue != null && effDue.isBefore(today)) ? "OVERDUE" : "PENDING";
        String remindDate = t.getRemindAt() == null ? null
                : t.getRemindAt().atZone(java.time.ZoneId.of("Asia/Shanghai")).toLocalDate().toString();
        String remindTime = t.getRecurrence() != null && !t.getRecurrence().isBlank() && t.getRemindTime() != null
                ? t.getRemindTime().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
                : null;
        return new TaskView(t.getId(), t.getContent(), t.getDetail(), t.getNextHint(), t.getFocusArea(),
                effDue == null ? null : effDue.toString(),
                status, t.isCompleted(), t.isReminded(),
                t.getRemindAt() == null ? null : t.getRemindAt().toString(), remindDate, t.getRecurrence(), remindTime);
    }

    public record GroupedTasksView(List<GroupView> groups) {}
    public record GroupView(String key, String label, int total, int overdueCount, boolean custom,
                            List<TaskView> tasks, List<TaskView> upcomingCollapsed, List<TaskView> history) {}
    public record TaskView(Long id, String content, String detail, String nextHint, String focusArea, String dueDate,
                           String status, boolean completed, boolean reminded, String remindAt, String remindDate,
                           String recurrence, String remindTime) {}

    /** 查询记忆：主对话全量；子对话仅返回绑定记忆。每条带分类。 */
    @GetMapping("/memories")
    public List<MemoryView> listMemories(@RequestParam String sessionType,
                                         @RequestParam(required = false) Long subSessionId) {
        Long userId = CurrentUser.userId();
        SessionType type = SessionType.valueOf(sessionType.toUpperCase());
        return memoryQueryService.query(userId, type, subSessionId).stream()
                .map(m -> new MemoryView(m.getId(), m.getCategory().name(), m.getCategory().getLabel(), m.getContent(),
                        m.getSubject(), m.getSubjectProfile(),
                        m.getEventDate() == null ? null : m.getEventDate().toString(),
                        m.getValidFrom() == null ? null : m.getValidFrom().toString(),
                        m.getValidTo() == null ? null : m.getValidTo().toString(),
                        m.getLocation(), m.getConfidence(),
                        AttributeRenderer.toMapList(m.getAttributes())))
                .toList();
    }

    public record MemoryView(Long id, String category, String categoryLabel, String content,
                             String subject, String subjectProfile, String eventDate, String validFrom, String validTo,
                             String location, Double confidence, List<Map<String, Object>> attributes) {}

}
