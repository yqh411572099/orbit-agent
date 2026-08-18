package com.butler.domain.model;

/**
 * 记忆分类。
 * <ul>
 *   <li>{@link #CONTEXT} 上下文：短期情景信息，可能随时间变化（如“最近3个月无法学习”）。</li>
 *   <li>{@link #FACT} 绝对事实：稳定不变的事实（如“孕妇33周岁”“已做过无创DNA”）。</li>
 *   <li>{@link #USER_INFO} 用户信息：用户基础档案（年龄、孕周/预产期、健康基线等）。</li>
 *   <li>{@link #PREFERENCE} 用户偏好：喜好、沟通风格、倾向性选择（如“不喜欢喝牛奶”“偏好文字简短回复”）。</li>
 * </ul>
 */
public enum MemoryCategory {
    CONTEXT("上下文"),
    FACT("绝对事实"),
    USER_INFO("用户信息"),
    PREFERENCE("用户偏好");

    private final String label;

    MemoryCategory(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public static MemoryCategory safeValueOf(String raw) {
        if (raw == null) return CONTEXT;
        try {
            return MemoryCategory.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return CONTEXT;
        }
    }
}
