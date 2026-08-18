package com.butler.perception.api;

import com.butler.application.KnowledgeAppService;
import com.butler.domain.service.EmbeddingPort;
import com.butler.domain.service.KnowledgeVectorStore;
import com.butler.domain.model.KnowledgeEntry;
import com.butler.infrastructure.auth.CurrentUser;
import java.time.Instant;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeController {

    private final KnowledgeAppService knowledgeAppService;
    private final EmbeddingPort embeddingPort;
    private final KnowledgeVectorStore vectorStore;

    public KnowledgeController(KnowledgeAppService knowledgeAppService,
                                EmbeddingPort embeddingPort,
                                KnowledgeVectorStore vectorStore) {
        this.knowledgeAppService = knowledgeAppService;
        this.embeddingPort = embeddingPort;
        this.vectorStore = vectorStore;
    }

    /** 列出待确认的知识候选（用户级 + 指定子会话级）。 */
    @GetMapping("/pending")
    public List<KnowledgeView> pending(@RequestParam(required = false) Long subSessionId) {
        Long userId = CurrentUser.userId();
        return knowledgeAppService.listPending(userId, subSessionId).stream()
                .map(KnowledgeController::toView).toList();
    }

    @PostMapping("/{id}/confirm")
    public KnowledgeView confirm(@PathVariable Long id) {
        return toView(knowledgeAppService.confirm(id));
    }

    @PostMapping("/{id}/reject")
    public KnowledgeView reject(@PathVariable Long id) {
        return toView(knowledgeAppService.reject(id));
    }

    static KnowledgeView toView(KnowledgeEntry e) {
        return new KnowledgeView(e.getId(), e.getUserId(), e.getSubSessionId(),
                e.getSource().name(), e.getSource().getLabel(),
                e.getStatus().name(), e.getStatus().getLabel(),
                e.getTitle(), e.getContent(), e.getSourceUrl(), e.getQuery(),
                e.getCreatedAt() == null ? null : e.getCreatedAt().toEpochMilli());
    }

    public record KnowledgeView(Long id, Long userId, Long subSessionId,
                                String source, String sourceLabel,
                                String status, String statusLabel,
                                String title, String content, String sourceUrl, String query,
                                Long createdAt) {}

    @GetMapping("/health")
    public java.util.Map<String,Object> health() {
        return java.util.Map.of(
            "embeddingAvailable", embeddingPort.available(),
            "embeddingDimension", embeddingPort.dimension(),
            "vectorReady", vectorStore.ready());
    }
}
