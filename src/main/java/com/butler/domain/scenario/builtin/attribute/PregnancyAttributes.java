package com.butler.domain.scenario.builtin.attribute;

import com.butler.domain.attribute.Attribute;

/** 孕期域的结构化属性集合，均以 no-arg + getType 便于多态绑定。 */
public final class PregnancyAttributes {
    private PregnancyAttributes() {}

    /** 孕妇基础档案：预产期、孕周、产检医院等。 */
    public static class Profile extends Attribute {
        public static final String TYPE = "pregnancy.profile";
        private String dueDate;
        private Integer gestationalWeek;
        private String hospital;
        private Integer age;
        @Override public String getType() { return TYPE; }
        public String getDueDate() { return dueDate; }
        public void setDueDate(String dueDate) { this.dueDate = dueDate; }
        public Integer getGestationalWeek() { return gestationalWeek; }
        public void setGestationalWeek(Integer gestationalWeek) { this.gestationalWeek = gestationalWeek; }
        public String getHospital() { return hospital; }
        public void setHospital(String hospital) { this.hospital = hospital; }
        public Integer getAge() { return age; }
        public void setAge(Integer age) { this.age = age; }
    }

    /** 关键产检节点/里程碑（NT、大排畸、糖耐、胎心监护等）。 */
    public static class Checkpoint extends Attribute {
        public static final String TYPE = "pregnancy.checkpoint";
        private String name;
        private String dueDate;
        private String notes;
        @Override public String getType() { return TYPE; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getDueDate() { return dueDate; }
        public void setDueDate(String dueDate) { this.dueDate = dueDate; }
        public String getNotes() { return notes; }
        public void setNotes(String notes) { this.notes = notes; }
    }
}
