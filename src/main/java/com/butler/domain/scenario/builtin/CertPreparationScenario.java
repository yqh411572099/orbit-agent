package com.butler.domain.scenario.builtin;

import com.butler.domain.attribute.Attribute;
import com.butler.domain.attribute.AttributeDescriptor;
import com.butler.domain.attribute.MeasureAttribute;
import com.butler.domain.attribute.StatusAttribute;
import com.butler.domain.attribute.VenueAttribute;
import com.butler.domain.model.Task;
import com.butler.domain.scenario.ScenarioDomain;
import com.butler.domain.scenario.builtin.attribute.CertAttributes;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/** 证书备考场景域。 */
@Component
public class CertPreparationScenario implements ScenarioDomain {

    @Override
    public String type() { return "cert_prep"; }

    @Override
    public String displayName() { return "证书备考"; }

    @Override
    public String description() { return "为 PMP、软考、语言等级等证书考试制定备考计划与刷题安排。"; }

    @Override
    public String defaultSessionDesc() {
        return "用户正在准备某项职业/语言证书考试，关注考试时间、知识点复习、刷题与模考安排。";
    }

    @Override
    public List<CollectField> collectFields() {
        return List.of(
                CollectField.text("certName", "证书名称", "如：PMP / 软考高项 / 雅思", true),
                CollectField.date("examDate", "考试时间", "如：2026-11-30", false),
                CollectField.location("examCity", "考点/报名城市", "如：杭州", false),
                CollectField.hidden("registrationDate", "报名时间"),
                CollectField.hidden("scoreDate", "查分时间"),
                CollectField.text("dailyTime", "每天可投入时间", "如：每天2小时", false)
        );
    }

    @Override
    public List<FocusArea> focusAreas() {
        // 考证目标结构简单，不拆多个关注块；用一个必选项承载全部时间轴任务。
        return List.of(new FocusArea("cert_core", "考证安排",
                "报名、复习、模考、考试、查成绩与领证的整体安排", Audience.BOTH, true, true));
    }

    @Override
    public List<AttributeDescriptor> attributeCatalog() {
        return List.of(
                new AttributeDescriptor(CertAttributes.Info.TYPE, "证书信息：名称、是否报名、考试日期",
                        List.of(new AttributeDescriptor.FieldSpec("name","string",true,"证书名称"),
                                new AttributeDescriptor.FieldSpec("registered","boolean",false,"是否已报名"),
                                new AttributeDescriptor.FieldSpec("examDate","date",false,"考试日期"))),
                new AttributeDescriptor(CertAttributes.ScoreTarget.TYPE, "分数目标（如雅思7分）",
                        List.of(new AttributeDescriptor.FieldSpec("exam","string",false,"考试/科目"),
                                new AttributeDescriptor.FieldSpec("target","number",true,"目标分"))),
                new AttributeDescriptor(MeasureAttribute.TYPE, "可度量项：每日学习时长、模考分数",
                        List.of(new AttributeDescriptor.FieldSpec("name","string",true,"度量项(daily_hours/mock_score等)"),
                                new AttributeDescriptor.FieldSpec("value","number",true,"数值"),
                                new AttributeDescriptor.FieldSpec("unit","string",false,"单位"))),
                new AttributeDescriptor(VenueAttribute.TYPE, "地点：报名城市、考点城市、具体考点",
                        List.of(new AttributeDescriptor.FieldSpec("name","string",true,"地点名称"))),
                new AttributeDescriptor(StatusAttribute.TYPE, "备考阶段/状态",
                        List.of(new AttributeDescriptor.FieldSpec("stage","string",true,"阶段")))
        );
    }

