package com.butler.domain.scenario.builtin;

import com.butler.domain.attribute.Attribute;
import com.butler.domain.attribute.AttributeDescriptor;
import com.butler.domain.attribute.MeasureAttribute;
import com.butler.domain.attribute.RelationAttribute;
import com.butler.domain.attribute.StatusAttribute;
import com.butler.domain.attribute.VenueAttribute;
import com.butler.domain.model.Task;
import com.butler.domain.scenario.ScenarioDomain;
import com.butler.domain.scenario.builtin.attribute.PregnancyAttributes;
import com.butler.domain.scenario.builtin.pregnancy.PregnancyTimeline;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.springframework.stereotype.Component;

/** 孕期管家场景域：区分准妈妈/准爸爸视角，内置孕检与多维度关注项。 */
@Component
public class PregnancyScenario implements ScenarioDomain {
    private static final Pattern GESTATION_PATTERN =
            Pattern.compile("(?:(\\d{4})年)?(\\d{1,2})月(\\d{1,2})日.{0,20}?孕(\\d{1,2})周(?:\\+(\\d{1,2})[天日dD])?");
    private static final Pattern WEEK_PATTERN =
            Pattern.compile("孕(\\d{1,2})周(?:\\+(\\d{1,2})[天日dD])?");

    @Override
    public String type() { return "pregnancy"; }

    @Override
    public String displayName() { return "孕期管家"; }

    @Override
    public String description() {
        return "记录孕周、产检提醒、体重/营养/饮食/运动指导、身心状态关注，以及准爸爸后勤陪产等孕期全程陪伴。";
    }

    @Override
    public String defaultSessionDesc() {
        return "用户处于孕期，关注孕周变化、产检时间表、体重营养与饮食运动、身心健康、待产与产后准备。";
    }

    @Override
    public List<CollectField> collectFields() {
        List<CollectField> base = List.of(
                CollectField.person("role", "我的身份", "选择你的身份", true,
                        List.of("准妈妈（女方）", "准爸爸（男方）")),
                CollectField.location("city", "所在城市/区县", "如：北京市朝阳区（用于查询当地产检补贴/生育政策）", false),
                CollectField.date("dueDate", "预产期", "如：2026-06-01", true),
                CollectField.measure("currentWeek", "当前孕周", "如：12", false, "周"),
                CollectField.location("hospital", "产检/建档医院", "如：市妇幼保健院", false),
                CollectField.textarea("healthNotes", "健康情况/注意事项", "如：血糖偏高、需控糖；下次B超时间8月7日", false),
                CollectField.hidden("latitude", "定位纬度"),
                CollectField.hidden("longitude", "定位经度")
        );
        java.util.List<CollectField> fields = new java.util.ArrayList<>(base);
        for (String key : com.butler.domain.scenario.builtin.pregnancy.PregnancyModules.milestoneKeys()) {
            fields.add(CollectField.hidden(
                    com.butler.domain.scenario.builtin.pregnancy.PregnancyModules.appointmentField(key),
                    milestoneLabel(key) + "预约/实际执行日期"));
        }
        return fields;
    }

    private String milestoneLabel(String key) {
        for (var module : com.butler.domain.scenario.builtin.pregnancy.PregnancyModules.all()) {
            for (var ms : module.milestones()) {
                if (key.equals(ms.key())) return ms.title();
            }
        }
        return key;
    }

    @Override
    public List<String> toolCategories() {
        return List.of("GeoService", "KnowledgeBase", "WebSearch");
    }

