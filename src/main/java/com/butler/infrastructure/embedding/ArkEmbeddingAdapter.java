package com.butler.infrastructure.embedding;

import com.butler.domain.service.EmbeddingPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
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
import org.springframework.stereotype.Component;

/**
 * 火山方舟 ARK embedding 实现（OpenAI 兼容 /embeddings）。
 * 复用 ARK_API_KEY；模型/接入点由 embedding.endpoint 配置（如 ep-xxxx 或 doubao-embedding-vision）。
 */
@Component
public class ArkEmbeddingAdapter implements EmbeddingPort {

    private static final Logger log = LoggerFactory.getLogger(ArkEmbeddingAdapter.class);

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8)).build();
    private final ObjectMapper mapper = new ObjectMapper();
    private final String apiKey;
    private final String baseUrl;
    private final String endpoint;
    private final int dimension;

    public ArkEmbeddingAdapter(
            @Value("${embedding.api-key:${ARK_API_KEY:}}") String apiKey,
            @Value("${embedding.base-url:${ARK_BASE_URL:https://ark.cn-beijing.volces.com/api/plan/v3}}") String baseUrl,
            @Value("${embedding.endpoint:${ARK_EMBEDDING_ENDPOINT:}}") String endpoint,
            @Value("${embedding.dimension:2048}") int dimension) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.baseUrl = baseUrl == null ? "https://ark.cn-beijing.volces.com/api/plan/v3" : baseUrl.trim();
        this.endpoint = endpoint == null ? "" : endpoint.trim();
        this.dimension = dimension;
    }

    @Override
    public List<Float> embed(String text) {
        List<List<Float>> all = embedAll(List.of(text == null ? "" : text));
        return all.isEmpty() ? List.of() : all.get(0);
    }

    @Override
    public List<List<Float>> embedAll(List<String> texts) {
        List<List<Float>> result = new ArrayList<>();
        if (!available() || texts == null || texts.isEmpty()) return result;
        try {
            ObjectNode body = mapper.createObjectNode();
            body.put("model", endpoint);
            ArrayNode input = body.putArray("input");
            for (String t : texts) input.add(t == null ? "" : t);

            String url = baseUrl.replaceAll("/+$", "") + "/embeddings";
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(30))
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() / 100 != 2) {
                log.warn("ARK embedding 返回非2xx: {} {}", resp.statusCode(), truncate(resp.body(), 300));
                return result;
            }
            JsonNode root = mapper.readTree(resp.body());
            JsonNode data = root.path("data");
            if (!data.isArray()) return result;
            for (JsonNode item : data) {
                JsonNode emb = item.path("embedding");
                List<Float> vec = new ArrayList<>();
                if (emb.isArray()) {
                    for (JsonNode v : emb) vec.add(v.floatValue());
                }
                result.add(vec);
            }
            return result;
        } catch (Exception e) {
            log.warn("ARK embedding 失败: {}", e.getMessage());
            return result;
        }
    }

    @Override
    public int dimension() { return dimension; }

    @Override
    public boolean available() {
        return !apiKey.isBlank() && !endpoint.isBlank();
    }

    private String truncate(String s, int max) {
        return s == null ? "" : (s.length() <= max ? s : s.substring(0, max));
    }
}
