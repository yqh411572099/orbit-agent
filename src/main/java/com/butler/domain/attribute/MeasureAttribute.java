package com.butler.domain.attribute;

/** 可度量数值，如孕周、体重、每日学习时长、模考分数。 */
public class MeasureAttribute extends Attribute {
    public static final String TYPE = "measure";
    private String name;
    private Object value;
    private String unit;

    public MeasureAttribute() {}
    public MeasureAttribute(String name, Object value, String unit) {
        this.name = name; this.value = value; this.unit = unit;
    }
    @Override public String getType() { return TYPE; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Object getValue() { return value; }
    public void setValue(Object value) { this.value = value; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
}
