package com.butler.domain.agent;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 所有工具大类的注册中心。工具是模型对话时的通用外部能力，与场景/主·子对话无关，
 * 统一全量暴露给模型；模型通过“大类 → 子工具”自行选择（大类本身即是渐进式披露，
 * 不会把全部底层工具塞进上下文）。
 */
@Component
public class ToolRegistry {

    private static final Logger log = LoggerFactory.getLogger(ToolRegistry.class);

    private final Map<String, ToolCategory> categories = new LinkedHashMap<>();
    private final List<ToolCategory> allCategories;

    public ToolRegistry(List<ToolCategory> all) {
        Set<String> seenChildren = new HashSet<>();
        for (ToolCategory c : all) {
            if (c == null || c.name() == null || c.name().isBlank()) {
                throw new IllegalStateException("注册了非法工具大类（name 为空）: " + c);
            }
            if (categories.containsKey(c.name())) {
                throw new IllegalStateException("工具大类名重复: " + c.name());
            }
            for (AgentTool child : c.children()) {
                if (child.name() == null || child.name().isBlank()) {
                    throw new IllegalStateException("大类 " + c.name() + " 下存在 name 为空的子工具");
                }
                if (!seenChildren.add(c.name() + "/" + child.name())) {
                    log.warn("子工具名在多个大类下重复注册: {}/{}", c.name(), child.name());
                }
            }
            categories.put(c.name(), c);
        }
        this.allCategories = List.copyOf(all);
        log.info("工具注册完成: {} 个大类, {} 个子工具", categories.size(), seenChildren.size());
    }

    /** 全部工具大类：任何会话都可由模型按需调用。 */
    public List<ToolCategory> all() {
        return allCategories;
    }

    /**
     * 工具契约校验：每个大类/子工具的 name、description 非空，参数 schema 为合法 JSON。
     * 不在应用启动时运行；由单元测试在改动工具时显式调用（改哪个工具跑哪个测试）。
     */
    public void selfCheck() {
        for (ToolCategory c : allCategories) {
            requireText(c.name(), "工具大类 name");
            requireText(c.description(), "工具大类 " + c.name() + " description");
            requireValidJson(c.parametersSchema(), "工具大类 " + c.name() + " parametersSchema");
            for (AgentTool child : c.children()) {
                requireText(child.name(), c.name() + " 子工具 name");
                requireText(child.description(), c.name() + "/" + child.name() + " description");
                requireValidJson(child.parametersSchema(), c.name() + "/" + child.name() + " parametersSchema");
            }
        }
    }

    private static void requireText(String v, String what) {
        if (v == null || v.isBlank()) throw new IllegalStateException(what + " 为空");
    }

    private static void requireValidJson(String schema, String what) {
        if (schema == null || schema.isBlank()) throw new IllegalStateException(what + " 为空");
        try {
            new com.fasterxml.jackson.databind.ObjectMapper().readTree(schema);
        } catch (Exception e) {
            throw new IllegalStateException(what + " 不是合法 JSON: " + e.getMessage());
        }
    }

    public ToolCategory get(String name) { return categories.get(name); }
}
