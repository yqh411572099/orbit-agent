package com.butler.application.tool;

import com.butler.application.KnowledgeAppService;
import com.butler.domain.agent.AgentTool;
import com.butler.domain.agent.ToolContext;
import com.butler.domain.model.KnowledgeEntry;
import com.butler.domain.model.Task;
import com.butler.domain.repository.SubSessionRepository;
import com.butler.domain.repository.TaskRepository;
import com.butler.domain.scenario.ScenarioDomain;
import com.butler.domain.scenario.ScenarioRegistry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 知识库检索：优先查“用户已确认沉淀的知识”，再回退到场景内置知识（关注项说明、时间轴任务详情）。
 * 命中已确认知识时带来源链接，便于溯源。
 */
@Component
public class KnowledgeSearchTool implements AgentTool {

    private final ScenarioRegistry scenarioRegistry;
    private final TaskRepository taskRepository;
    private final SubSessionRepository subSessionRepository;
    private final KnowledgeAppService knowledgeAppService;
    private final ObjectMapper mapper = new ObjectMapper();

    public KnowledgeSearchTool(ScenarioRegistry scenarioRegistry,
                               TaskRepository taskRepository,
                               SubSessionRepository subSessionRepository,
                               KnowledgeAppService knowledgeAppService) {
        this.scenarioRegistry = scenarioRegistry;
        this.taskRepository = taskRepository;
        this.subSessionRepository = subSessionRepository;
        this.knowledgeAppService = knowledgeAppService;
    }

    @Override public String name() { return "knowledge_search"; }
    @Override public String description() { return "检索已沉淀的知识（用户确认保存的政策/办事指南，以及本目标时间轴/关注项说明），优先于联网使用"; }

    @Override
    public String parametersSchema() {
        ObjectNode root = mapper.createObjectNode();
        root.put("type", "object");
        ObjectNode props = root.putObject("properties");
        props.putObject("query").put("type", "string").put("description", "要查的知识点，如：NT检查注意事项、建档流程、产检补贴");
        root.putArray("required").add("query");
        return root.toString();
    }

    @Override
    public String execute(String argumentsJson, ToolContext context) {
        try {
            JsonNode args = mapper.readTree(argumentsJson == null ? "{}" : argumentsJson);
            String query = args.path("query").asText("").trim();
            if (query.isEmpty()) return "请说明要查的知识点。";
            if (context.userId() == null) return "当前没有用户上下文，无法检索知识库。";

            List<String> hits = new ArrayList<>();

            // 1. 用户已确认沉淀的知识（用户级 + 当前子会话级）
            for (KnowledgeEntry e : knowledgeAppService.searchConfirmed(
                    context.userId(), context.subSessionId(), query, 5)) {
                StringBuilder sb = new StringBuilder("【已沉淀·")
                        .append(e.getSource().getLabel()).append("】")
                        .append(e.getTitle() == null ? "" : e.getTitle());
                if (e.getContent() != null && !e.getContent().isBlank()) sb.append("：").append(e.getContent());
                if (e.getSourceUrl() != null && !e.getSourceUrl().isBlank()) sb.append("（来源：").append(e.getSourceUrl()).append("）");
                hits.add(sb.toString());
            }

            // 2. 场景内置知识（关注项 + 时间轴任务），作为兜底
            if (context.scenarioType() != null && scenarioRegistry.supports(context.scenarioType())) {
                ScenarioDomain domain = scenarioRegistry.get(context.scenarioType());
                String q = query.toLowerCase();
                for (ScenarioDomain.FocusArea f : domain.focusAreas()) {
                    if (contains(q, f.label(), f.description())) {
                        hits.add("【内置·关注项·" + f.label() + "】" + f.description());
                    }
                }
                if (context.subSessionId() != null) {
                    for (Task t : taskRepository.findBySubSessionId(context.subSessionId())) {
                        String blob = (t.getContent() == null ? "" : t.getContent()) + " "
                                + (t.getDetail() == null ? "" : t.getDetail()) + " "
                                + (t.getNextHint() == null ? "" : t.getNextHint());
                        if (contains(q, blob)) {
                            hits.add("【内置·时间轴·" + t.getContent() + "】" + (t.getDetail() == null ? "" : t.getDetail()));
                        }
                    }
                }
            }

            if (hits.isEmpty()) {
                return "知识库中没有直接匹配“" + query + "”的内容，建议改用 WebSearch 联网检索最新信息；检索后你可以确认把可靠结果保存进知识库。";
            }
            StringBuilder sb = new StringBuilder("从知识库检索到：\n");
            hits.stream().limit(8).forEach(h -> sb.append("- ").append(h).append("\n"));
            return sb.toString().trim();
        } catch (Exception e) {
            return "知识库检索失败：" + e.getMessage();
        }
    }

    private boolean contains(String q, String... texts) {
        for (String t : texts) {
            if (t == null) continue;
            String low = t.toLowerCase();
            for (String kw : q.split("\\s+")) {
                if (!kw.isBlank() && low.contains(kw)) return true;
            }
        }
        return false;
    }
}
