package com.butler.application.tool;

import com.butler.domain.agent.AgentTool;
import com.butler.domain.agent.ToolContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * 日历计算：日期差（相距多少天/周）、按偏移推算日期、求星期几等确定性时间运算。
 * 让模型把“距今多久、还剩几周、N 天后是几号”交给工具，避免心算出错。
 * 基准“今天”优先取上下文锚定日期，缺省用系统当天。
 */
@Component
public class CalendarCalcTool implements AgentTool {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private final ObjectMapper mapper = new ObjectMapper();

    @Override public String name() { return "calendar_calc"; }

    @Override public String description() {
        return "日历/日期计算：算两个日期相差多少天或多少周、从某天往前/后推 N 天是几号、某天是星期几，"
                + "适用于还剩多久、距今几天/几周、预产期倒计时、备考剩余天数等。日期格式 yyyy-MM-dd。";
    }

    @Override public String parametersSchema() {
        ObjectNode root = mapper.createObjectNode();
        ObjectNode props = root.putObject("properties");
        // 注意：工具大类 ToolCategory 已占用 sub_tool 字段表示“选哪个子工具”，
        // 这里日历子工具内部的运算类型用 op，避免与大类 sub_tool 冲突被覆盖。
        ObjectNode sub = props.putObject("op");
        sub.put("type", "string");
        sub.put("description", "日期运算类型：diff(两日期相差天数/周数)、add(基准日期加减天数得到新日期)、weekday(某日是星期几)。");
        sub.putArray("enum").add("diff").add("add").add("weekday");
        props.putObject("date").put("type", "string")
                .put("description", "目标日期 yyyy-MM-dd。diff 时为结束日期；add 时为基准日期；weekday 时为要查询的日期。");
        props.putObject("startDate").put("type", "string")
                .put("description", "diff 时可选，起始日期 yyyy-MM-dd；省略则用今天。");
        props.putObject("days").put("type", "integer")
                .put("description", "add 时必填：要偏移的天数，正数向后、负数向前。");
        ObjectNode unit = props.putObject("unit");
        unit.put("type", "string");
        unit.put("description", "diff 时可选结果单位：days(默认) 或 weeks(保留1位小数)。");
        unit.putArray("enum").add("days").add("weeks");
        props.putObject("query").put("type", "string")
                .put("description", "可选，自然语言说明在算什么（如“距春节还有几周”），便于结果可读。");
        root.putArray("required").add("op");
        return root.toString();
    }

    @Override public String execute(String argumentsJson, ToolContext context) {
        try {
            JsonNode args = mapper.readTree(argumentsJson == null ? "{}" : argumentsJson);
            String sub = args.path("op").asText(args.path("sub_tool").asText("")).trim().toLowerCase();
            // 若大类把 sub_tool=calendar_calc 透传进来，op 缺失时不要误当运算类型
            if ("calendar_calc".equals(sub) || "math_calc".equals(sub)) sub = "";
            String note = args.path("query").asText("");
            LocalDate today = context != null && context.today() != null
                    ? context.today() : LocalDate.now(java.time.ZoneId.of("Asia/Shanghai"));
            StringBuilder out = new StringBuilder();
            if (note != null && !note.isBlank()) out.append("（").append(note).append("）\n");

            switch (sub) {
                case "diff" -> {
                    LocalDate end = parse(args.path("date").asText(""));
                    if (end == null) return "diff 需要 date（结束日期 yyyy-MM-dd）。";
                    LocalDate start = args.has("startDate") && !args.path("startDate").asText("").isBlank()
                            ? parse(args.path("startDate").asText("")) : today;
                    if (start == null) return "startDate 格式应为 yyyy-MM-dd。";
                    long days = ChronoUnit.DAYS.between(start, end);
                    String unit = args.path("unit").asText("days");
                    out.append(start).append(" 到 ").append(end).append("：");
                    if ("weeks".equals(unit)) {
                        double weeks = days / 7.0;
                        out.append("相差 ").append(days).append(" 天，约 ")
                           .append(String.format(Locale.ROOT, "%.1f", Math.abs(weeks))).append(" 周")
                           .append(days >= 0 ? "（之后）" : "（之前）");
                    } else {
                        out.append(days >= 0 ? "还剩/之后 " : "已过/之前 ").append(Math.abs(days)).append(" 天");
                    }
                }
                case "add" -> {
                    LocalDate base = parse(args.path("date").asText(""));
                    if (base == null) return "add 需要 date（基准日期 yyyy-MM-dd）。";
                    if (!args.path("days").isInt()) return "add 需要整数 days（偏移天数）。";
                    int days = args.path("days").asInt();
                    LocalDate result = base.plusDays(days);
                    out.append(base).append(days >= 0 ? " 加 " : " 减 ").append(Math.abs(days))
                       .append(" 天 = ").append(result.format(ISO)).append("（").append(weekday(result)).append("）");
                }
                case "weekday" -> {
                    LocalDate d = parse(args.path("date").asText(""));
                    if (d == null) return "weekday 需要 date（yyyy-MM-dd）。";
                    out.append(d.format(ISO)).append(" 是").append(weekday(d));
                }
                default -> { return "op 取 diff / add / weekday。"; }
            }
            return out.toString();
        } catch (Exception e) {
            return "日期计算失败：" + e.getMessage();
        }
    }

    private LocalDate parse(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try { return LocalDate.parse(raw.trim().substring(0, Math.min(10, raw.trim().length()))); }
        catch (Exception e) { return null; }
    }

    private String weekday(LocalDate d) {
        DayOfWeek w = d.getDayOfWeek();
        return switch (w) {
            case MONDAY -> "星期一";
            case TUESDAY -> "星期二";
            case WEDNESDAY -> "星期三";
            case THURSDAY -> "星期四";
            case FRIDAY -> "星期五";
            case SATURDAY -> "星期六";
            case SUNDAY -> "星期日";
        };
    }

}
