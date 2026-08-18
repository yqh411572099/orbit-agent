package com.butler.domain.model;

/** 知识条目的来源。 */
public enum KnowledgeSource {
    BUILT_IN("内置"),
    WEB_SEARCH("联网检索"),
    USER_DOC("用户文档"),
    MANUAL("手动录入");

    private final String label;
    KnowledgeSource(String label) { this.label = label; }
    public String getLabel() { return label; }
}
