package com.butler.infrastructure.search;

import com.butler.domain.service.WebSearchPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;

/**
 * 火山引擎「豆包搜索 Custom 版」实现。
 * 文档：https://docs.volcengine.com/docs/87772/2272953
 * APIKey 接入端点：https://open.feedcoopapi.com/search_api/web_search
 * 使用与方舟模型独立的搜索 API Key（SEARCH_API_KEY）。
 */
public class DoubaoWebSearchAdapter implements WebSearchPort {

    private static final Logger log = LoggerFactory.getLogger(DoubaoWebSearchAdapter.class);
    private static final String ENDPOINT = "https://open.feedcoopapi.com/search_api/web_search";

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(6))
            .followRedirects(HttpClient.Redirect.NORMAL).build();
    private final ObjectMapper mapper;
    private final String apiKey;

    public DoubaoWebSearchAdapter(ObjectMapper mapper, @Value("${search.api-key:}") String apiKey) {
        this.mapper = mapper;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
    }

    @Override
    public List<WebResult> search(String query, int count) {
        if (apiKey.isBlank()) {
            return List.of();
        }
        if (query == null || query.isBlank()) return List.of();
        String q = query.length() > 100 ? query.substring(0, 100) : query;
        try {
            ObjectNode body = mapper.createObjectNode();
            body.put("Query", q);
            body.put("SearchType", "web");
            body.put("Count", Math.max(1, Math.min(count, 50)));
            ObjectNode filter = body.putObject("Filter");
            filter.put("NeedContent", false);

            HttpRequest req = HttpRequest.newBuilder(URI.create(ENDPOINT))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(15))
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() / 100 != 2) {
                log.warn("豆包搜索返回非2xx: {} {}", resp.statusCode(), truncate(resp.body(), 300));
                return List.of();
            }
            JsonNode root = mapper.readTree(resp.body());
            JsonNode err = root.path("ResponseMetadata").path("Error");
            if (err != null && !err.isNull() && err.has("Code")) {
                log.warn("豆包搜索业务错误: {} {}", err.path("Code").asText(), err.path("Message").asText());
                return List.of();
            }
            JsonNode items = root.path("Result").path("WebResults");
            if (!items.isArray()) return List.of();
            List<WebResult> out = new ArrayList<>();
            for (JsonNode it : items) {
                out.add(new WebResult(
                        text(it, "Title"), text(it, "Url"), text(it, "Snippet"), text(it, "Summary"),
                        text(it, "SiteName"), text(it, "PublishTime"), it.path("AuthInfoLevel").asInt(0)));
            }
            return out;
        } catch (Exception e) {
            log.warn("豆包搜索失败 query={} err={}", query, e.getMessage());
            return List.of();
        }
    }

    private String text(JsonNode n, String k) {
        JsonNode v = n.path(k);
        return v == null || v.isNull() ? null : v.asText(null);
    }

    private String truncate(String s, int max) {
        return s == null ? "" : (s.length() <= max ? s : s.substring(0, max));
    }
}