    @Override
    public List<FocusArea> focusAreas() {
        return List.of(
                // —— 必选（刚性骨架，不可取消）——
                new FocusArea("prenatal_checkup", "孕/产检相关",
                        "产检时间表、B超/NT/大排畸、唐筛/无创DNA、糖耐、胎心监护等节点提醒与准备", Audience.BOTH, true, true),
                new FocusArea("birth", "生产相关",
                        "建档、生育登记与生育保险、生产医院/病房选择、分娩方式与入院流程", Audience.BOTH, true, true),
                new FocusArea("postpartum_care", "产后照护",
                        "月嫂/月子预订、新生儿护理准备、出生证明/户口/医保/生育津贴等产后办理", Audience.BOTH, true, true),

                // —— 可选（默认选中常用项，可自行取消）——
                new FocusArea("weight", "体重管理",
                        "孕前BMI、各孕周合理增重目标、每周晨起空腹体重记录与复盘", Audience.FEMALE, true, false,
                        List.of("diet_nutrition")),
                new FocusArea("diet_nutrition", "饮食和营养",
                        "叶酸/铁/钙/DHA补充、三餐+加餐搭配、控糖控盐与忌口", Audience.FEMALE, true, false),
                new FocusArea("birth_prep", "待产准备",
                        "待产包清单、入院证件、分娩方式了解与分娩计划", Audience.BOTH, true, false,
                        List.of("birth")),
                new FocusArea("knowledge", "孕产知识学习",
                        "学习孕周变化与危险征兆，做到能判断、能提醒、不添乱", Audience.BOTH, false, false)
        );
    }

    @Override
    public List<PlannedTask> plannedTasks(java.util.Map<String, String> collected,
                                          List<String> focusAreas, LocalDate today) {
        String due = collected == null ? null : collected.get("dueDate");
        LocalDate dueDate = Task.parseDueDate(due);
        if (dueDate == null) {
            return List.of();
        }
        Set<String> enabled = new LinkedHashSet<>();
        if (focusAreas != null) enabled.addAll(focusAreas);
        return PregnancyTimeline.plan(dueDate, enabled, today, collected == null ? Map.of() : collected);
    }

    @Override
    public ScenarioDomain.Situation situation(java.util.Map<String, String> collected,
                                             java.util.List<String> focusAreas, LocalDate today) {
        String due = collected == null ? null : collected.get("dueDate");
        LocalDate dueDate = Task.parseDueDate(due);
        if (dueDate == null) {
            return new ScenarioDomain.Situation("", List.of());
        }
        com.butler.domain.scenario.builtin.pregnancy.PregnancyClock clock =
                new com.butler.domain.scenario.builtin.pregnancy.PregnancyClock(dueDate);
        var age = clock.ageOn(today);
        long daysToDue = java.time.temporal.ChronoUnit.DAYS.between(today, dueDate);
        StringBuilder loc = new StringBuilder();
        String city = collected.get("city");
        String lat = collected.get("latitude");
        String lon = collected.get("longitude");
        if ((city != null && !city.isBlank()) || (lat != null && lon != null && !lat.isBlank())) {
            loc.append("【用户当前位置（系统已通过浏览器授权获取，可直接使用，不要再说获取不到位置）】\n");
            if (city != null && !city.isBlank()) loc.append("- 所在城市/区县：").append(city).append("\n");
            if (lat != null && lon != null && !lat.isBlank()) loc.append("- 精确坐标：纬度").append(lat).append("，经度").append(lon).append("\n");
            loc.append("推荐附近医院/建档机构时，直接调用地图类工具用上述坐标检索，无需再向用户索要位置。\n");
        }
        String summary = "【实时孕周（按今天 " + today + " 计算，以此为准，不要沿用历史对话中的旧孕周）】\n"
                + "- 当前孕周：" + age.display() + "\n"
                + "- 预产期：" + dueDate + "（距今天 " + daysToDue + " 天）\n"
                + "回答中凡涉及“现在孕几周/第几周”，必须使用这里实时算出的孕周。\n"
                + loc;
        java.util.List<String> alerts = new java.util.ArrayList<>();
        if (daysToDue < 0) alerts.add("预产期已过 " + (-daysToDue) + " 天，请确认是否已分娩或更新预产期。");
        return new ScenarioDomain.Situation(summary, alerts);
    }

