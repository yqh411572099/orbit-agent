package com.butler.action;

import com.butler.domain.model.RawChatLog;
import com.butler.domain.model.SessionType;
import com.butler.domain.model.SubSession;
import com.butler.domain.model.Task;
import com.butler.domain.repository.RawChatLogRepository;
import com.butler.domain.repository.SubSessionRepository;
import com.butler.domain.repository.TaskRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 任务提醒调度（不依赖 LLM，模型异常时仍可用）。
 * 定时扫描到期未提醒的任务，把提醒作为一条系统消息写入对应子对话，
 * 这样子对话内即可感知“该去产检/建档”等待办。
 */
@Service
public class ReminderService {

    private static final Logger log = LoggerFactory.getLogger(ReminderService.class);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy年M月d日");
    private static final java.time.ZoneId ZONE = java.time.ZoneId.of("Asia/Shanghai");

    private final TaskRepository taskRepository;
    private final SubSessionRepository subSessionRepository;
    private final RawChatLogRepository rawChatLogRepository;

    public ReminderService(TaskRepository taskRepository,
                           SubSessionRepository subSessionRepository,
                           RawChatLogRepository rawChatLogRepository) {
        this.taskRepository = taskRepository;
        this.subSessionRepository = subSessionRepository;
        this.rawChatLogRepository = rawChatLogRepository;
    }

    /** 每分钟扫描一次到期任务并推送（fixedRate 可被配置覆盖，测试用 fixedDelay 防止重叠）。 */
    @Scheduled(fixedDelayString = "${butler.reminder.interval-ms:60000}")
    @Transactional
    public void processDueReminders() {
        Instant now = Instant.now();
        LocalDate today = LocalDate.now(ZONE);
        rollOverdueRecurringToToday(today);
        List<Task> due = taskRepository.findDueReminders(now).stream()
                .filter(task -> task.getDueDate() != null && task.getDueDate().isEqual(today))
                .toList();
        for (Task task : due) {
            try {
                pushReminder(task);
                // 周期任务也只标记“本次已提醒”，保留在今天待用户确认完成；
                // 下一周期在用户标记完成时才生成，避免未完成就跳到明天。
                task.markReminded();
                taskRepository.save(task);
                log.info("已推送任务提醒 subSession={} task={}", task.getSubSessionId(), task.getContent());
            } catch (Exception e) {
                log.warn("推送提醒失败 taskId={} err={}", task.getId(), e.getMessage());
            }
        }
    }

    /** 周期任务（每天/每周…）若上次执行日已过且未完成，滚动到今天，不再被当作过期项。 */
    private void rollOverdueRecurringToToday(LocalDate today) {
        for (Task t : taskRepository.findOverdueRecurring(today)) {
            try {
                taskRepository.save(t.rescheduleTo(today));
            } catch (Exception e) {
                log.warn("滚动周期任务失败 taskId={} err={}", t.getId(), e.getMessage());
            }
        }
    }

    private void pushReminder(Task task) {
        SubSession sub = subSessionRepository.findById(task.getSubSessionId()).orElse(null);
        if (sub == null) {
            return;
        }
        LocalDate remindDate = task.getRemindAt() == null ? null
                : task.getRemindAt().atZone(ZONE).toLocalDate();
        String dateText;
        if (task.getDueDate() == null) {
            dateText = "";
        } else if (remindDate != null && remindDate.isBefore(task.getDueDate())) {
            dateText = "（提前准备，计划于 " + formatDate(task.getDueDate()) + " 执行）";
        } else {
            dateText = "（" + formatDate(task.getDueDate()) + "）";
        }
        StringBuilder content = new StringBuilder("⏰ 待办提醒：" + task.getContent() + dateText);
        if (task.getDetail() != null && !task.getDetail().isBlank()) {
            content.append("\n📌 ").append(task.getDetail());
        }
        content.append("\n如果已完成，可以告诉我，我帮你标记并更新计划。");
        rawChatLogRepository.save(new RawChatLog(
                null, sub.getUserId(), SessionType.SUB, task.getSubSessionId(),
                "system", content.toString(), null, Instant.now()));
    }

    /** 手动触发一次（供测试/管理接口调用）。 */
    public int triggerNow() {
        LocalDate today = LocalDate.now(ZONE);
        List<Task> due = taskRepository.findDueReminders(Instant.now()).stream()
                .filter(task -> task.getDueDate() != null && task.getDueDate().isEqual(today))
                .toList();
        for (Task task : due) {
            pushReminder(task);
            task.markReminded();
            taskRepository.save(task);
        }
        return due.size();
    }

    private String formatDate(LocalDate d) {
        return d.format(DATE_FMT);
    }
}
