package com.butler.domain.scenario.builtin;

import com.butler.domain.attribute.Attribute;
import com.butler.domain.attribute.AttributeDescriptor;
import com.butler.domain.attribute.MeasureAttribute;
import com.butler.domain.attribute.StatusAttribute;
import com.butler.domain.attribute.VenueAttribute;
import com.butler.domain.model.Task;
import com.butler.domain.scenario.ScenarioDomain;
import com.butler.domain.scenario.builtin.attribute.ExamAttributes;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Component;

/** 考研备考场景域。 */
@Component
public class ExamPreparationScenario implements ScenarioDomain {

    @Override
    public String type() { return "exam_prep"; }

    @Override
    public String displayName() { return "考研备考"; }

    @Override
    public String description() { return "制定考研复习计划、跟踪各科进度、生成阶段任务与冲刺安排。"; }

    @Override
    public String defaultSessionDesc() {
        return "用户正在准备研究生入学考试，关注目标院校专业、各科复习计划、进度与考试时间安排。";
    }

    @Override
    public List<CollectField> collectFields() {
        return List.of(
                CollectField.text("targetSchool", "目标院校/专业", "如：清华大学 计算机学硕", false),
                CollectField.date("examDate", "考试时间", "如：2026-12-20", false),
                CollectField.text("subjects", "重点科目", "如：数学、英语、数据结构", false),
                CollectField.text("currentLevel", "当前基础", "如：英语四级水平、数学零基础", false)
        );
    }

    @Override
    public List<AttributeDescriptor> attributeCatalog() {
        return List.of(
                new AttributeDescriptor(ExamAttributes.Target.TYPE, "报考目标：院校、专业、考试日期",
                        List.of(new AttributeDescriptor.FieldSpec("school","string",false,"目标院校"),
                                new AttributeDescriptor.FieldSpec("major","string",false,"目标专业"),
                                new AttributeDescriptor.FieldSpec("examDate","date",false,"考试日期 yyyy-MM-dd"))),
                new AttributeDescriptor(ExamAttributes.Subject.TYPE, "考试科目及当前基础水平",
                        List.of(new AttributeDescriptor.FieldSpec("name","string",true,"科目名称"),
                                new AttributeDescriptor.FieldSpec("level","string",false,"当前基础/水平"))),
                new AttributeDescriptor(MeasureAttribute.TYPE, "可度量项：每日学习时长、模考分数",
                        List.of(new AttributeDescriptor.FieldSpec("name","string",true,"度量项(daily_hours/mock_score等)"),
                                new AttributeDescriptor.FieldSpec("value","number",true,"数值"),
                                new AttributeDescriptor.FieldSpec("unit","string",false,"单位"))),
                new AttributeDescriptor(StatusAttribute.TYPE, "备考阶段/状态（基础/强化/冲刺）",
                        List.of(new AttributeDescriptor.FieldSpec("stage","string",true,"阶段"))),
                new AttributeDescriptor(VenueAttribute.TYPE, "地点：考点、目标院校所在城市",
                        List.of(new AttributeDescriptor.FieldSpec("name","string",true,"地点名称")))
        );
    }