    @Override
    public List<AttributeDescriptor> attributeCatalog() {
        return List.of(
                new AttributeDescriptor(PregnancyAttributes.Profile.TYPE, "孕妇基础档案：预产期、孕周、产检医院、年龄",
                        List.of(new AttributeDescriptor.FieldSpec("dueDate","date",false,"预产期 yyyy-MM-dd"),
                                new AttributeDescriptor.FieldSpec("gestationalWeek","number",false,"当前孕周"),
                                new AttributeDescriptor.FieldSpec("hospital","string",false,"产检/建档医院"),
                                new AttributeDescriptor.FieldSpec("age","number",false,"孕妇年龄"))),
                new AttributeDescriptor(PregnancyAttributes.Checkpoint.TYPE, "关键产检/里程碑节点（NT、大排畸、糖耐、胎心监护等）",
                        List.of(new AttributeDescriptor.FieldSpec("name","string",true,"节点名称"),
                                new AttributeDescriptor.FieldSpec("dueDate","date",false,"节点日期"),
                                new AttributeDescriptor.FieldSpec("notes","string",false,"注意事项"))),
                new AttributeDescriptor(MeasureAttribute.TYPE, "可度量项：孕周、体重、BMI、血压、血糖",
                        List.of(new AttributeDescriptor.FieldSpec("name","string",true,"度量项英文名(gestational_week/weight/bmi等)"),
                                new AttributeDescriptor.FieldSpec("value","number",true,"数值"),
                                new AttributeDescriptor.FieldSpec("unit","string",false,"单位"))),
                new AttributeDescriptor(StatusAttribute.TYPE, "孕期阶段/状态（备孕期/孕早期/孕中期/孕晚期）",
                        List.of(new AttributeDescriptor.FieldSpec("stage","string",true,"阶段"))),
                new AttributeDescriptor(RelationAttribute.TYPE, "人物关系/陪产安排（谁陪同、做什么）",
                        List.of(new AttributeDescriptor.FieldSpec("party","string",true,"相关人物"),
                                new AttributeDescriptor.FieldSpec("action","string",true,"动作/职责"))),
                new AttributeDescriptor(VenueAttribute.TYPE, "地点/机构：医院、社区、月子中心",
                        List.of(new AttributeDescriptor.FieldSpec("name","string",true,"机构名称")))
        );
    }

    @Override
    public List<Class<? extends Attribute>> attributeClasses() {
        return List.of(PregnancyAttributes.Profile.class, PregnancyAttributes.Checkpoint.class);
    }

    @Override
    public List<Attribute> attributesFromCollected(java.util.Map<String, String> collected) {
        List<Attribute> attrs = new java.util.ArrayList<>();
        PregnancyAttributes.Profile profile = new PregnancyAttributes.Profile();
        boolean hasProfile = false;
        if (collected.containsKey("dueDate") && !collected.get("dueDate").isBlank()) {
            profile.setDueDate(collected.get("dueDate")); hasProfile = true;
        }
        if (collected.containsKey("currentWeek") && !collected.get("currentWeek").isBlank()) {
            try { profile.setGestationalWeek(Integer.parseInt(collected.get("currentWeek").replaceAll("[^0-9]", ""))); hasProfile = true; }
            catch (Exception ignored) {}
        }
        if (collected.containsKey("hospital") && !collected.get("hospital").isBlank()) {
            profile.setHospital(collected.get("hospital")); hasProfile = true;
        }
        if (hasProfile) attrs.add(profile);
        return attrs;
    }

    @Override
    public String subjectProfileFromCollected(java.util.Map<String, String> collected) {
        String role = collected.getOrDefault("role", "");
        if (role.isBlank()) return null;
        try {
            var map = new java.util.LinkedHashMap<String, Object>();
            map.put("role", role);
            if (role.contains("准爸爸")) map.put("relatedParty", "孕妇");
            else if (role.contains("准妈妈")) map.put("relatedParty", "宝宝");
            return new ObjectMapper().writeValueAsString(map);
        } catch (Exception e) {
            return "{...}";
        }
    }

    @Override
    public List<String> initialTasks(String goal) {
        return List.of("建立产检时间表与提醒", "整理孕期注意事项与异常信号清单",
                "制定体重/营养/饮食管理计划", "准备待产包与入院证件清单");
    }

    @Override
    public Map<String, String> normalizeCollected(String goal, Map<String, String> collected, LocalDate today) {
        Map<String, String> result = new LinkedHashMap<>(collected == null ? Map.of() : collected);
        String dueDate = inferDueDate(goal == null ? "" : goal, today);
        if (dueDate != null) {
            result.put("dueDate", dueDate);
        } else if (result.get("dueDate") == null && result.get("currentWeek") != null) {
            Integer week = parseInt(result.get("currentWeek"));
            if (week != null && week > 0 && week <= 42) {
                result.put("dueDate", today.plusDays(280L - week * 7L).toString());
            }
        }
        return result;
    }

