package com.butler.domain.model;

/**
 * 外部计费工具开关（按会话配置，默认开启）。
 *
 * <p>WebSearch（联网）与 GeoSearch（地图/POI）是按量单独计费的外部服务；本开关控制这两个工具是否对模型可用。
 * <ul>
 *   <li>{@link #ENABLED}：开启（默认）——WebSearch、GeoSearch 可用；模型是否、何时调用由提示词自主判断。</li>
 *   <li>{@link #DISABLED}：关闭——这两个计费工具不注册给模型（未续费/不想用外部服务时选用）；
 *       Calculator、KnowledgeBase 等本地/不计费能力照常可用。</li>
 * </ul>
 * 该开关只能由用户通过界面按钮配置，对话内容/模型无权修改。
 */
public enum InfoSourceMode {
    ENABLED("外部工具开启", "联网搜索、地图检索可用（这两项按量计费），由模型按需自主调用"),
    DISABLED("外部工具关闭", "不使用联网搜索、地图检索（不计费）；仅用模型自身知识、本地知识库与本地计算");

    private final String label;
    private final String desc;

    InfoSourceMode(String label, String desc) {
        this.label = label;
        this.desc = desc;
    }

    public String getLabel() { return label; }
    public String getDesc() { return desc; }

    /** 是否启用计费外部工具（WebSearch / GeoSearch）。 */
    public boolean externalToolsEnabled() {
        return this == ENABLED;
    }

    public static InfoSourceMode from(String raw) {
        if (raw == null || raw.isBlank()) return ENABLED;
        String v = raw.trim().toUpperCase();
        // 兼容旧三档命名：外部优先/自主判断都视为开启计费工具，仅本地视为关闭。
        return switch (v) {
            case "DISABLED", "LOCAL_ONLY", "OFF", "FALSE" -> DISABLED;
            default -> ENABLED;
        };
    }
}
