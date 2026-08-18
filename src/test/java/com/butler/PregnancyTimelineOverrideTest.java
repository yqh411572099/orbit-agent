package com.butler;

import com.butler.domain.scenario.ScenarioDomain.PlannedTask;
import com.butler.domain.scenario.builtin.pregnancy.PregnancyModules;
import com.butler.domain.scenario.builtin.pregnancy.PregnancyTimeline;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PregnancyTimelineOverrideTest {

    private static final LocalDate DUE = LocalDate.parse("2027-03-13");
    private static final LocalDate TODAY = LocalDate.parse("2026-08-14");

    private PlannedTask nt(java.util.List<PlannedTask> tasks) {
        return tasks.stream().filter(t -> "nt".equals(t.milestoneKey())).findFirst().orElseThrow();
    }

    @Test
    void ntFallsBackToComputedWeekDate() {
        PlannedTask task = nt(PregnancyTimeline.plan(DUE, Set.of(), TODAY));
        assertEquals(LocalDate.parse("2026-08-29"), task.dueDate(), "未提供预约日期时按12周推算");
        assertNotNull(task.milestoneKey());
    }

    @Test
    void appointmentDateOverridesDueDate() {
        Map<String, String> collected = Map.of(
                PregnancyModules.appointmentField("nt"), "2026-08-20");
        PlannedTask task = nt(PregnancyTimeline.plan(DUE, Set.of(), TODAY, collected));
        assertEquals(LocalDate.parse("2026-08-20"), task.dueDate(), "预约日期应覆盖执行日");
        assertNotNull(task.remindDate(), "提醒日仍保留（提前准备起点）");
        assertFalse(task.remindDate().isAfter(task.dueDate()), "提醒日不得晚于执行日");
    }

    @Test
    void otherMilestonesUnaffected() {
        Map<String, String> collected = Map.of(
                PregnancyModules.appointmentField("nt"), "2026-08-20");
        var tasks = PregnancyTimeline.plan(DUE, Set.of(), TODAY, collected);
        PlannedTask anomaly = tasks.stream().filter(t -> "anomaly_scan".equals(t.milestoneKey())).findFirst().orElseThrow();
        assertEquals(LocalDate.parse("2026-11-07"), anomaly.dueDate(), "其他里程碑日期不受影响");
    }
}