    @Override
    public List<PlannedTask> plannedTasks(java.util.Map<String, String> collected,
                                          List<String> focusAreas, LocalDate today) {
        LocalDate examDate = Task.parseDueDate(collected == null ? null : collected.get("examDate"));
        if (examDate == null) return List.of();
        if (examDate.isBefore(today)) {
            return List.of(new PlannedTask("确认下一次考试时间",
                    "记录的考试日期 " + examDate + " 已过，请核实最新考试时间并更新。",
                    "exam_timeline", null, today, today, "更新后系统会自动重排后续节点。"));
        }
        List<PlannedTask> tasks = new java.util.ArrayList<>();
        addFuture(tasks, today, new PlannedTask("完成考试信息确认与基础摸底",
                "确认目标院校专业、考试科目、报名时间与考点要求，完成一次基础水平摸底。",
                "exam_timeline", null, examDate.minusDays(180), examDate.minusDays(180), "完成后进入基础复习节奏。"));
        addFuture(tasks, today, new PlannedTask("完成第一轮基础复习",
                "按科目完成教材/课程第一轮学习，整理知识框架和薄弱点清单。",
                "exam_timeline", null, examDate.minusDays(90), examDate.minusDays(90), "开始真题训练并强化薄弱项。"));
        addFuture(tasks, today, new PlannedTask("完成真题训练与模考",
                "按考试时间做套卷，训练答题速度，复盘错题和高频考点。",
                "exam_timeline", null, examDate.minusDays(30), examDate.minusDays(30), "进入冲刺阶段，查漏补缺。"));
        addFuture(tasks, today, new PlannedTask("准备准考证、文具与考点路线",
                "提前打印准考证，确认身份证件、文具、考点交通与住宿安排。",
                "exam_timeline", null, examDate.minusDays(7), examDate.minusDays(7), "考前调整作息，保持状态。"));
        addFuture(tasks, today, new PlannedTask("参加研究生考试",
                "带齐证件和文具，提前到达考点，按考试安排完成各科考试。",
                "exam_timeline", null, examDate.minusDays(1), examDate, "考后复盘并准备复试/调剂信息。"));
        return List.copyOf(tasks);
    }

    /** 提醒日/执行日都已早于今天的节点不再生成，避免产生过期待办。 */
    private void addFuture(List<PlannedTask> tasks, LocalDate today, PlannedTask t) {
        LocalDate anchor = t.dueDate() != null ? t.dueDate() : t.remindDate();
        if (anchor == null || !anchor.isBefore(today)) tasks.add(t);
    }

    @Override
    public List<Class<? extends Attribute>> attributeClasses() {
        return List.of(ExamAttributes.Target.class, ExamAttributes.Subject.class);
    }

    @Override
    public List<Attribute> attributesFromCollected(java.util.Map<String, String> collected) {
        List<Attribute> attrs = new java.util.ArrayList<>();
        if (collected == null) return attrs;
        ExamAttributes.Target target = new ExamAttributes.Target();
        boolean hasTarget = false;
        String school = collected.get("targetSchool");
        if (school != null && !school.isBlank()) { target.setSchool(school); hasTarget = true; }
        String examDate = collected.get("examDate");
        if (examDate != null && !examDate.isBlank()) { target.setExamDate(examDate); hasTarget = true; }
        if (hasTarget) attrs.add(target);
        String subjects = collected.get("subjects");
        if (subjects != null && !subjects.isBlank()) {
            ExamAttributes.Subject subj = new ExamAttributes.Subject();
            subj.setName(subjects);
            subj.setLevel(collected.getOrDefault("currentLevel", ""));
            attrs.add(subj);
        }
        return attrs;
    }

    @Override
    public List<String> initialTasks(String goal) {
        return List.of("明确目标院校与专业及考试科目", "制定各科分阶段复习计划", "安排每日学习时间并开始基础摸底");
    }

    @Override
    public ScenarioDomain.Situation situation(java.util.Map<String, String> collected,
                                              List<String> focusAreas, LocalDate today) {
        LocalDate examDate = Task.parseDueDate(collected == null ? null : collected.get("examDate"));
        if (examDate == null) return new ScenarioDomain.Situation("", List.of());
        long days = java.time.temporal.ChronoUnit.DAYS.between(today, examDate);
        String phase = days > 180 ? "基础摸底阶段" : days > 90 ? "基础复习阶段"
                : days > 30 ? "真题强化阶段" : days > 7 ? "冲刺阶段" : "考前准备阶段";
        String summary = "【备考处境（按今天 " + today + " 计算）】\n"
                + "- 距考试还有 " + days + " 天（考试日 " + examDate + "）\n"
                + "- 当前应处于：" + phase + "。涉及阶段判断/剩余天数一律以此为准，不要沿用旧对话。\n";
        java.util.List<String> alerts = new java.util.ArrayList<>();
        if (days < 0) alerts.add("考试日期已过 " + (-days) + " 天，请确认是否已考完或更新考试时间。");
        else if (days <= 7) alerts.add("距考试仅剩 " + days + " 天，确认准考证、文具、考点路线与住宿。");
        return new ScenarioDomain.Situation(summary, alerts);
    }

}
