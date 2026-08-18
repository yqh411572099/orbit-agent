package com.butler.application;

import com.butler.domain.scenario.ScenarioDomain;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

public final class ScenarioStateSupport {
    private ScenarioStateSupport() {}

    public record ScenarioState(Map<String, String> collected, List<String> focusAreas) {}

    public static ScenarioState parse(ScenarioDomain domain, String collectedInfo) {
        return parse(domain, collectedInfo, Map.of());
    }

    /**
     * @param customLabels 自定义关注项的 key→中文label 映射（由对话动态创建、不在内置 focusAreas 里的项）。
     */
    public static ScenarioState parse(ScenarioDomain domain, String collectedInfo,
                                      Map<String, String> customLabels) {
        Map<String, String> byLabel = new LinkedHashMap<>();
        Map<String, String> labelToKey = new LinkedHashMap<>();
        Map<String, String> focusLabelToKey = new LinkedHashMap<>();
        for (ScenarioDomain.CollectField field : domain.collectFields()) {
            labelToKey.put(field.label(), field.key());
        }
        for (ScenarioDomain.FocusArea focusArea : domain.focusAreas()) {
            focusLabelToKey.put(focusArea.label(), focusArea.key());
        }
        // 自定义关注项：label→key 反查
        if (customLabels != null) {
            customLabels.forEach((k, v) -> { if (v != null && !v.isBlank()) focusLabelToKey.put(v, k); });
        }

        Map<String, String> collected = new LinkedHashMap<>();
        List<String> focusAreas = new ArrayList<>();
        if (collectedInfo != null && !collectedInfo.isBlank()) {
            for (String line : collectedInfo.split("\\n")) {
                int idx = line.indexOf('：');
                if (idx <= 0) continue;
                String label = line.substring(0, idx).trim();
                String value = line.substring(idx + 1).trim();
                if (label.equals("重点关注项")) {
                    for (String item : value.split("、")) {
                        String name = item.trim();
                        if (name.isEmpty()) continue;
                        String key = focusLabelToKey.get(name);
                        if (key != null) {
                            focusAreas.add(key);
                        } else {
                            // 未知项：可能是旧数据残留的中文 label 或裸 key，原样保留以便后续映射
                            focusAreas.add(name);
                        }
                    }
                } else {
                    String key = labelToKey.get(label);
                    if (key != null) collected.put(key, value);
                }
            }
        }
        return new ScenarioState(collected, focusAreas);
    }

    public static String render(ScenarioDomain domain, Map<String, String> collected, List<String> focusAreas) {
        return render(domain, collected, focusAreas, Map.of());
    }

    /**
     * @param customLabels 自定义关注项的 key→中文label 映射。
     */
    public static String render(ScenarioDomain domain, Map<String, String> collected,
                                List<String> focusAreas, Map<String, String> customLabels) {
        Map<String, String> keyToLabel = new LinkedHashMap<>();
        for (ScenarioDomain.CollectField field : domain.collectFields()) {
            keyToLabel.put(field.key(), field.label());
        }
        Map<String, String> focusKeyToLabel = new LinkedHashMap<>();
        for (ScenarioDomain.FocusArea focusArea : domain.focusAreas()) {
            focusKeyToLabel.put(focusArea.key(), focusArea.label());
        }
        if (customLabels != null) focusKeyToLabel.putAll(customLabels);

        StringBuilder sb = new StringBuilder();
        if (collected != null) {
            for (ScenarioDomain.CollectField field : domain.collectFields()) {
                String value = collected.get(field.key());
                if (value != null && !value.isBlank()) {
                    sb.append(field.label()).append('：').append(value.trim()).append('\n');
                }
            }
        }
        if (focusAreas != null && !focusAreas.isEmpty()) {
            List<String> labels = new ArrayList<>();
            for (String key : new LinkedHashSet<>(focusAreas)) {
                if (key == null || key.isBlank()) continue;
                String label = focusKeyToLabel.get(key);
                labels.add(label != null ? label : key);
            }
            if (!labels.isEmpty()) {
                sb.append("重点关注项：").append(String.join("、", labels)).append('\n');
            }
        }
        return sb.toString().trim();
    }

    /** 面向用户/对话上下文渲染：跳过 HIDDEN 技术字段（如定位经纬度）。 */
    public static String renderVisible(ScenarioDomain domain, Map<String, String> collected,
                                       List<String> focusAreas) {
        return renderVisible(domain, collected, focusAreas, Map.of());
    }

    public static String renderVisible(ScenarioDomain domain, Map<String, String> collected,
                                       List<String> focusAreas, Map<String, String> customLabels) {
        Map<String, String> visible = new LinkedHashMap<>();
        if (collected != null) {
            java.util.Set<String> hidden = domain.collectFields().stream()
                    .filter(f -> f.type() == ScenarioDomain.FieldType.HIDDEN)
                    .map(ScenarioDomain.CollectField::key)
                    .collect(java.util.stream.Collectors.toSet());
            collected.forEach((k, v) -> { if (!hidden.contains(k)) visible.put(k, v); });
        }
        return render(domain, visible, focusAreas, customLabels);
    }
}
