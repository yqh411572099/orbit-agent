package com.butler;

import com.butler.domain.scenario.builtin.PregnancyScenario;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PregnancyNormalizeUpdatesTest {

    private final PregnancyScenario scenario = new PregnancyScenario();
    private final LocalDate today = LocalDate.parse("2026-08-14");

    @Test
    void explicitDueDateIsDetected() {
        Map<String, String> r = scenario.normalizeUpdates(
                "预产期2027-03-13", Map.of(), today);
        assertEquals("2027-03-13", r.get("dueDate"));
    }

    @Test
    void gestationWeekInfersDueDate() {
        Map<String, String> r = scenario.normalizeUpdates(
                "现在是孕12周", Map.of(), today);
        assertNotNull(r.get("dueDate"));
    }

    @Test
    void doesNotDeriveDueDateFromCheckupDate() {
        // “NT约到8月20号”不得自行反推预产期/孕周（里程碑日期由模型写入对应字段）
        Map<String, String> r = scenario.normalizeUpdates(
                "NT约到8月20号", Map.of(), today);
        assertNull(r.get("dueDate"));
        assertNull(r.get("currentWeek"));
    }

    @Test
    void milestoneFieldFromModelIsNormalizedToIsoDate() {
        Map<String, String> r = scenario.normalizeUpdates("NT预约到8月20号",
                Map.of("milestone_nt_date", "8月20号"), today);
        assertEquals("2026-08-20", r.get("milestone_nt_date"));
    }

    @Test
    void unparseableMilestoneDateIsDropped() {
        Map<String, String> r = scenario.normalizeUpdates("时间定了",
                Map.of("milestone_nt_date", "下下周"), today);
        assertFalse(r.containsKey("milestone_nt_date"));
    }

    @Test
    void noDateNoChange() {
        Map<String, String> r = scenario.normalizeUpdates("今天有点累", Map.of(), today);
        assertTrue(r.isEmpty());
    }
}
