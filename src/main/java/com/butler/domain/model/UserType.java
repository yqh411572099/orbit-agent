package com.butler.domain.model;

/** 用户类型：后续可扩展（如 VIP/高级用户），影响功能权限与数据删除策略。 */
public enum UserType {
    NORMAL("普通用户"),
    TEST("测试用户"),
    PREMIUM("高级用户");

    private final String label;
    UserType(String label) { this.label = label; }
    public String getLabel() { return label; }
}
