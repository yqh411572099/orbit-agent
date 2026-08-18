package com.butler.domain.scenario.builtin.attribute;

import com.butler.domain.attribute.Attribute;

/** 考研/学习类目标的结构化属性。 */
public final class ExamAttributes {
    private ExamAttributes() {}

    /** 报考目标：院校、专业、考试日期。 */
    public static class Target extends Attribute {
        public static final String TYPE = "exam.target";
        private String school;
        private String major;
        private String examDate;
        @Override public String getType() { return TYPE; }
        public String getSchool() { return school; }
        public void setSchool(String school) { this.school = school; }
        public String getMajor() { return major; }
        public void setMajor(String major) { this.major = major; }
        public String getExamDate() { return examDate; }
        public void setExamDate(String examDate) { this.examDate = examDate; }
    }

    /** 科目及当前基础。 */
    public static class Subject extends Attribute {
        public static final String TYPE = "exam.subject";
        private String name;
        private String level;
        @Override public String getType() { return TYPE; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getLevel() { return level; }
        public void setLevel(String level) { this.level = level; }
    }
}
