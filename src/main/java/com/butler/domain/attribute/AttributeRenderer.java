package com.butler.domain.attribute;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 把结构化 {@link Attribute} 渲染为可读文本 / 通用 Map，供上下文拼装与接口返回使用。
 *
 * <p>不依赖具体子类：通过 Jackson 把多态属性转成 Map（含 type 与子类声明字段、extras 透传字段），
 * 因此后续新增域属性子类无需改动此处。</p>
 */
public final class AttributeRenderer {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private AttributeRenderer() {}

    /** 渲染单条属性为紧凑文本，如 measure{name=孕周, value=7, unit=周}。 */
    public static String toText(Attribute attr) {
        if (attr == null) return "";
        Map<String, Object> map = toMap(attr);
        String type = String.valueOf(map.getOrDefault("type", attr.getType()));
        Map<String, Object> copy = new LinkedHashMap<>(map);
        copy.remove("type");
        if (copy.isEmpty()) return type;
        StringBuilder sb = new StringBuilder(type).append("{");
        boolean first = true;
        for (Map.Entry<String, Object> e : copy.entrySet()) {
            if (e.getValue() == null) continue;
            if (!first) sb.append(", ");
            sb.append(e.getKey()).append("=").append(e.getValue());
            first = false;
        }
        return sb.append("}").toString();
    }

    /** 渲染属性集合，多个以空格分隔。 */
    public static String toText(List<Attribute> attrs) {
        if (attrs == null || attrs.isEmpty()) return "";
        List<String> parts = new ArrayList<>();
        for (Attribute a : attrs) {
            String t = toText(a);
            if (!t.isBlank()) parts.add(t);
        }
        return String.join(" ", parts);
    }

    /** 转为通用 Map（含 type 与所有声明/透传字段），用于 JSON 序列化返回前端。 */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> toMap(Attribute attr) {
        if (attr == null) return Map.of();
        return MAPPER.convertValue(attr, Map.class);
    }

    public static List<Map<String, Object>> toMapList(List<Attribute> attrs) {
        if (attrs == null || attrs.isEmpty()) return List.of();
        List<Map<String, Object>> list = new ArrayList<>();
        for (Attribute a : attrs) list.add(toMap(a));
        return list;
    }
}