    @Override
    public List<PlannedTask> plannedTasks(java.util.Map<String, String> collected,
                                          List<String> focusAreas, LocalDate today) {
        LocalDate examDate = Task.parseDueDate(collected == null ? null : collected.get("examDate"));
        if (examDate == null) return List.of();
        if (examDate.isBefore(today)) {
            // 考试日期已过：不倒推/不生成已过期节点，只提醒用户确认下一次考期。
            return List.of(new PlannedTask("确认下一次考试时间",
                    "记录的考试日期 " + examDate + " 已过，请核实最近一次可报考期并更新考试时间。",
                    "cert_timeline", "cert_core", today, today, "更新后系统会自动重排后续节点。"));
        }
        LocalDate registrationDate = Task.parseDueDate(collected == null ? null : collected.get("registrationDate"));
        LocalDate scoreDate = Task.parseDueDate(collected == null ? null : collected.get("scoreDate"));
        LocalDate registerDue = registrationDate != null ? registrationDate : examDate.minusDays(75);
        LocalDate scoreDue = scoreDate != null ? scoreDate : examDate.plusDays(45);
        LocalDate certDue = scoreDate != null ? scoreDate.plusDays(75) : examDate.plusDays(120);
        List<PlannedTask> tasks = new java.util.ArrayList<>();
        // 只生成“今天或之后”的节点，避免离考试太近时倒推出一堆已过期的准备任务。
        addFuture(tasks, today, new PlannedTask("关注并完成考试报名",
                "确认报名时间、报名条件、费用与入口，按时完成报名并核对信息。",
                "cert_timeline", "cert_core", registerDue.minusDays(7), registerDue, "报名后关注准考证打印时间。"));
        addFuture(tasks, today, new PlannedTask("确认考试大纲与复习资料",
                "核对考试大纲、教材和题库版本，制定分章节复习计划。",
                "cert_timeline", "cert_core", examDate.minusDays(120), examDate.minusDays(120), "按章节开始第一轮复习。"));
        addFuture(tasks, today, new PlannedTask("完成知识点第一轮复习",
                "完成核心章节学习，整理章节笔记和错题本。",
                "cert_timeline", "cert_core", examDate.minusDays(60), examDate.minusDays(60), "进入刷题和专项强化阶段。"));
        addFuture(tasks, today, new PlannedTask("完成模考与错题复盘",
                "至少完成2-3套模拟题，按考试时间控制节奏，复盘高频错题。",
                "cert_timeline", "cert_core", examDate.minusDays(14), examDate.minusDays(14), "针对薄弱章节做最后强化。"));
        addFuture(tasks, today, new PlannedTask("准备证件、考点路线与考试用品",
                "确认准考证、身份证件、计算器/文具等允许携带物品，提前规划路线。",
                "cert_timeline", "cert_core", examDate.minusDays(7), examDate.minusDays(7), "考前调整作息，轻量复习。"));
        addFuture(tasks, today, new PlannedTask("参加证书考试",
                "提前到达考点，仔细审题，按答题节奏完成考试。",
                "cert_timeline", "cert_core", examDate.minusDays(1), examDate, "考后关注成绩查询时间。"));
        addFuture(tasks, today, new PlannedTask("查询考试成绩",
                "在官方查分入口开放后查询成绩，核对分数与是否通过。",
                "cert_timeline", "cert_core", scoreDue.minusDays(3), scoreDue, "成绩公布后关注证书领取/邮寄安排。"));
        addFuture(tasks, today, new PlannedTask("领取或办理证书",
                "按官方通知完成证书领取、邮寄或电子证书下载，留存合格证明。",
                "cert_timeline", "cert_core", certDue, certDue, "确认是否需要继续教育或定期注册。"));
        return List.copyOf(tasks);
    }

    /** 提醒日/执行日都已早于今天的节点不再生成，避免产生过期待办。 */
    private void addFuture(List<PlannedTask> tasks, LocalDate today, PlannedTask t) {
        LocalDate anchor = t.dueDate() != null ? t.dueDate() : t.remindDate();
        if (anchor == null || !anchor.isBefore(today)) tasks.add(t);
    }

    @Override
    public List<Class<? extends Attribute>> attributeClasses() {
        return List.of(CertAttributes.Info.class, CertAttributes.ScoreTarget.class);
    }

