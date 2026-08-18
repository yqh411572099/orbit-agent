package com.butler;

import com.butler.domain.model.Task;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.*;

class TaskRecurrenceTest {

    @Test
    void parseRemindTimeFormats() {
        assertEquals(LocalTime.of(20, 30), Task.parseRemindTime("20:30"));
        assertNull(Task.parseRemindTime(""));
        assertNull(Task.parseRemindTime("晚上8点半"));
        assertEquals(LocalTime.of(8, 0), Task.parseRemindTime("提醒时间 08:00"));
        assertNull(Task.parseRemindTime("不固定"));
    }

    @Test
    void dailyTaskAtCustomTimeRollsToNextDay() {
        Task daily = Task.createScheduled(1L, "每天抹妊娠油", null, null, null,
                "skin_care", LocalDate.parse("2026-08-15"), LocalDate.parse("2026-08-15"),
                null, "daily", LocalTime.of(20, 30));
        assertTrue(daily.isRecurring());
        assertEquals(LocalTime.of(20, 30), daily.getRemindTime());
        Task next = daily.rollToNextOccurrence(LocalDate.parse("2026-08-15"));
        assertNotNull(next);
        assertEquals(LocalDate.parse("2026-08-16"), next.getDueDate());
        assertEquals(LocalTime.of(20, 30), next.getRemindTime());
    }

    @Test
    void nonRecurringTaskDoesNotRoll() {
        Task once = Task.createScheduled(1L, "NT检查", null, null, null,
                "prenatal_checkup", LocalDate.parse("2026-08-20"), LocalDate.parse("2026-08-20"),
                "nt", null);
        assertFalse(once.isRecurring());
        assertNull(once.rollToNextOccurrence(LocalDate.parse("2026-08-20")));
    }
}
