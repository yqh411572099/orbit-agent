package com.butler.application.tool;

import com.butler.application.KnowledgeAppService;
import com.butler.domain.agent.AgentTool;
import com.butler.domain.agent.ToolContext;
import com.butler.domain.model.KnowledgeEntry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 知识沉淀管理：用户在对话中说“保存/采纳这条”或“忽略”时，由模型调用，
 * 把联网检索产生的 PENDING 候选流转为 CONFIRMED/REJECTED。也可列出待确认项。
 */
@Component
public class KnowledgeManageTool implements AgentTool {

    private final KnowledgeAppService knowledgeAppService;
    private final ObjectMapper mapper = new ObjectMapper();

    public KnowledgeManageTool(KnowledgeAppService knowledgeAppService) {
        this.knowledgeAppService = knowledgeAppService;
    }

    @Override public String name() { return "knowledge_manage"; }

    @Override public String description() {
        return "管理待确认的知识候选：确认保存(confirm)、忽略(reject)、列出待确认项(list)。"
                + "当用户说“把这条存下来/采纳”“这个不用记”等时调用。";
    }

    @Override
    public String parametersSchema() {
        ObjectNode root = mapper.createObjectNode();
        root.put("type", "object");
        ObjectNode props = root.putObject("properties");
        props.putObject("action").put("type", "string")
                .put("description", "confirm=确认保存为知识库条目；reject=忽略；list=列出待确认项");
        props.putObject("id").put("type", "integer").put("description", "候选知识 id（confirm/reject 必填）");
        root.putArray("required").add("action");
        return root.toString();
    }

    @Override
    public String execute(String argumentsJson, ToolContext context) {
        try {
            JsonNode args = mapper.readTree(argumentsJson == null ? "{}" : argumentsJson);
            String action = args.path("action").asText("").trim().toLowerCase();
            return switch (action) {
                case "confirm" -> confirm(args, context);
                case "reject" -> reject(args, context);
                case "list" -> list(context);
                default -> "请指定 action：confirm / reject / list。";
            };
        } catch (Exception e) {
            return "知识管理操作失败：" + e.getMessage();
        }
    }

    private String confirm(JsonNode args, ToolContext context) {
        Long id = args.path("id").isNumber() ? args.path("id").asLong() : null;
        if (id == null) return "请提供要确认的知识条目 id。";
        KnowledgeEntry e = knowledgeAppService.confirm(id);
        return "已保存到知识库：" + e.getTitle() + (e.getSourceUrl() == null ? "" : "（" + e.getSourceUrl() + "）");
    }

    private String reject(JsonNode args, ToolContext context) {
        Long id = args.path("id").isNumber() ? args.path("id").asLong() : null;
        if (id == null) return "请提供要忽略的知识条目 id。";
        KnowledgeEntry e = knowledgeAppService.reject(id);
        return "已忽略该条知识：" + e.getTitle();
    }

    private String list(ToolContext context) {
        List<KnowledgeEntry> pending = knowledgeAppService.listPending(context.userId(), context.subSessionId());
        if (pending.isEmpty()) return "当前没有待确认的知识候选。";
        StringBuilder sb = new StringBuilder("待确认的知识候选：\n");
        for (KnowledgeEntry e : pending) {
            sb.append("- id=").append(e.getId()).append("：").append(e.getTitle() == null ? "" : e.getTitle());
            if (e.getSourceUrl() != null) sb.append("（").append(e.getSourceUrl()).append("）");
            sb.append("\n");
        }
        sb.append("用户确认保存就调用 confirm，忽略就调用 reject。");
        return sb.toString().trim();
    }
}
