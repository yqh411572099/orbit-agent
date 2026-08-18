package com.butler.application;

import com.butler.domain.model.*;
import com.butler.domain.repository.*;
import com.butler.domain.attribute.Attribute;
import com.butler.domain.attribute.AttributeDescriptor;
import com.butler.domain.scenario.ScenarioDomain;
import com.butler.domain.scenario.ScenarioRegistry;
import com.butler.infrastructure.llm.LlmPort;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MemoryExtractionAppService {

    private static final Logger log = LoggerFactory.getLogger(MemoryExtractionAppService.class);

    private final RawChatLogRepository rawChatLogRepository;
    private final UserMemoryRepository userMemoryRepository;
    private final MemorySessionRelRepository relRepository;
    private final SubSessionRepository subSessionRepository;
    private final ScenarioRegistry scenarioRegistry;
    private final LlmPort llmPort;

    public MemoryExtractionAppService(RawChatLogRepository rawChatLogRepository,
                                      UserMemoryRepository userMemoryRepository,
                                      MemorySessionRelRepository relRepository,
                                      SubSessionRepository subSessionRepository,
                                      ScenarioRegistry scenarioRegistry,
                                      LlmPort llmPort) {
        this.rawChatLogRepository = rawChatLogRepository;
        this.userMemoryRepository = userMemoryRepository;
        this.relRepository = relRepository;
        this.subSessionRepository = subSessionRepository;
        this.scenarioRegistry = scenarioRegistry;
        this.llmPort = llmPort;
    }

    @Transactional
    public int runForUser(Long userId, Instant from, Instant to) {
        List<RawChatLog> logs = rawChatLogRepository.findByUserIdAndCreatedAtBetween(userId, from, to);
        if (logs.isEmpty()) {
            return 0;
        }

        List<SubSession> active = subSessionRepository.findByUserIdAndStatus(userId, SubSessionStatus.ACTIVE);
        List<String> descs = active.stream().map(SubSession::getSessionDesc).toList();
        String attributeSchema = buildAttributeSchema(active);

        String conversation = renderConversation(logs);

        // 已有有效记忆（编号传给 LLM，用于判断哪些被新信息取代/矛盾）
        LocalDate today = LocalDate.now(java.time.ZoneId.of("Asia/Shanghai"));
        List<UserMemory> existing = userMemoryRepository.findByUserId(userId).stream()
                .filter(m -> m.isValidOn(today))
                .toList();
        String existingText = renderIndexedMemories(existing);

        LlmPort.ExtractResult result;
        try {
            result = llmPort.extractAndAssociate(conversation, descs, attributeSchema, existingText);
        } catch (Exception e) {
            log.warn("提炼/关联推理失败，降级为不提炼: userId={}, err={}", userId, e.getMessage());
            return 0;
        }

        if (result == null || result.memories() == null || result.memories().isEmpty()) {
            return 0;
        }

        Long firstLogId = logs.get(0).getId();
        int saved = 0;
        for (LlmPort.ExtractedMemory em : result.memories()) {
            if (em.content() == null || em.content().isBlank()) continue;
            UserMemory memory = userMemoryRepository.save(new UserMemory(
                    null, userId, MemoryCategory.safeValueOf(em.category()), em.content(),
                    blankToNull(em.subject()), blankToNull(em.subjectProfile()),
                    parseDate(em.eventDate()), parseDate(em.validFrom()), parseDate(em.validTo()),
                    blankToNull(em.location()), em.confidence(),
                    em.attributes() == null ? List.<Attribute>of() : em.attributes(),
                    firstLogId, Instant.now()));
            saved++;

            if (em.associatedIndexes() != null) {
                for (Integer idx : em.associatedIndexes()) {
                    if (idx == null || idx < 0 || idx >= active.size()) continue;
                    Long subId = active.get(idx).getId();
                    if (!relRepository.existsByMemoryIdAndSubSessionId(memory.getId(), subId)) {
                        try {
                            relRepository.save(new MemorySessionRel(null, memory.getId(), subId, Instant.now()));
                        } catch (Exception e) {
                            log.warn("关联写入失败，跳过: memoryId={}, subId={}", memory.getId(), subId);
                        }
                    }
                }
            }
        }
        // 把本次新信息判定为“已过时/矛盾”的旧记忆软失效（validTo=昨天），读取层会自动过滤
        int expired = 0;
        if (result.supersededIndexes() != null && !existing.isEmpty()) {
            LocalDate yesterday = today.minusDays(1);
            for (Integer idx : result.supersededIndexes()) {
                if (idx == null || idx < 0 || idx >= existing.size()) continue;
                UserMemory oldM = existing.get(idx);
                if (oldM.getValidTo() != null) continue;
                UserMemory revised = new UserMemory(oldM.getId(), oldM.getUserId(), oldM.getCategory(),
                        oldM.getContent(), oldM.getSubject(), oldM.getSubjectProfile(), oldM.getEventDate(),
                        oldM.getValidFrom(), yesterday, oldM.getLocation(), oldM.getConfidence(),
                        oldM.getAttributes(), oldM.getSourceRawLogId(), oldM.getCreatedAt());
                userMemoryRepository.save(revised);
                expired++;
                // 关联关系不删：历史复盘仍可见，但主/子对话读取时按有效期过滤
            }
        }
        log.info("记忆提炼完成 userId={} saved={} expired={}", userId, saved, expired);
        return saved;
    }

    private String renderIndexedMemories(List<UserMemory> memories) {
        if (memories.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < memories.size(); i++) {
            UserMemory m = memories.get(i);
            sb.append("[").append(i).append("] (").append(m.getCategory()).append(")");
            if (m.getSubject() != null) sb.append(" subject=").append(m.getSubject());
            if (m.getEventDate() != null) sb.append(" eventDate=").append(m.getEventDate());
            sb.append(" ").append(m.getContent()).append("\n");
        }
        return sb.toString();
    }

    /** 合并所有活跃子会话对应场景的属性目录，去重后渲染成 LLM 可读的 schema。 */
    private String buildAttributeSchema(List<SubSession> active) {
        Set<String> seenTypes = new LinkedHashSet<>();
        StringBuilder sb = new StringBuilder();
        for (SubSession sub : active) {
            if (!scenarioRegistry.supports(sub.getScenarioType())) continue;
            ScenarioDomain domain = scenarioRegistry.get(sub.getScenarioType());
            for (AttributeDescriptor d : domain.attributeCatalog()) {
                if (!seenTypes.add(d.type())) continue;
                sb.append("- type=").append(d.type()).append("：").append(d.description());
                if (!d.fields().isEmpty()) {
                    sb.append("；字段：");
                    List<String> fs = new ArrayList<>();
                    for (AttributeDescriptor.FieldSpec f : d.fields()) {
                        fs.add(f.name() + "(" + f.schemaType() + (f.required() ? ",必填" : ",选填") + ")");
                    }
                    sb.append(String.join("、", fs));
                }
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    private LocalDate parseDate(String raw) {
        if (raw == null) return null;
        String v = raw.trim();
        if (v.isBlank()) return null;
        try { return LocalDate.parse(v.substring(0, Math.min(10, v.length()))); }
        catch (Exception e) { return null; }
    }

    private String blankToNull(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }

    private String renderConversation(List<RawChatLog> logs) {
        List<String> lines = new ArrayList<>();
        for (RawChatLog l : logs) {
            if ("system".equals(l.getRole())) continue;
            String tag = l.getSessionType() == SessionType.SUB ? "子会话" : "主会话";
            lines.add("[%s] %s: %s".formatted(tag, l.getRole(), l.getContent()));
        }
        return String.join("\n", lines);
    }
}
