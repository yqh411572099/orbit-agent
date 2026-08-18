package com.butler.infrastructure.vector;

import com.butler.domain.service.KnowledgeVectorStore;
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
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 向量库实现：通过 HTTP 连接 Milvus Lite 桥接服务（本机一个 Python 进程，内嵌 pymilvus，本地文件存储）。
 * 换真正的 Milvus standalone/集群时，只需新增一个走 Milvus gRPC SDK 的 KnowledgeVectorStore 实现，
 * 应用层不变。
 *
 * <p>连接失败时不阻断启动，{@link #ready()} 返回 false，应用层降级为关键词检索。</p>
 */
@Component
public class MilvusKnowledgeVectorStore implements KnowledgeVectorStore {

    private static final Logger log = LoggerFactory.getLogger(MilvusKnowledgeVectorStore.class);

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper mapper = new ObjectMapper();
    private final String baseUrl;
    private final String collection;
    private final int dimension;
    private volatile boolean ready = false;

    public MilvusKnowledgeVectorStore(
            @Value("${milvus.host:127.0.0.1}") String host,
            @Value("${milvus.port:19531}") int port,
            @Value("${milvus.collection:butler_knowledge}") String collection,
            @Value("${embedding.dimension:2048}") int dimension) {
        this.baseUrl = "http://" + host + ":" + port;
        this.collection = collection;
        this.dimension = dimension;
    }

    @PostConstruct
    public void init() {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/health"))
                    .timeout(Duration.ofSeconds(5)).GET().build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            JsonNode node = mapper.readTree(resp.body());
            ready = resp.statusCode() == 200 && node.path("ok").asBoolean(false);
            log.info("向量库桥接连接状态: {} ready={}", baseUrl, ready);
        } catch (Exception e) {
            log.warn("向量库桥接不可用，语义检索降级为关键词: {}", e.getMessage());
            ready = false;
        }
    }

    @Override
    public void upsert(Long knowledgeId, List<Float> vector) {
        upsertAll(List.of(knowledgeId), List.of(vector));
    }

    @Override
    public void upsertAll(List<Long> knowledgeIds, List<List<Float>> vectors) {
        if (!ready || knowledgeIds == null || knowledgeIds.isEmpty()) return;
        try {
            ObjectNode body = mapper.createObjectNode();
            body.put("collection", collection);
            body.put("dimension", dimension);
            ArrayNode points = body.putArray("points");
            for (int i = 0; i < knowledgeIds.size(); i++) {
                ObjectNode p = points.addObject();
                p.put("id", knowledgeIds.get(i));
                ArrayNode v = p.putArray("vector");
                for (Float f : vectors.get(i)) v.add(f);
            }
            post("/upsert", body);
        } catch (Exception e) {
            log.warn("向量库 upsert 失败: {}", e.getMessage());
        }
    }

    @Override
    public List<Long> search(List<Float> queryVector, int topK) {
        return searchWithin(queryVector, null, topK);
    }

    @Override
    public List<Long> searchWithin(List<Float> queryVector, List<Long> scopeIds, int topK) {
        if (!ready || queryVector == null || queryVector.isEmpty()) return List.of();
        try {
            ObjectNode body = mapper.createObjectNode();
            body.put("collection", collection);
            body.put("limit", Math.max(1, topK));
            ArrayNode v = body.putArray("vector");
            for (Float f : queryVector) v.add(f);
            if (scopeIds != null && !scopeIds.isEmpty()) {
                ArrayNode scope = body.putArray("ids_scope");
                for (Long id : scopeIds) scope.add(id);
            }
            JsonNode resp = post("/search", body);
            List<Long> ids = new ArrayList<>();
            JsonNode hits = resp.path("hits");
            if (hits.isArray()) {
                for (JsonNode h : hits) ids.add(h.path("id").asLong());
            }
            return ids;
        } catch (Exception e) {
            log.warn("向量库 search 失败: {}", e.getMessage());
            return List.of();
        }
    }

    @Override
    public void delete(Long knowledgeId) {
        delete(List.of(knowledgeId));
    }

    private void delete(List<Long> ids) {
        if (!ready || ids == null || ids.isEmpty()) return;
        try {
            ObjectNode body = mapper.createObjectNode();
            body.put("collection", collection);
            ArrayNode arr = body.putArray("ids");
            for (Long id : ids) arr.add(id);
            post("/delete", body);
        } catch (Exception e) {
            log.warn("向量库 delete 失败: {}", e.getMessage());
        }
    }

    @Override
    public boolean ready() { return ready; }

    private JsonNode post(String path, ObjectNode body) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(20))
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body), StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() / 100 != 2) {
            throw new RuntimeException("HTTP " + resp.statusCode() + ": " + resp.body());
        }
        return mapper.readTree(resp.body());
    }
}
