package com.butler.application.tool;

import com.butler.application.KnowledgeAppService;
import com.butler.domain.agent.AgentTool;
import com.butler.domain.agent.ToolContext;
import com.butler.domain.model.KnowledgeEntry;
import com.butler.domain.service.WebSearchPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 联网检索工具：通过结构化搜索端口（豆包搜索 Custom 版）获取网页结果，
 * 返回标题、摘要、链接，供主模型综合作答。搜索源可替换（见 WebSearchPort）。
 *
 * <p>检索到的高价值结果会以 PENDING 知识候选落库（按 url 去重），
 * 用户可在界面或对话中确认保存，确认后纳入知识库检索。</p>
 */
@Component
public class WebSearchTool implements AgentTool {

    private static final int COUNT = 8;
    private final WebSearchPort searchPort;
    private final KnowledgeAppService knowledgeAppService;
    private final ObjectMapper mapper = new ObjectMapper();

    public WebSearchTool(WebSearchPort searchPort, KnowledgeAppService knowledgeAppService) {
        this.searchPort = searchPort;
        this.knowledgeAppService = knowledgeAppService;
    }

    @Override public String name() { return "web_search"; }

    @Override
    public String description() {
        return "联网搜索最新信息（政策、补贴、办事流程、新闻、机构动态等），返回带来源链接的结构化结果。"
                + "适用于需要实时/开放网络信息的问题；地址、周边地点等现实世界位置问题请用 GeoService。";
    }

    @Override
    public String parametersSchema() {
        ObjectNode root = mapper.createObjectNode();
        root.put("type", "object");
        ObjectNode props = root.putObject("properties");
        props.putObject("query").put("type", "string")
                .put("description", "搜索关键词或问题，尽量具体，带上城市/地区等限定词；不支持多词搜索。");
        root.putArray("required").add("query");
        return root.toString();
    }

    @Override
    public String execute(String argumentsJson, ToolContext context) {
        try {
            JsonNode args = mapper.readTree(argumentsJson == null ? "{}" : argumentsJson);
            String query = args.path("query").asText("").trim();
            if (query.isBlank()) return "请给出要搜索的关键词或问题。";
            if (query.length() > 100) query = query.substring(0, 100);

            List<WebSearchPort.WebResult> results = searchPort.search(query, COUNT);
            if (results.isEmpty()) {
                return "未检索到相关结果，可能是搜索服务未配置或暂无内容；建议换个关键词或到官方渠道核实。";
            }
            StringBuilder sb = new StringBuilder("联网检索到以下结果（请据此用中文回答，并在关键结论处标注来源序号）：\n");
            int i = 1;
            for (WebSearchPort.WebResult r : results) {
                sb.append(i++).append(". ").append(safe(r.title()));
                if (r.publishTime() != null && !r.publishTime().isBlank()) sb.append("（").append(r.publishTime()).append("）");
                sb.append("\n");
                String body = r.summary() != null && !r.summary().isBlank() ? r.summary() : r.snippet();
                if (body != null && !body.isBlank()) sb.append("   摘要：").append(body).append("\n");
                if (r.url() != null && !r.url().isBlank()) sb.append("   链接：").append(r.url()).append("\n");
                if (r.siteName() != null && !r.siteName().isBlank()) sb.append("   来源：").append(r.siteName()).append("\n");
            }

            // 沉淀为待确认候选（按 url 去重）。仅在用户上下文存在时执行。
            if (context.userId() != null) {
            if (context.researchOnly()) {
                return sb.toString().trim();
            }
            List<KnowledgeEntry> candidates =
                    knowledgeAppService.proposeFromWeb(context.userId(), context.subSessionId(), query, results);
                if (!candidates.isEmpty()) {
                    sb.append("\n【可沉淀知识】以下结果已存为待确认候选，回答后请询问用户是否保存到知识库：\n");
                    for (KnowledgeEntry e : candidates) {
                        sb.append("- id=").append(e.getId()).append("：").append(safe(e.getTitle()))
                                .append("（").append(safe(e.getSourceUrl())).append("）\n");
                    }
                    sb.append("用户确认保存时调用 KnowledgeBase 的 knowledge_manage(action=confirm, id=...)；忽略则 reject。");
                }
            }
            return sb.toString();
        } catch (Exception e) {
            return "联网检索失败：" + e.getMessage();
        }
    }

    private String safe(String s) { return s == null ? "" : s; }
}