    @Override
    public Map<String, String> normalizeUpdates(String message, Map<String, String> updates, LocalDate today) {
        Map<String, String> result = new LinkedHashMap<>(updates == null ? Map.of() : updates);
        String text = message == null ? "" : message;
        // 预产期/孕周只能由“明确的孕周表述”确定性推导，避免模型把某检查的预约日期误判成当前孕周而整体平移时间轴。
        String dueDate = inferDueDate(text, today);
        if (dueDate != null) {
            result.put("dueDate", dueDate);
        } else if (!explicitlyMentionsGestation(text)) {
            result.remove("dueDate");
            result.remove("currentWeek");
        }
        // 里程碑预约/执行日期（milestone_<key>_date）由模型语义提取并放入 updates；
        // 这里确定性地把日期值归一化为 yyyy-MM-dd，无法解析则丢弃，避免自然语言日期落库。
        result.replaceAll((k, v) -> {
            if (k != null && k.startsWith("milestone_") && k.endsWith("_date") && v != null) {
                LocalDate d = Task.parseDueDate(v);
                return d == null ? null : d.toString();
            }
            return v;
        });
        result.values().removeIf(Objects::isNull);
        return result;
    }

    private boolean explicitlyMentionsGestation(String text) {
        return GESTATION_PATTERN.matcher(text).find() || WEEK_PATTERN.matcher(text).find();
    }

    private static final Pattern DUE_DATE_PATTERN =
            Pattern.compile("预产期[^0-9]{0,6}((\\d{4})[-/年.])?(\\d{1,2})[-/月.](\\d{1,2})\\s*[号日]?");

    private String inferDueDate(String text, LocalDate today) {
        Matcher dueMatcher = DUE_DATE_PATTERN.matcher(text);
        if (dueMatcher.find()) {
            LocalDate d = parseReferenceDate(dueMatcher.group(2),
                    Integer.parseInt(dueMatcher.group(3)), Integer.parseInt(dueMatcher.group(4)), today);
            if (d != null) return d.toString();
        }
        Matcher matcher = GESTATION_PATTERN.matcher(text);
        if (matcher.find()) {
            LocalDate reference = parseReferenceDate(matcher.group(1),
                    Integer.parseInt(matcher.group(2)), Integer.parseInt(matcher.group(3)), today);
            int week = Integer.parseInt(matcher.group(4));
            int day = matcher.group(5) == null ? 0 : Integer.parseInt(matcher.group(5));
            long pregnantDays = week * 7L + day;
            if (week <= 42 && day <= 6 && reference != null) {
                return reference.plusDays(280 - pregnantDays).toString();
            }
        }
        Matcher weekMatcher = WEEK_PATTERN.matcher(text);
        if (weekMatcher.find()) {
            int week = Integer.parseInt(weekMatcher.group(1));
            int day = weekMatcher.group(2) == null ? 0 : Integer.parseInt(weekMatcher.group(2));
            if (week <= 42 && day <= 6) {
                return today.plusDays(280 - (week * 7L + day)).toString();
            }
        }
        return null;
    }

    private LocalDate parseReferenceDate(String yearText, int month, int day, LocalDate today) {
        try {
            if (yearText != null && !yearText.isBlank()) {
                return LocalDate.of(Integer.parseInt(yearText), month, day);
            }
            LocalDate date = LocalDate.of(today.getYear(), month, day);
            if (date.isAfter(today.plusDays(180))) {
                return date.minusYears(1);
            }
            if (date.isBefore(today.minusDays(180))) {
                return date.plusYears(1);
            }
            return date;
        } catch (Exception e) {
            return null;
        }
    }

    private Integer parseInt(String raw) {
        if (raw == null) return null;
        Matcher matcher = Pattern.compile("\\d+").matcher(raw);
        return matcher.find() ? Integer.parseInt(matcher.group()) : null;
    }
}
