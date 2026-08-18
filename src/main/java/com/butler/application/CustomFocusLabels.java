package com.butler.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.butler.domain.model.SubSession;
import java.util.LinkedHashMap;
import java.util.Map;

/** 读写子对话上“自定义关注项 key→中文label”的 JSON 映射。 */
public final class CustomFocusLabels {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<LinkedHashMap<String, String>> TYPE = new TypeReference<>() {};

    private CustomFocusLabels() {}

    public static Map<String, String> read(SubSession sub) {
        String raw = sub == null ? null : sub.getCustomFocusLabels();
        if (raw == null || raw.isBlank()) return new LinkedHashMap<>();
        try {
            return new LinkedHashMap<>(MAPPER.readValue(raw, TYPE));
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    public static void write(SubSession sub, Map<String, String> labels) {
        if (sub == null) return;
        try {
            sub.setCustomFocusLabels(labels == null || labels.isEmpty() ? null
                    : MAPPER.writeValueAsString(new LinkedHashMap<>(labels)));
        } catch (Exception e) {
            sub.setCustomFocusLabels(null);
        }
    }

    /** 合并一条自定义关注项 label，返回更新后的 map。 */
    public static Map<String, String> with(Map<String, String> existing, String key, String label) {
        Map<String, String> map = existing == null ? new LinkedHashMap<>() : new LinkedHashMap<>(existing);
        if (key != null && !key.isBlank() && label != null && !label.isBlank()) {
            map.put(key, label);
        }
        return map;
    }
}
