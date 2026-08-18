package com.butler.domain.scenario.builtin.attribute;

import com.butler.domain.attribute.Attribute;

/** 证书备考类目标的结构化属性。 */
public final class CertAttributes {
    private CertAttributes() {}

    /** 证书信息：名称、是否已报名、考试日期。 */
    public static class Info extends Attribute {
        public static final String TYPE = "cert.info";
        private String name;
        private Boolean registered;
        private String examDate;
        @Override public String getType() { return TYPE; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public Boolean getRegistered() { return registered; }
        public void setRegistered(Boolean registered) { this.registered = registered; }
        public String getExamDate() { return examDate; }
        public void setExamDate(String examDate) { this.examDate = examDate; }
    }

    /** 分数目标：考试名、目标分。 */
    public static class ScoreTarget extends Attribute {
        public static final String TYPE = "cert.score_target";
        private String exam;
        private Double target;
        @Override public String getType() { return TYPE; }
        public String getExam() { return exam; }
        public void setExam(String exam) { this.exam = exam; }
        public Double getTarget() { return target; }
        public void setTarget(Double target) { this.target = target; }
    }
}
