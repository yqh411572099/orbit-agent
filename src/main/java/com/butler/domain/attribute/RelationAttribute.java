package com.butler.domain.attribute;

/** 人物关系/动作，如“准爸爸 陪同 孕妇 产检”。 */
public class RelationAttribute extends Attribute {
    public static final String TYPE = "relation";
    private String party;
    private String action;

    public RelationAttribute() {}
    public RelationAttribute(String party, String action) { this.party = party; this.action = action; }
    @Override public String getType() { return TYPE; }
    public String getParty() { return party; }
    public void setParty(String party) { this.party = party; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
}
