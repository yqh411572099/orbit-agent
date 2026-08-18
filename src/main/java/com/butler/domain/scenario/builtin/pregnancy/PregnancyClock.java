package com.butler.domain.scenario.builtin.pregnancy;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * 孕周时钟：基于预产期（=孕40周+0天）反推任意日期的孕周，以及某孕周对应的公历日期。
 *
 * <p>这是“时间轴驱动”的确定性核心，取代让 LLM 猜日期。所有产检窗口、建档、提前量提醒
 * 都以“孕周”为锚点，再由本类换算成具体日期。</p>
 */
public record PregnancyClock(LocalDate dueDate) {

    private static final int DAYS_AT_TERM = 280; // 40 周 * 7

    public PregnancyClock {
        if (dueDate == null) {
            throw new IllegalArgumentException("预产期不能为空");
        }
    }

    /** 指定日期对应的孕龄（周、天）。 */
    public GestationalAge ageOn(LocalDate date) {
        long daysPregnant = DAYS_AT_TERM - ChronoUnit.DAYS.between(date, dueDate);
        if (daysPregnant < 0) daysPregnant = 0;
        return new GestationalAge((int) (daysPregnant / 7), (int) (daysPregnant % 7));
    }

    /** 今天的孕周。 */
    public GestationalAge ageToday(LocalDate today) {
        return ageOn(today);
    }

    /** 某孕周（+0天）对应的公历日期。 */
    public LocalDate dateAtWeek(int week) {
        return dueDate.minusDays((long) (40 - week) * 7);
    }

    /** 某孕周窗口内一个日期：默认取窗口起始周（让任务尽早进入准备/执行）。 */
    public LocalDate dateAtWeekStart(int week) {
        return dateAtWeek(week);
    }

    /** 在基准孕周上偏移 n 周。 */
    public LocalDate dateAtWeek(int week, int offsetWeeks) {
        return dateAtWeek(week + offsetWeeks);
    }

    public record GestationalAge(int weeks, int days) {
        public int totalWeeksFloor() { return weeks; }
        public String display() { return weeks + "周" + (days > 0 ? "+" + days + "天" : ""); }
    }
}
