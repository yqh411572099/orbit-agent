package com.butler.application;

import com.butler.domain.model.KnowledgeEntry;
import com.butler.domain.model.KnowledgeSource;
import com.butler.domain.model.KnowledgeStatus;
import com.butler.domain.repository.KnowledgeEntryRepository;
import com.butler.domain.service.EmbeddingPort;
import com.butler.domain.service.KnowledgeVectorStore;
import com.butler.domain.service.WebSearchPort;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 知识沉淀应用服务：
 * - 联网检索结果先存为 PENDING 候选（按 url 去重）；
 * - 用户确认时向量化并写入向量库，状态转 CONFIRMED；
 * - 检索优先走向量语义相似度；embedding/向量库不可用时降级为关键词匹配。
 */
@Service
public class KnowledgeAppService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeAppService.class);
    private static final int MAX_PENDING_PER_QUERY = 3;
    private static final int SEARCH_LIMIT = 5;

    private final KnowledgeEntryRepository repository;
    private final EmbeddingPort embeddingPort;
    private final KnowledgeVectorStore vectorStore;

    public KnowledgeAppService(KnowledgeEntryRepository repository,
                               EmbeddingPort embeddingPort,
                               KnowledgeVectorStore vectorStore) {
        this.repository = repository;
        this.embeddingPort = embeddingPort;
        this.vectorStore = vectorStore;
    }

    @Transactional
    public List<KnowledgeEntry> proposeFromWeb(Long userId, Long subSessionId, String query,
                                               List<WebSearchPort.WebResult> results) {
        List<KnowledgeEntry> created = new ArrayList<>();
        if (userId == null || results == null || results.isEmpty()) return created;
        int added = 0;
        for (WebSearchPort.WebResult r : results) {
            if (added >= MAX_PENDING_PER_QUERY) break;
            String url = r.url();
            if (url == null || url.isBlank()) continue;
            if (repository.existsByUserIdAndSourceUrl(userId, url)) continue;
            String body = r.summary() != null && !r.summary().isBlank() ? r.summary() : r.snippet();
            String title = r.title() == null || r.title().isBlank() ? query : r.title();
            KnowledgeEntry entry = new KnowledgeEntry(null, userId, subSessionId,
                    KnowledgeSource.WEB_SEARCH, KnowledgeStatus.PENDING,
                    title, body, url, query, null, null, Instant.now(), Instant.now());
            created.add(repository.save(entry));
            added++;
        }
        if (!created.isEmpty()) {
            log.info("知识候选已生成 userId={} count={}", userId, created.size());
        }
        return created;
    }

    @Transactional
    public KnowledgeEntry confirm(Long id) {
        KnowledgeEntry e = require(id);
        KnowledgeEntry confirmed = repository.save(e.withStatus(KnowledgeStatus.CONFIRMED));
        embedAndIndex(confirmed);
        return confirmed;
    }

    @Transactional
    public KnowledgeEntry reject(Long id) {
        KnowledgeEntry e = require(id);
        try { vectorStore.delete(id); } catch (Exception ignored) {}
        return repository.save(e.withStatus(KnowledgeStatus.REJECTED));
    }

    public List<KnowledgeEntry> listPending(Long userId, Long subSessionId) {
        return repository.findByUserIdAndStatus(userId, KnowledgeStatus.PENDING).stream()
                .filter(e -> subSessionId == null
                        || e.getSubSessionId() == null
                        || e.getSubSessionId().equals(subSessionId))
                .toList();
    }

    public List<KnowledgeEntry> searchConfirmed(Long userId, Long subSessionId, String query, int limit) {
        int topK = limit <= 0 ? SEARCH_LIMIT : limit;
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
        List<KnowledgeEntry> confirmed = repository.findConfirmedForSearch(userId, subSessionId).stream()
                .filter(e -> e.isValidOn(today))
                .toList();
        if (confirmed.isEmpty()) return List.of();

        if (embeddingPort.available() && vectorStore.ready()) {
            try {
                List<Float> qv = embeddingPort.embed(query);
                if (qv != null && !qv.isEmpty()) {
                    List<Long> scopeIds = confirmed.stream().map(KnowledgeEntry::getId).toList();
                    List<Long> hitIds = vectorStore.searchWithin(qv, scopeIds, topK);
                    if (!hitIds.isEmpty()) {
                        Map<Long, KnowledgeEntry> byId = new LinkedHashMap<>();
                        for (KnowledgeEntry e : confirmed) byId.put(e.getId(), e);
                        List<KnowledgeEntry> ordered = new ArrayList<>();
                        for (Long hid : hitIds) {
                            KnowledgeEntry e = byId.get(hid);
                            if (e != null) ordered.add(e);
                        }
                        if (!ordered.isEmpty()) return ordered;
                    }
                }
            } catch (Exception e) {
                log.warn("向量检索失败，降级为关键词匹配: {}", e.getMessage());
            }
        }
        return keywordFallback(confirmed, query, topK);
    }

    private void embedAndIndex(KnowledgeEntry e) {
        if (!embeddingPort.available() || !vectorStore.ready()) {
            log.warn("embedding/向量库不可用，知识 id={} 已存 DB 但未建立向量索引", e.getId());
            return;
        }
        try {
            String text = (e.getTitle() == null ? "" : e.getTitle() + "。")
                    + (e.getContent() == null ? "" : e.getContent());
            List<Float> v = embeddingPort.embed(text);
            if (v != null && !v.isEmpty()) {
                vectorStore.upsert(e.getId(), v);
                log.info("知识已向量化入库 id={} dim={}", e.getId(), v.size());
            }
        } catch (Exception ex) {
            log.warn("知识向量化失败 id={}: {}", e.getId(), ex.getMessage());
        }
    }

    private List<KnowledgeEntry> keywordFallback(List<KnowledgeEntry> confirmed, String query, int topK) {
        String q = query == null ? "" : query.toLowerCase();
        return confirmed.stream()
                .filter(e -> matches(e, q))
                .limit(topK)
                .toList();
    }

    private boolean matches(KnowledgeEntry e, String q) {
        if (q.isBlank()) return true;
        String hay = ((e.getTitle() == null ? "" : e.getTitle()) + " "
                + (e.getContent() == null ? "" : e.getContent())).toLowerCase();
        for (String kw : q.split("\\s+")) {
            if (!kw.isBlank() && hay.contains(kw)) return true;
        }
        return false;
    }

    private KnowledgeEntry require(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("知识条目不存在: " + id));
    }

    @Transactional
    public void deleteForUser(Long userId) {
        List<KnowledgeEntry> all = repository.findByUserId(userId);
        for (KnowledgeEntry e : all) {
            try { vectorStore.delete(e.getId()); } catch (Exception ignored) {}
        }
        repository.archiveAndDeleteByUserId(userId, "PURGE_USER");
    }

    @Transactional
    public void deleteForSubSession(Long subSessionId, Long userId) {
        List<KnowledgeEntry> all = repository.findBySubSessionId(subSessionId);
        for (KnowledgeEntry e : all) {
            try { vectorStore.delete(e.getId()); } catch (Exception ignored) {}
        }
        repository.archiveAndDeleteBySubSessionId(subSessionId, userId, "PURGE_SUB_SESSION");
    }

    /** 重新为所有已确认知识建立向量索引（向量库重启/补数据时用）。 */
    public int reindexAllConfirmed() {
        List<KnowledgeEntry> all = repository.findByStatus(KnowledgeStatus.CONFIRMED);
        int count = 0;
        for (KnowledgeEntry e : all) {
            embedAndIndex(e);
            count++;
        }
        log.info("已为 {} 条确认知识重建向量索引", count);
        return count;
    }
}
