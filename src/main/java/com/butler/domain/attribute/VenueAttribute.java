package com.butler.domain.attribute;

/** 地点/机构，如产检医院、考点、目标院校所在城市。 */
public class VenueAttribute extends Attribute {
    public static final String TYPE = "venue";
    private String name;

    public VenueAttribute() {}
    public VenueAttribute(String name) { this.name = name; }
    @Override public String getType() { return TYPE; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
