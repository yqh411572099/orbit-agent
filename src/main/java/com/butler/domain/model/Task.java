package com.butler.domain.model;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.Year;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Task {
    private final Long id;
    private final Long subSessionId;
    private final String content;
    private String detail;
    private final String nextHint;
    private final String moduleKey;
    private final String milestoneKey;
    private final String recurrence;
    private final String focusArea;
    private boolean completed;
    private final Instant remindAt;
    private final LocalDate dueDate;
    private boolean reminded;
    /** 到点提醒时是否需要 LLM 结合近况动态生成内容；非空表示需要，内容为给模型的生成指令。 */
    private final String aiBrief;

    public static Task createScheduled(Long subSessionId, String content, LocalDate dueDate) {
        return createScheduled(subSessionId, content, null, null, null, null, dueDate);
    }

    public static Task createScheduled(Long subSessionId, String content, String focusArea, LocalDate dueDate) {
        return createScheduled(subSessionId, content, null, null, null, focusArea, dueDate);
    }

    public static Task createScheduled(Long subSessionId, String content, String detail, String moduleKey,
                                       String focusArea, LocalDate dueDate) {
        return createScheduled(subSessionId, content, detail, null, moduleKey, focusArea, dueDate);
    }

    /**
     * 生成带提醒的任务：到期日当天 09:00（Asia/Shanghai）推送；无日期则不提醒。
     *
     * @param detail    提醒时附带的知识/准备事项，可为空
     * @param nextHint  任务完成后推进下一节点的提示，可为空
     * @param moduleKey 所属时间轴模块 key，可为空
     */
    public static Task createScheduled(Long subSessionId, String content, String detail, String nextHint,
                                       String moduleKey, String focusArea, LocalDate dueDate) {
        return createScheduled(subSessionId, content, detail, nextHint, moduleKey, focusArea, dueDate, dueDate);
    }

    /**
     * @param remindDate 提醒开始日期；为空则与 dueDate 同一天提醒
     * @param dueDate    执行/截止日期
     */
    public static Task createScheduled(Long subSessionId, String content, String detail, String nextHint,
                                       String moduleKey, String focusArea,
                                       LocalDate remindDate, LocalDate dueDate) {
        LocalDate remindOn = remindDate != null ? remindDate : dueDate;
        Instant remindAt = remindOn == null ? null
                : remindOn.atTime(9, 0).atZone(ZoneId.of("Asia/Shanghai")).toInstant();
        return createScheduled(subSessionId, content, detail, nextHint, moduleKey, focusArea, remindDate, dueDate, null);
    }

    public static Task createScheduled(Long subSessionId, String content, String detail, String nextHint,
                                       String moduleKey, String focusArea,
                                       LocalDate remindDate, LocalDate dueDate, String milestoneKey) {
        LocalDate remindOn = remindDate != null ? remindDate : dueDate;
        Instant remindAt = remindOn == null ? null
                : remindOn.atTime(9, 0).atZone(ZoneId.of("Asia/Shanghai")).toInstant();
        return createScheduled(subSessionId, content, detail, nextHint, moduleKey, focusArea,
                remindDate, dueDate, milestoneKey, null);
    }

    /**
     * @param recurrence 周期：daily/weekly/biweekly/monthly；为空表示一次性任务。
     *                   周期性任务在提醒推送后自动滚动到下一周期，直到用户标记完成。
     */
    public static Task createScheduled(Long subSessionId, String content, String detail, String nextHint,
                                       String moduleKey, String focusArea,
                                       LocalDate remindDate, LocalDate dueDate, String milestoneKey,
                                       String recurrence) {
        return createScheduled(subSessionId, content, detail, nextHint, moduleKey, focusArea,
                remindDate, dueDate, milestoneKey, recurrence, null);
    }

    /**
     * @param recurrence 周期：daily/weekly/biweekly/monthly；为空表示一次性任务。
     *                   周期性任务在提醒推送后自动滚动到下一周期，直到用户标记完成。
     * @param remindTime 每天提醒的具体时刻（几点几分）；为空默认 09:00。仅影响提醒触发时刻。
     */
    public static Task createScheduled(Long subSessionId, String content, String detail, String nextHint,
                                       String moduleKey, String focusArea,
                                       LocalDate remindDate, LocalDate dueDate, String milestoneKey,
                                       String recurrence, LocalTime remindTime) {
        LocalDate remindOn = remindDate != null ? remindDate : dueDate;
        LocalTime time = remindTime != null ? remindTime : LocalTime.of(9, 0);
        Instant remindAt = remindOn == null ? null
                : remindOn.atTime(time).atZone(ZoneId.of("Asia/Shanghai")).toInstant();
        return new Task(null, subSessionId, content, detail, nextHint, moduleKey, milestoneKey, recurrence, focusArea, false, remindAt, dueDate, false, null);
    }

    /**
     * 动态（LLM 规划）任务：可带周期、提醒时刻与 AI 生成指令。
     * aiBrief 非空时，到点提醒会调用 LLM 结合近况动态生成本次推送内容（如每日食谱）。
     */
    public static Task createDynamic(Long subSessionId, String content, String detail, String focusArea,
                                     LocalDate dueDate, String recurrence, LocalTime remindTime, String aiBrief) {
        LocalDate due = dueDate != null ? dueDate : LocalDate.now(ZoneId.of("Asia/Shanghai"));
        LocalTime time = remindTime != null ? remindTime : LocalTime.of(9, 0);
        Instant remindAt = due.atTime(time).atZone(ZoneId.of("Asia/Shanghai")).toInstant();
        return new Task(null, subSessionId, content, detail, null, null, null, recurrence, focusArea,
                false, remindAt, due, false, aiBrief);
    }

    /** 解析提醒时刻，仅接受 HH:mm（24 小时制），无法解析返回 null。 */
    public static LocalTime parseRemindTime(String raw) {
        if (raw == null) return null;
        Matcher m = Pattern.compile("(\\d{1,2}):(\\d{2})").matcher(raw.trim());
        if (m.find()) {
            int h = Integer.parseInt(m.group(1));
            int min = Integer.parseInt(m.group(2));
            if (h >= 0 && h <= 23 && min >= 0 && min <= 59) return LocalTime.of(h, min);
        }
        return null;
    }

    /** 提醒时刻（Asia/Shanghai），用于前端展示“每天 HH:mm”。 */
    public LocalTime getRemindTime() {
        return remindAt == null ? null : remindAt.atZone(ZoneId.of("Asia/Shanghai")).toLocalTime();
    }

    public Task(Long id, Long subSessionId, String content, boolean completed,
                Instant remindAt, LocalDate dueDate, boolean reminded) {
        this(id, subSessionId, content, null, null, null, null, completed, remindAt, dueDate, reminded);
    }

    public Task(Long id, Long subSessionId, String content, String focusArea, boolean completed,
                Instant remindAt, LocalDate dueDate, boolean reminded) {
        this(id, subSessionId, content, null, null, null, focusArea, completed, remindAt, dueDate, reminded);
    }

    public Task(Long id, Long subSessionId, String content, String detail, String moduleKey, String focusArea,
                boolean completed, Instant remindAt, LocalDate dueDate, boolean reminded) {
        this(id, subSessionId, content, detail, null, moduleKey, focusArea, completed, remindAt, dueDate, reminded);
    }

    public Task(Long id, Long subSessionId, String content, String detail, String nextHint, String moduleKey,
                String focusArea, boolean completed, Instant remindAt, LocalDate dueDate, boolean reminded) {
        this(id, subSessionId, content, detail, nextHint, moduleKey, null, focusArea, completed, remindAt, dueDate, reminded);
    }

    public Task(Long id, Long subSessionId, String content, String detail, String nextHint, String moduleKey,
                String milestoneKey, String focusArea, boolean completed, Instant remindAt, LocalDate dueDate, boolean reminded) {
        this(id, subSessionId, content, detail, nextHint, moduleKey, milestoneKey, null, focusArea, completed, remindAt, dueDate, reminded);
    }

    public Task(Long id, Long subSessionId, String content, String detail, String nextHint, String moduleKey,
                String milestoneKey, String recurrence, String focusArea, boolean completed, Instant remindAt, LocalDate dueDate, boolean reminded) {
        this(id, subSessionId, content, detail, nextHint, moduleKey, milestoneKey, recurrence, focusArea,
                completed, remindAt, dueDate, reminded, null);
    }

    public Task(Long id, Long subSessionId, String content, String detail, String nextHint, String moduleKey,
                String milestoneKey, String recurrence, String focusArea, boolean completed, Instant remindAt,
                LocalDate dueDate, boolean reminded, String aiBrief) {
        this.id = id;
        this.subSessionId = subSessionId;
        this.content = content;
        this.detail = detail;
        this.nextHint = nextHint;
        this.moduleKey = moduleKey;
        this.milestoneKey = milestoneKey;
        this.recurrence = recurrence;
        this.focusArea = focusArea;
        this.completed = completed;
        this.remindAt = remindAt;
        this.dueDate = dueDate;
        this.reminded = reminded;
        this.aiBrief = aiBrief;
    }

    public static LocalDate parseDueDate(String raw) {
        if (raw == null) return null;
        String v = raw.trim();
        if (v.isBlank()) return null;
        try {
            String prefix = v.substring(0, Math.min(10, v.length()));
            return LocalDate.parse(prefix, DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (Exception ignored) {
        }
        try {
            Matcher matcher = Pattern.compile("(\\d{4})[-/.年](\\d{1,2})[-/.月](\\d{1,2})").matcher(v);
            if (matcher.find()) {
                return LocalDate.of(Integer.parseInt(matcher.group(1)),
                        Integer.parseInt(matcher.group(2)), Integer.parseInt(matcher.group(3)));
            }
            matcher = Pattern.compile("(\\d{1,2})[-/.月](\\d{1,2})").matcher(v);
            if (matcher.find()) {
                int month = Integer.parseInt(matcher.group(1));
                int day = Integer.parseInt(matcher.group(2));
                int year = Year.now().getValue();
                LocalDate candidate = LocalDate.of(year, month, day);
                if (candidate.isBefore(LocalDate.now(ZoneId.of("Asia/Shanghai")).minusDays(30))) {
                    candidate = candidate.plusYears(1);
                }
                return candidate;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    public Long getId() { return id; }
    public Long getSubSessionId() { return subSessionId; }
    public String getContent() { return content; }
    public String getDetail() { return detail; }
    public String getNextHint() { return nextHint; }
    public String getModuleKey() { return moduleKey; }
    public String getMilestoneKey() { return milestoneKey; }
    public String getRecurrence() { return recurrence; }
    public String getFocusArea() { return focusArea; }
    public boolean isCompleted() { return completed; }
    public Instant getRemindAt() { return remindAt; }
    public LocalDate getDueDate() { return dueDate; }
    public boolean isReminded() { return reminded; }
    public String getAiBrief() { return aiBrief; }
    /** 到点提醒是否需要 LLM 结合近况动态生成本次内容。 */
    public boolean isAiAssisted() { return aiBrief != null && !aiBrief.isBlank(); }

    /** 把周期任务重新排到指定日期（保留提醒时刻），用于把过期的“每天/每周”任务滚动到今天。 */
    public Task rescheduleTo(LocalDate date) {
        LocalTime time = getRemindTime() != null ? getRemindTime() : LocalTime.of(9, 0);
        Instant remind = date.atTime(time).atZone(ZoneId.of("Asia/Shanghai")).toInstant();
        return new Task(id, subSessionId, content, detail, nextHint, moduleKey, milestoneKey,
                recurrence, focusArea, completed, remind, date, false, aiBrief);
    }

    public void markCompleted() { this.completed = true; }
    public void markReminded() { this.reminded = true; }

    /** 是否为周期性任务（提醒后自动滚动到下一周期，直到被标记完成）。 */
    public boolean isRecurring() {
        return recurrence != null && !recurrence.isBlank() && !"none".equalsIgnoreCase(recurrence);
    }

    /**
     * 展示/分组用的“生效执行日”：周期任务（每天/每周…）若上次执行日已过，
     * 滚动到今天，作为今天这一次的待办，而不是被当成历史过期项。
     */
    public LocalDate effectiveDueDate(LocalDate today) {
        if (isRecurring() && dueDate != null && dueDate.isBefore(today)) {
            return today;
        }
        return dueDate;
    }

    /** 滚动到下一周期：生成一条新任务（提醒状态重置），用于替换已推送的本次任务。 */
    public Task rollToNextOccurrence(LocalDate today) {
        if (!isRecurring() || dueDate == null) return null;
        LocalDate nextDue = switch (recurrence.toLowerCase()) {
            case "daily" -> dueDate.plusDays(1);
            case "weekly" -> dueDate.plusWeeks(1);
            case "biweekly" -> dueDate.plusWeeks(2);
            case "monthly" -> dueDate.plusMonths(1);
            default -> dueDate.plusDays(1);
        };
        LocalDate base = nextDue.isAfter(today) ? nextDue : today;
        LocalDate remindOn = remindAt == null ? base
                : remindAt.atZone(ZoneId.of("Asia/Shanghai")).toLocalDate();
        if (remindOn.isBefore(base)) remindOn = base;
        LocalTime time = getRemindTime() != null ? getRemindTime() : LocalTime.of(9, 0);
        return new Task(null, subSessionId, content, detail, nextHint, moduleKey, milestoneKey,
                recurrence, focusArea, false,
                remindOn.atTime(time).atZone(ZoneId.of("Asia/Shanghai")).toInstant(),
                base, false, aiBrief);
    }

    /** 用运行时检索到的地区政策要点补充任务详情。 */
    public void appendDetail(String extra) {
        if (extra == null || extra.isBlank()) return;
        this.detail = (this.detail == null ? "" : this.detail) + "\n\n【当地政策】" + extra;
    }

    public boolean isDueForReminder(Instant now) {
        return !completed && !reminded && remindAt != null && !remindAt.isAfter(now);
    }
}
