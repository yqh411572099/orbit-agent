package com.butler.domain.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工具大类：第一层只暴露给模型一个大类，大类内部再按需要选择/分发到子工具。
 * 避免一次性把全部工具塞进上下文。
 */
public class ToolCategory implements AgentTool {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String name;
    private final String description;
    private final Map<String, AgentTool> children = new LinkedHashMap<>();

    public ToolCategory(String name, String description, List<AgentTool> children) {
        this.name = name;
        this.description = description;
        for (AgentTool c : children) this.children.put(c.name(), c);
    }

    @Override
    public String name() { return name; }

    @Override
    public String description() {
        StringBuilder sb = new StringBuilder(description);
        if (!children.isEmpty()) {
            sb.append("。包含：");
            boolean first = true;
            for (AgentTool c : children.values()) {
                if (!first) sb.append("；");
                sb.append(c.name()).append("（").append(c.description()).append("）");
                first = false;
            }
            if (children.size() > 1) {
                sb.append("。如不确定用哪个子工具，sub_tool 可留空。");
            }
        }
        return sb.toString();
    }

    @Override
    public String parametersSchema() {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("type", "object");
        ObjectNode props = root.putObject("properties");
        // 合并所有子工具声明的参数（去重），让模型能直接传子工具专属字段（如 location/address/cityHint）
        java.util.HashSet<String> added = new java.util.HashSet<>();
        for (AgentTool c : children.values()) {
            try {
                JsonNode child = MAPPER.readTree(c.parametersSchema());
                JsonNode cp = child.path("properties");
                if (cp.isObject()) {
                    cp.fields().forEachRemaining(e -> {
                        if (added.add(e.getKey())) props.set(e.getKey(), e.getValue());
                    });
                }
            } catch (Exception ignored) {
                // 某个子工具 schema 解析失败不影响整体
            }
        }
        // query 始终可用作自然语言兜底
        if (!added.contains("query")) {
            props.putObject("query").put("type", "string").put("description", "要查什么，用自然语言描述。");
        }
        if (children.size() > 1) {
            ObjectNode sub = props.putObject("sub_tool");
            sub.put("type", "string");
            ArrayNode en = sub.putArray("enum");
            children.keySet().forEach(en::add);
            sub.put("description", "要使用的具体子工具；不确定可省略。");
        }
        ArrayNode required = root.putArray("required");
        // 子工具必填字段取并集过于严格，这里不强制（由子工具自行校验）；query 非强制，因为子工具可能用 address/location
        return root.toString();
    }

    @Override
    public String execute(String argumentsJson, ToolContext context) throws Exception {
        JsonNode args = MAPPER.readTree(argumentsJson == null ? "{}" : argumentsJson);
        String query = args.path("query").asText("");
        AgentTool target = null;
        if (args.hasNonNull("sub_tool")) {
            target = children.get(args.get("sub_tool").asText());
        }
        if (target == null) {
            target = route(query);
        }
        if (target == null) {
            return "未找到合适的子工具，请明确要查的内容。";
        }
        // 把大类收到的 query 透传给子工具（子工具自行决定还需要哪些字段）。
        ObjectNode childArgs = MAPPER.createObjectNode();
        childArgs.put("query", query);
        args.fields().forEachRemaining(e -> {
            if (!"sub_tool".equals(e.getKey())) childArgs.set(e.getKey(), e.getValue());
        });
        return target.execute(childArgs.toString(), context);
    }

    protected AgentTool route(String query) {
        return children.isEmpty() ? null : children.values().iterator().next();
    }

    public List<AgentTool> children() { return List.copyOf(children.values()); }
}