    @Override
    public List<Attribute> attributesFromCollected(java.util.Map<String, String> collected) {
        List<Attribute> attrs = new java.util.ArrayList<>();
        if (collected == null) return attrs;
        CertAttributes.Info info = new CertAttributes.Info();
        boolean hasInfo = false;
        String name = collected.get("certName");
        if (name != null && !name.isBlank()) { info.setName(name); hasInfo = true; }
        String examDate = collected.get("examDate");
        if (examDate != null && !examDate.isBlank()) { info.setExamDate(examDate); hasInfo = true; }
        if (hasInfo) attrs.add(info);
        String examCity = collected.get("examCity");
        if (examCity != null && !examCity.isBlank()) attrs.add(new VenueAttribute(examCity));
        String dailyTime = collected.get("dailyTime");
        if (dailyTime != null && !dailyTime.isBlank()) attrs.add(new MeasureAttribute("daily_study_time", dailyTime, ""));
        return attrs;
    }

    @Override
    public List<String> initialTasks(String goal) {
        return List.of("确认报名时间与考试大纲", "制定分章节复习计划", "安排每日刷题与每周模考");
    }

    @Override
    public ScenarioDomain.Situation situation(java.util.Map<String, String> collected,
                                              List<String> focusAreas, LocalDate today) {
        LocalDate examDate = Task.parseDueDate(collected == null ? null : collected.get("examDate"));
        if (examDate == null) return new ScenarioDomain.Situation("", List.of());
        long days = java.time.temporal.ChronoUnit.DAYS.between(today, examDate);
        String phase = days > 120 ? "报名与资料准备阶段" : days > 60 ? "基础复习阶段"
                : days > 14 ? "刷题模考阶段" : days > 7 ? "考前强化阶段" : "考前准备阶段";
        String summary = "【备考处境（按今天 " + today + " 计算）】\n"
                + "- 距考试还有 " + days + " 天（考试日 " + examDate + "）\n"
                + "- 当前应处于：" + phase + "。涉及阶段判断/剩余天数一律以此为准。\n";
        java.util.List<String> alerts = new java.util.ArrayList<>();
        if (days < 0) alerts.add("考试日期已过 " + (-days) + " 天，请确认是否已考完或更新考试时间。");
        else if (days <= 7) alerts.add("距考试仅剩 " + days + " 天，确认证件、考点路线与允许携带物品。");
        return new ScenarioDomain.Situation(summary, alerts);
    }

    @Override
    public boolean researchBeforeCreate() {
        return true;
    }

    @Override
    public String researchBrief() {
        return """
                用户想考的证书可能名称相近但发证机构、报考方式差异很大，请联网核实后再让用户确认，不要凭记忆编造：
                1. 确认证书全称、类型与发证/备案机构（如中国营养学会公共营养师、人社备案第三方评价的婴幼儿辅食师、协会培训证等），区分职业技能等级证与培训合格证；
                2. 报考条件（学历/专业/工作年限/是否需培训学时）、是否必须通过授权机构报名、能否个人报名；
                3. 最近的报名时间窗口与考试时间（公共营养师有固定统考批次，部分证书为评价机构滚动开考），日期以官方/授权机构最新通知为准；
                4. 考试内容与题型（理论+技能、机考/纸笔等）、官方指定教材与备考资料名称；
                5. 官方报名入口、成绩查询入口；查分、领证时间给出来源或标注“以官方通知为准”。
                核实不到的项明确标注“待确认”，不要臆造日期或入口链接。""";
    }

    @Override
    public String researchOutputHint() {
        return """
                collected 只能使用该场景收集字段的 key。日期必须是精确到日的 yyyy-MM-dd 格式；
                examDate 必须填“今天之后最近的一次可报考期”的开考日期（若官方只公布到月份，取该月常见考试日并在 rows 标注待确认，不要留空）；
                查不到的报名/查分日期留空并在 rows 里标注待确认，不要把报名/查分日期填到 examDate。
                若最近一次可报的考试距今已不足60天（按今天计算），必须在 sections 中用醒目一行提示“本次考期较紧，是否赶这次？若时间不够可改报下个考期”，并同时给出下个考期（若查得到）；不要默默跳过近期考试。
                materials 列出官方教材、大纲、报名/查分入口等可直接访问的资料链接（来自联网检索结果的真实 url，不要编造）：每项 {"title":"...","url":"..."}；没有可靠链接就给空数组。""";
    }
}
