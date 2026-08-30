package com.butler.agent;

import static org.junit.jupiter.api.Assertions.*;

import com.butler.application.tool.CalendarCalcTool;
import com.butler.application.tool.MathCalcTool;
import com.butler.domain.agent.ToolContext;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/**
 * 确定性计算工具单测：不连模型、不连网络，直接验证加减乘除、舍入与日期运算结果。
 */
class CalculatorToolsTest {

    private final MathCalcTool math = new MathCalcTool();
    private final CalendarCalcTool cal = new CalendarCalcTool();
    private final ToolContext ctx =
            new ToolContext(1L, "sub", 99L, "generic", java.util.Map.of(), LocalDate.of(2026, 8, 30));

    // ---------- 数学工具 ----------

    @Test
    void mathAddsMultipleNumbers() throws Exception {
        String out = math.execute("{\"operation\":\"add\",\"numbers\":[140,80]}", ctx);
        assertTrue(out.contains("220"), out);
    }

    @Test
    void mathSubtractsTargetWeight() throws Exception {
        String out = math.execute("{\"operation\":\"subtract\",\"numbers\":[185,165]}", ctx);
        assertTrue(out.contains("20"), out);
    }

    @Test
    void mathDividesAndRoundsToOneDecimal() throws Exception {
        String out = math.execute("{\"operation\":\"divide\",\"numbers\":[20,23],\"scale\":1}", ctx);
        assertTrue(out.contains("0.9"), out);
    }

    @Test
    void mathMultiply() throws Exception {
        String out = math.execute("{\"operation\":\"multiply\",\"numbers\":[7,23]}", ctx);
        assertTrue(out.contains("161"), out);
    }

    @Test
    void mathDefaultsScaleToTwoDecimals() throws Exception {
        String out = math.execute("{\"operation\":\"divide\",\"numbers\":[10,4]}", ctx);
        assertTrue(out.contains("2.5"), out);
    }

    @Test
    void mathDivisionByZeroGuarded() throws Exception {
        String out = math.execute("{\"operation\":\"divide\",\"numbers\":[10,0]}", ctx);
        assertTrue(out.contains("不能为 0") || out.contains("除数"), out);
    }

    @Test
    void mathRequiresAtLeastTwoNumbers() throws Exception {
        String out = math.execute("{\"operation\":\"add\",\"numbers\":[100]}", ctx);
        assertTrue(out.contains("至少 2 个数字"), out);
    }

    @Test
    void mathRejectsUnknownOperation() throws Exception {
        String out = math.execute("{\"operation\":\"pow\",\"numbers\":[2,3]}", ctx);
        assertTrue(out.contains("不支持的运算"), out);
    }

    // ---------- 日历工具 ----------

    @Test
    void calendarDiffDaysToSpringFestival() throws Exception {
        String out = cal.execute(
                "{\"op\":\"diff\",\"date\":\"2027-02-06\",\"unit\":\"days\"}", ctx);
        // 2026-08-30 -> 2027-02-06 应为 160 天
        assertTrue(out.contains("160"), out);
    }

    @Test
    void calendarDiffWeeks() throws Exception {
        String out = cal.execute(
                "{\"op\":\"diff\",\"date\":\"2027-02-06\",\"unit\":\"weeks\"}", ctx);
        assertTrue(out.contains("160"), out);
        assertTrue(out.contains("22.9"), out);
    }

    @Test
    void calendarWeekday() throws Exception {
        String out = cal.execute("{\"op\":\"weekday\",\"date\":\"2027-02-06\"}", ctx);
        assertTrue(out.contains("星期六"), out);
    }

    @Test
    void calendarAddDays() throws Exception {
        String out = cal.execute("{\"op\":\"add\",\"date\":\"2026-08-30\",\"days\":7}", ctx);
        assertTrue(out.contains("2026-09-06"), out);
        assertTrue(out.contains("星期日"), out);
    }

    @Test
    void calendarAddNegativeDays() throws Exception {
        String out = cal.execute("{\"op\":\"add\",\"date\":\"2026-08-30\",\"days\":-30}", ctx);
        assertTrue(out.contains("2026-07-31"), out);
    }

    @Test
    void calendarDiffWithExplicitStart() throws Exception {
        String out = cal.execute(
                "{\"op\":\"diff\",\"startDate\":\"2026-08-01\",\"date\":\"2026-08-31\",\"unit\":\"days\"}", ctx);
        assertTrue(out.contains("30"), out);
    }

    @Test
    void calendarRequiresDateForDiff() throws Exception {
        String out = cal.execute("{\"op\":\"diff\"}", ctx);
        assertTrue(out.contains("date"), out);
    }

    @Test
    void calendarRejectsUnknownOp() throws Exception {
        String out = cal.execute("{\"op\":\"fortnight\"}", ctx);
        assertTrue(out.contains("diff / add / weekday") || out.contains("op"), out);
    }
}
