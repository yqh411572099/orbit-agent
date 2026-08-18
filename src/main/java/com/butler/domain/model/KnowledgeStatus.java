package com.butler.domain.model;

/** 知识条目的沉淀状态。 */
public enum KnowledgeStatus {
    PENDING("待确认"),
    CONFIRMED("已确认"),
    REJECTED("已忽略");

    private final String label;
    KnowledgeStatus(String label) { this.label = label; }
    public String getLabel() { return label; }
}
