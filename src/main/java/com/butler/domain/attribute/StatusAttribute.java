package com.butler.domain.attribute;

/** 阶段/状态，如孕早期、备考基础阶段、暂停。 */
public class StatusAttribute extends Attribute {
    public static final String TYPE = "status";
    private String stage;

    public StatusAttribute() {}
    public StatusAttribute(String stage) { this.stage = stage; }
    @Override public String getType() { return TYPE; }
    public String getStage() { return stage; }
    public void setStage(String stage) { this.stage = stage; }
}
