package com.butler.domain.scenario.builtin.pregnancy;

import com.butler.domain.scenario.ScenarioDomain.PlannedTask;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 把 {@link PregnancyModule} 目录按“今天的孕周”解析成带具体日期与提前量的任务。
 *
 * <p>刚性模块始终启用；可选模块仅在 focusArea 命中时叠加。每个里程碑只生成一个任务，
 * 但带两个时间：leadWeeks 决定提醒开始日期，dueWeek 决定执行/截止日期。</p>
 */
public final class PregnancyTimeline {

    private PregnancyTimeline() {}

    public static List<PlannedTask> plan(LocalDate dueDate, Set<String> enabledFocusAreas, LocalDate today) {
        return plan(dueDate, enabledFocusAreas, today, Map.of());
    }

    /**
     * @param collected 用户已收集信息；若其中包含某里程碑的“实际预约日期”（字段名由
     *                  {@link PregnancyModules#appointmentField(String)} 约定），
     *                  则用该日期覆盖按孕周推算的执行日，实现“预约确定后时间轴重排”。
     *                  提醒日仍取提前 leadWeeks 的准备起点，不会随执行日漂移；
     *                  若准备起点晚于新执行日，则退化为执行日当天提醒。
     */
    public static List<PlannedTask> plan(LocalDate dueDate, Set<String> enabledFocusAreas,
                                         LocalDate today, Map<String, String> collected) {
        PregnancyClock clock = new PregnancyClock(dueDate);
        List<PlannedTask> tasks = new ArrayList<>();

        for (PregnancyModule module : PregnancyModules.all()) {
            if (!module.mandatory() && (module.focusArea() == null
                    || !enabledFocusAreas.contains(module.focusArea()))) {
                continue;
            }
            for (PregnancyModule.Milestone m : module.milestones()) {
                LocalDate computedDue = clock.dateAtWeek(m.dueWeek());
                LocalDate remind = m.leadWeeks() > 0
                        ? clock.dateAtWeek(m.dueWeek() - m.leadWeeks()) : computedDue;
                LocalDate due = overrideDate(m.key(), collected);
                if (due == null) {
                    due = computedDue;
                } else if (remind != null && remind.isAfter(due)) {
                    remind = due;
                }
                tasks.add(new PlannedTask(m.title(), m.detail(), module.key(),
                        module.focusArea(), remind, due, m.nextHint(), m.key()));
            }
        }
        return tasks;
    }

    private static LocalDate overrideDate(String milestoneKey, Map<String, String> collected) {
        if (milestoneKey == null || collected == null) return null;
        String raw = collected.get(PregnancyModules.appointmentField(milestoneKey));
        if (raw == null || raw.isBlank()) return null;
        try {
            return com.butler.domain.model.Task.parseDueDate(raw.trim());
        } catch (Exception e) {
            return null;
        }
    }
}
