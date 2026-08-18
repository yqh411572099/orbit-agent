package com.butler.domain.agent;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** 所有工具大类的注册中心，按场景声明的名字过滤。 */
@Component
public class ToolRegistry {

    private final Map<String, ToolCategory> categories = new LinkedHashMap<>();

    public ToolRegistry(List<ToolCategory> all) {
        for (ToolCategory c : all) categories.put(c.name(), c);
    }

    public List<ToolCategory> forScenario(List<String> names) {
        if (names == null || names.isEmpty()) return List.of();
        return names.stream().map(categories::get).filter(java.util.Objects::nonNull).toList();
    }

    public ToolCategory get(String name) { return categories.get(name); }
}
