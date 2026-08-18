package com.butler.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.butler.domain.attribute.Attribute;
import com.butler.domain.model.MemoryCategory;
import com.butler.domain.model.MemorySessionRel;
import com.butler.domain.model.RawChatLog;
import com.butler.domain.model.SessionType;
import com.butler.domain.model.SubSession;
import com.butler.domain.model.SubSessionStatus;
import com.butler.domain.model.Task;
import com.butler.domain.model.UserMemory;
import com.butler.domain.repository.MemorySessionRelRepository;
import com.butler.domain.repository.RawChatLogRepository;
import com.butler.domain.repository.SubSessionRepository;
import com.butler.domain.repository.TaskRepository;
import com.butler.domain.repository.UserMemoryRepository;
import com.butler.domain.scenario.ScenarioDomain;
import com.butler.domain.scenario.ScenarioRegistry;
import com.butler.infrastructure.llm.LlmPort;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConversationAppService {
    private static final Logger log = LoggerFactory.getLogger(ConversationAppService.class);

    private final RawChatLogRepository rawChatLogRepository;
    private final SubSessionRepository subSessionRepository;
    private final TaskRepository taskRepository;
    private final LlmPort llmPort;
    private final TimelineAppService timelineAppService;
    private final UserMemoryRepository userMemoryRepository;
    private final MemorySessionRelRepository relRepository;
    private final ScenarioRegistry scenarioRegistry;
    private final ObjectMapper objectMapper;
    private final PendingProposalStore pendingProposalStore;

    public ConversationAppService(RawChatLogRepository rawChatLogRepository,
                                  SubSessionRepository subSessionRepository,
                                  TaskRepository taskRepository,
                                  LlmPort llmPort,
                                  TimelineAppService timelineAppService,
                                  UserMemoryRepository userMemoryRepository,
                                  MemorySessionRelRepository relRepository,
                                  ScenarioRegistry scenarioRegistry,
                                  ObjectMapper objectMapper,
                                  PendingProposalStore pendingProposalStore) {
        this.rawChatLogRepository = rawChatLogRepository;
        this.subSessionRepository = subSessionRepository;
        this.taskRepository = taskRepository;
        this.llmPort = llmPort;
        this.timelineAppService = timelineAppService;
        this.userMemoryRepository = userMemoryRepository;
        this.relRepository = relRepository;
        this.scenarioRegistry = scenarioRegistry;
        this.objectMapper = objectMapper;
        this.pendingProposalStore = pendingProposalStore;
    }

    /** 独立事务保存一条聊天记录，即使后续业务处理异常也不回滚。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveMessage(Long userId, SessionType sessionType, Long subSessionId, String role, String content) {
        saveMessage(userId, sessionType, subSessionId, role, content, null);
    }

    /** 独立事务保存一条聊天记录（可带思考过程），即使后续业务处理异常也不回滚。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveMessage(Long userId, SessionType sessionType, Long subSessionId,
                            String role, String content, String reasoning) {
        rawChatLogRepository.save(new RawChatLog(
                null, userId, sessionType, subSessionId, role, content, reasoning, Instant.now()));
    }

    @Transactional
    public RawChatLog ingest(Long userId, SessionType sessionType, Long subSessionId, String content) {
        RawChatLog logEntry = rawChatLogRepository.save(new RawChatLog(
                null, userId, sessionType, subSessionId, "user", content, null, Instant.now()));
        if (sessionType == SessionType.SUB && subSessionId != null) {
            processSubSessionMessage(subSessionId, content);
        }
        return logEntry;
    }

    /**
     * 直接更新子对话已收集的关键字段（如浏览器定位得到的城市/区县），并重排时间轴。
     * 与对话提炼不同，这里来自确定性渠道（如定位），不经过 LLM。
     */
    @Transactional
    public Map<String, String> updateCollected(Long subSessionId, Map<String, String> updates) {
        SubSession sub = subSessionRepository.findById(subSessionId).orElse(null);
        if (sub == null || !scenarioRegistry.supports(sub.getScenarioType()) || updates == null || updates.isEmpty()) {
            return Map.of();
        }
        ScenarioDomain domain = scenarioRegistry.get(sub.getScenarioType());
        Map<String, String> customLabels = CustomFocusLabels.read(sub);
        ScenarioStateSupport.ScenarioState state = ScenarioStateSupport.parse(domain, sub.getCollectedInfo(), customLabels);

        Set<String> allowedKeys = domain.collectFields().stream()
                .map(ScenarioDomain.CollectField::key).collect(java.util.stream.Collectors.toSet());
        Map<String, String> safe = new LinkedHashMap<>();
        updates.forEach((k, v) -> {
            if (allowedKeys.contains(k) && v != null && !v.isBlank()) safe.put(k, v.trim());
        });
        if (safe.isEmpty()) return state.collected();

        List<String> effectiveFocus = ScenarioDomain.resolveEffectiveFocusAreas(
                domain.focusAreas(), state.focusAreas());
        Map<String, String> collected = new LinkedHashMap<>(state.collected());
        collected.putAll(safe);
        sub.setCollectedInfo(ScenarioStateSupport.render(domain, collected, effectiveFocus, customLabels));
        subSessionRepository.save(sub);
        timelineAppService.resync(sub, domain, collected, effectiveFocus, "");

        return collected;
    }

    /** 读取子对话当前已收集字段（供前端判断是否需要定位等）。 */
    @Transactional
    public Map<String, String> getCollected(Long subSessionId) {
        SubSession sub = subSessionRepository.findById(subSessionId).orElse(null);
        if (sub == null || !scenarioRegistry.supports(sub.getScenarioType())) return Map.of();
        ScenarioDomain domain = scenarioRegistry.get(sub.getScenarioType());
        return ScenarioStateSupport.parse(domain, sub.getCollectedInfo(), CustomFocusLabels.read(sub)).collected();
    }

    /** 读取子对话的自定义关注项 key→中文label（不在内置 focusAreas 里、由对话动态创建的项）。 */
    @Transactional
    public java.util.Map<String, String> getCustomFocusLabels(Long subSessionId) {
        return subSessionRepository.findById(subSessionId)
                .map(CustomFocusLabels::read)
                .orElse(java.util.Map.of());
    }

    /** 读取子对话当前已收集字段 + 已启用关注项（关注项弹窗回显的权威来源）。 */
    @Transactional
    public ScenarioStateSupport.ScenarioState getState(Long subSessionId) {
        SubSession sub = subSessionRepository.findById(subSessionId).orElse(null);
        if (sub == null || !scenarioRegistry.supports(sub.getScenarioType())) {
            return new ScenarioStateSupport.ScenarioState(Map.of(), List.of());
        }
        ScenarioDomain domain = scenarioRegistry.get(sub.getScenarioType());
        return ScenarioStateSupport.parse(domain, sub.getCollectedInfo(), CustomFocusLabels.read(sub));
    }

    @Transactional
    /**
     * 兼容旧的“发消息即应用”入口（如 /api/messages）：解析变更后直接应用。
     * 新的流式对话入口走 {@link #proposeSubSessionChange} + 确认后 {@link #applyProposal}。
     */
    public void processSubSessionMessage(Long subSessionId, String newMessage) {
        ChangePreview preview = proposeSubSessionChange(subSessionId, newMessage);
        if (preview == null || preview.isEmpty()) return;
        ApplyOutcome outcome = applyProposal(preview.proposalId(), newMessage);
        if (outcome.note() != null && !outcome.note().isBlank()) {
            SubSession sub = subSessionRepository.findById(subSessionId).orElse(null);
            if (sub != null) {
                rawChatLogRepository.save(new RawChatLog(null, sub.getUserId(), SessionType.SUB,
                        subSessionId, "assistant", outcome.note(), null, Instant.now()));
            }
        }
    }

    /** 仅解析用户输入将引发的变更，生成预览并暂存，不落库（修改前弹窗确认用）。 */
    public ChangePreview proposeSubSessionChange(Long subSessionId, String newMessage) {
        SubSession sub = subSessionRepository.findById(subSessionId).orElse(null);
        if (sub == null || sub.getStatus() == SubSessionStatus.ARCHIVED) return null;
        if (!scenarioRegistry.supports(sub.getScenarioType())) return null;

        ScenarioDomain domain = scenarioRegistry.get(sub.getScenarioType());
        Map<String, String> existingCustomLabels = CustomFocusLabels.read(sub);
        ScenarioStateSupport.ScenarioState state = ScenarioStateSupport.parse(domain, sub.getCollectedInfo(), existingCustomLabels);
        List<ScenarioDomain.ScenarioIntent> intents = domain.interpret(
                newMessage, state.collected(), state.focusAreas(),
                (hints, message) -> {
                    LlmPort.ScenarioEvent event = extractEvent(domain, state, existingCustomLabels, message);
                    return new ScenarioDomain.DomainEvent(event.fieldUpdates(), event.completedKeywords(),
                            event.enableFocusAreas(), event.disableFocusAreas(), event.note(), event.affectsTasks());
                });
        ScenarioDomain.ScenarioIntent event = aggregateIntents(intents);

        LocalDate today = LocalDate.now(java.time.ZoneId.of("Asia/Shanghai"));
        Map<String, String> updates = domain.normalizeUpdates(newMessage,
                sanitizeUpdates(domain, event.updatedFields()), today);
        List<String> completed = sanitizeList(event.completedMilestones());
        List<FocusRef> enable = parseFocusRefs(event.enableFocusAreas());
        List<FocusRef> disable = parseFocusRefs(event.disableFocusAreas());
        Map<String, String> customLabels = new LinkedHashMap<>(existingCustomLabels);
        for (FocusRef ref : enable) {
            if (ref.label() != null) customLabels = CustomFocusLabels.with(customLabels, ref.key(), ref.label());
        }

        List<String> beforeFocus = ScenarioDomain.resolveEffectiveFocusAreas(
                domain.focusAreas(), state.focusAreas());
        Set<String> selectedFocus = new LinkedHashSet<>(state.focusAreas());
        for (FocusRef ref : enable) selectedFocus.add(ref.key());
        for (FocusRef ref : disable) {
            if (!isMandatoryFocus(domain, ref.key())) selectedFocus.remove(ref.key());
        }
        List<String> effectiveFocus = ScenarioDomain.resolveEffectiveFocusAreas(
                domain.focusAreas(), new ArrayList<>(selectedFocus));

        Map<String, String> collected = new LinkedHashMap<>(state.collected());
        collected.putAll(updates);

        // 1) 字段（目标定制信息/头部）变更
        Map<String, String> labelByKey = new LinkedHashMap<>();
        for (ScenarioDomain.CollectField f : domain.collectFields()) labelByKey.put(f.key(), f.label());
        List<ChangePreview.FieldChange> fieldChanges = new ArrayList<>();
        for (Map.Entry<String, String> e : updates.entrySet()) {
            String key = e.getKey();
            if (key.equals("latitude") || key.equals("longitude")) continue;
            String oldValue = state.collected().get(key);
            if (Objects.equals(oldValue, e.getValue())) continue;
            fieldChanges.add(new ChangePreview.FieldChange(key,
                    labelByKey.getOrDefault(key, key), oldValue, e.getValue()));
        }

        // 2) 关注项变更
        List<String> focusAdded = new ArrayList<>();
        for (FocusRef ref : enable) {
            String key = ref.key();
            if (!state.focusAreas().contains(key) && !beforeFocus.contains(key)) {
                focusAdded.add(focusLabel(domain, key, customLabels, ref.label()));
            }
        }
        List<String> focusRemoved = new ArrayList<>();
        for (FocusRef ref : disable) {
            String key = ref.key();
            if (!isMandatoryFocus(domain, key) && state.focusAreas().contains(key)) {
                focusRemoved.add(focusLabel(domain, key, customLabels, ref.label()));
            }
        }

        // 3) 待办变更（dry-run）
        TimelineAppService.TimelineDiff diff =
                timelineAppService.previewDiff(sub, domain, collected, effectiveFocus);
        List<ChangePreview.TaskChange> tasksAdded = new ArrayList<>();
        for (ScenarioDomain.PlannedTask p : diff.added()) {
            tasksAdded.add(new ChangePreview.TaskChange(p.title(), p.focusArea(),
                    fmt(p.remindDate()), fmt(p.dueDate()), null));
        }
        List<ChangePreview.TaskChange> tasksUpdated = new ArrayList<>();
        for (Map.Entry<Task, ScenarioDomain.PlannedTask> e : diff.updated().entrySet()) {
            Task old = e.getKey();
            ScenarioDomain.PlannedTask p = e.getValue();
            tasksUpdated.add(new ChangePreview.TaskChange(p.title(), p.focusArea(),
                    fmt(p.remindDate()), fmt(p.dueDate()),
                    old.getDueDate() == null ? null : old.getDueDate().toString()));
        }
        List<String> tasksRemoved = diff.removed().stream().map(Task::getContent).toList();
        List<String> tasksCompleted = matchCompletedTitles(subSessionId, completed);

        // 4) 对话记忆变更
        List<String> memories = new ArrayList<>();
        if (event.memoryUpserts() != null) {
            for (Attribute a : event.memoryUpserts()) {
                String text = com.butler.domain.attribute.AttributeRenderer.toText(a);
                if (!text.isBlank()) memories.add(text);
            }
        }

        // 5) 动态任务预演：为时间轴未覆盖的关注项（用户自定义习惯等）先让模型生成拟新建待办，
        //    随确认弹窗一并展示；确认时直接复用，不重复调用模型。
        List<LlmPort.TaskItem> plannedDynamic = List.of();
        List<ChangePreview.TaskChange> tasksPlanned = new ArrayList<>();
        if (event.affectsTasks()) {
            plannedDynamic = planDynamicTasks(sub, subSessionId, newMessage, domain, effectiveFocus);
            List<Task> existingDynamic = taskRepository.findBySubSessionId(subSessionId).stream()
                    .filter(t -> t.getModuleKey() == null).toList();
            for (LlmPort.TaskItem item : plannedDynamic) {
                LocalDate due = Task.parseDueDate(item.dueDate());
                String recurrence = item.recurrence() == null ? "" : item.recurrence().trim();
                String remindTime = item.remindTime();
                String detail = item.detail();
                Task existing = findExistingDynamic(existingDynamic, item, due, recurrence);
                if (existing != null) {
                    // 动态任务的“更新”只对周期任务有意义（提醒时刻变化）；非周期任务被模型原样回显时直接跳过，
                    // 避免把“日期没变、仅描述性 detail 随孕周滚动”误报为改期。
                    if (!recurrence.isBlank()) {
                        String oldRemindTime = existing.getRemindTime() == null ? null
                                : existing.getRemindTime().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
                        boolean changed = !java.util.Objects.equals(oldRemindTime, normalizeTime(remindTime))
                                || !java.util.Objects.equals(existing.getDueDate(), due);
                        if (changed) {
                            tasksUpdated.add(new ChangePreview.TaskChange(item.content(), item.focusArea(),
                                    fmt(due), fmt(due),
                                    existing.getDueDate() == null ? null : existing.getDueDate().toString(),
                                    recurrence, remindTime, detail, oldRemindTime));
                        }
                    }
                    continue;
                }
                tasksPlanned.add(new ChangePreview.TaskChange(item.content(), item.focusArea(),
                        fmt(due), fmt(due), null,
                        recurrence.isBlank() ? null : recurrence, remindTime, detail));
            }
        }

        PendingProposalStore.StoredProposal stored = pendingProposalStore.put(
                sub.getUserId(), subSessionId, sub.getScenarioType(), newMessage,
                collected, effectiveFocus,
                customLabels,
                event.memoryUpserts() == null ? List.of() : event.memoryUpserts(), completed,
                plannedDynamic);

        ChangePreview preview = new ChangePreview(stored.id(), fieldChanges, focusAdded, focusRemoved,
                tasksAdded, tasksUpdated, tasksRemoved, tasksCompleted, tasksPlanned,
                memories, event.note());
        try {
            pendingProposalStore.attachPreview(stored.id(), objectMapper.writeValueAsString(preview));
        } catch (Exception ignored) {}
        return preview;
    }

    /** 确认应用一份暂存的变更提案。供“弹窗确认”后端接口使用。 */
    @Transactional
    public void applyProposal(String proposalId) {
        applyProposal(proposalId, null);
    }

    private ApplyOutcome applyProposal(String proposalId, String fallbackMessage) {
        PendingProposalStore.StoredProposal p = pendingProposalStore.get(proposalId);
        if (p == null) {
            throw new IllegalArgumentException("变更已过期或不存在，请重新发送消息");
        }
        SubSession sub = subSessionRepository.findById(p.subSessionId()).orElse(null);
        if (sub == null || sub.getStatus() == SubSessionStatus.ARCHIVED) {
            pendingProposalStore.remove(proposalId);
            return new ApplyOutcome("");
        }
        ScenarioDomain domain = scenarioRegistry.get(p.scenarioType());
        List<String> effectiveFocus = p.effectiveFocus();
        Map<String, String> collected = p.newCollected();
        Map<String, String> customLabels = CustomFocusLabels.read(sub);
        customLabels.putAll(p.customFocusLabels() == null ? Map.of() : p.customFocusLabels());
        CustomFocusLabels.write(sub, customLabels);

        java.util.Set<String> beforeFocus = new java.util.LinkedHashSet<>(ScenarioDomain.resolveEffectiveFocusAreas(
                domain.focusAreas(),
                ScenarioStateSupport.parse(domain, sub.getCollectedInfo(), customLabels).focusAreas()));
        java.util.Set<String> removedFocus = new java.util.LinkedHashSet<>(beforeFocus);
        effectiveFocus.forEach(removedFocus::remove);

        sub.setCollectedInfo(ScenarioStateSupport.render(domain, collected, effectiveFocus, customLabels));
        subSessionRepository.save(sub);

        int dynamicRemoved = cleanupDisabledFocusTasks(sub, removedFocus);
        String note = timelineAppService.resync(sub, domain, collected, effectiveFocus, "");
        if (dynamicRemoved > 0) {
            note = (note == null || note.isBlank() ? "" : note)
                    + "已移除 " + dynamicRemoved + " 项该关注项的待办。";
        }

        upsertMemoryAttributes(sub, p.memoryUpserts());
        if (!p.completedKeywords().isEmpty()) markCompletedByKeywords(sub.getId(), p.completedKeywords());

        // 动态任务以预览时模型给出的 plannedDynamicTasks 为准；预览判定无任务变更时不在确认时再次调模型，
        // 避免纯咨询/闲聊消息在“确认”环节被误生成待办。
        if (p.plannedDynamicTasks() != null && !p.plannedDynamicTasks().isEmpty()) {
            replaceDynamicTasks(sub.getId(), p.plannedDynamicTasks());
        }

        pendingProposalStore.remove(proposalId);
        return new ApplyOutcome(note);
    }

    /** 放弃一份暂存的变更提案。 */
    public void discardProposal(String proposalId) {
        pendingProposalStore.discard(proposalId);
    }

    private String focusLabel(ScenarioDomain domain, String key, Map<String, String> customLabels, String fallback) {
        String label = domain.focusAreas().stream().filter(f -> f.key().equals(key))
                .map(ScenarioDomain.FocusArea::label).findFirst().orElse(null);
        if (label != null) return label;
        if (customLabels != null && customLabels.get(key) != null) return customLabels.get(key);
        if (fallback != null && !fallback.isBlank()) return fallback;
        return key;
    }

    /** 解析 LLM 返回的关注项引用：内置项为 "key"，自定义项为 "key|中文名称"。 */
    private List<FocusRef> parseFocusRefs(List<String> raw) {
        List<FocusRef> refs = new ArrayList<>();
        if (raw == null) return refs;
        for (String item : raw) {
            if (item == null) continue;
            String v = item.trim();
            if (v.isEmpty()) continue;
            int sep = v.indexOf('|');
            if (sep > 0) {
                refs.add(new FocusRef(v.substring(0, sep).trim(), v.substring(sep + 1).trim()));
            } else {
                refs.add(new FocusRef(v, null));
            }
        }
        return refs;
    }

    private record FocusRef(String key, String label) {}

    private List<String> matchCompletedTitles(Long subSessionId, List<String> completedKeywords) {
        if (completedKeywords.isEmpty()) return List.of();
        List<String> lower = completedKeywords.stream().map(String::toLowerCase).toList();
        return taskRepository.findBySubSessionId(subSessionId).stream()
                .filter(t -> !t.isCompleted())
                .map(Task::getContent)
                .filter(c -> {
                    String lc = c.toLowerCase();
                    return lower.stream().anyMatch(lc::contains);
                })
                .distinct().toList();
    }

    private static String fmt(LocalDate d) {
        return d == null ? null : d.toString();
    }

    private record ApplyOutcome(String note) {}


    private ScenarioDomain.ScenarioIntent aggregateIntents(List<ScenarioDomain.ScenarioIntent> intents) {
        Map<String, String> updates = new LinkedHashMap<>();
        List<String> completed = new ArrayList<>();
        List<String> enable = new ArrayList<>();
        List<String> disable = new ArrayList<>();
        List<Attribute> memoryUpserts = new ArrayList<>();
        StringBuilder note = new StringBuilder();
        boolean affectsTasks = false;
        for (ScenarioDomain.ScenarioIntent intent : intents) {
            if (intent.updatedFields() != null) updates.putAll(intent.updatedFields());
            if (intent.completedMilestones() != null) completed.addAll(intent.completedMilestones());
            if (intent.enableFocusAreas() != null) enable.addAll(intent.enableFocusAreas());
            if (intent.disableFocusAreas() != null) disable.addAll(intent.disableFocusAreas());
            if (intent.memoryUpserts() != null) memoryUpserts.addAll(intent.memoryUpserts());
            if (intent.affectsTasks()) affectsTasks = true;
            if (intent.note() != null && !intent.note().isBlank()) {
                if (!note.isEmpty()) note.append('；');
                note.append(intent.note());
            }
        }
        return new ScenarioDomain.ScenarioIntent(updates, completed, enable, disable, memoryUpserts, note.toString(), affectsTasks);
    }

    private LlmPort.ScenarioEvent extractEvent(ScenarioDomain domain,
                                               ScenarioStateSupport.ScenarioState state,
                                               Map<String, String> customLabels,
                                               String newMessage) {
        try {
            List<String> hints = new ArrayList<>(domain.keyFieldHints());
            for (ScenarioDomain.FocusArea focusArea : domain.focusAreas()) {
                hints.add(focusArea.key() + "(关注项:" + focusArea.label() + ")");
            }
            return llmPort.extractScenarioEvent(domain.type(), hints,
                    ScenarioStateSupport.render(domain, state.collected(), state.focusAreas(), customLabels), newMessage);
        } catch (Exception e) {
            log.warn("场景事件提取失败 sub scenario={} err={}", domain.type(), e.getMessage());
            return new LlmPort.ScenarioEvent(Map.of(), List.of(), List.of(), List.of(), "");
        }
    }

    private Map<String, String> sanitizeUpdates(ScenarioDomain domain, Map<String, String> updates) {
        Map<String, String> result = new LinkedHashMap<>();
        if (updates == null) return result;
        Set<String> allowed = domain.collectFields().stream()
                .map(ScenarioDomain.CollectField::key)
                .collect(java.util.stream.Collectors.toSet());
        for (Map.Entry<String, String> entry : updates.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) continue;
            String key = entry.getKey().trim();
            String value = entry.getValue().trim();
            if (allowed.contains(key) && !value.isBlank()) result.put(key, value);
        }
        return result;
    }

    private List<String> sanitizeList(List<String> values) {
        if (values == null) return List.of();
        return values.stream()
                .filter(v -> v != null && !v.isBlank())
                .map(String::trim)
                .toList();
    }

    private boolean isMandatoryFocus(ScenarioDomain domain, String key) {
        return domain.focusAreas().stream()
                .anyMatch(f -> f.key().equals(key) && f.mandatory());
    }

    /**
     * 在已有动态任务中查找与模型返回项“同一条”的任务（同标题+关注项+周期+执行日）。
     * 命中返回旧任务，供比对时间/详情是否发生变化；未命中返回 null（视为新增）。
     */
    private Task findExistingDynamic(List<Task> existingDynamic, LlmPort.TaskItem item,
                                     LocalDate due, String recurrence) {
        String content = item.content() == null ? "" : item.content().trim();
        String focus = item.focusArea() == null ? "" : item.focusArea().trim();
        for (Task t : existingDynamic) {
            String tContent = t.getContent() == null ? "" : t.getContent().trim();
            String tFocus = t.getFocusArea() == null ? "" : t.getFocusArea().trim();
            String tRecurrence = t.getRecurrence() == null ? "" : t.getRecurrence().trim();
            if (tContent.equalsIgnoreCase(content)
                    && tFocus.equals(focus)
                    && tRecurrence.equalsIgnoreCase(recurrence)
                    && java.util.Objects.equals(t.getDueDate(), due)) {
                return t;
            }
        }
        return null;
    }

    private String normalizeTime(String raw) {
        if (raw == null) return null;
        java.time.LocalTime t = Task.parseRemindTime(raw);
        return t == null ? null : t.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
    }

    /** 关闭关注项时，清理该关注项下由对话生成的动态任务（moduleKey=null）。 */
    private int cleanupDisabledFocusTasks(SubSession sub, java.util.Set<String> removedFocus) {
        if (removedFocus == null || removedFocus.isEmpty()) return 0;
        int count = 0;
        for (Task t : taskRepository.findBySubSessionId(sub.getId())) {
            if (t.getModuleKey() != null) continue;
            if (t.getFocusArea() != null && removedFocus.contains(t.getFocusArea())) {
                taskRepository.archiveAndDelete(t, sub.getUserId(), "FOCUS_AREA_DISABLED");
                count++;
            }
        }
        return count;
    }

    private void upsertMemoryAttributes(SubSession sub, List<Attribute> attributes) {
        if (attributes == null || attributes.isEmpty()) return;

        List<Long> memoryIds = relRepository.findBySubSessionId(sub.getId()).stream()
                .map(MemorySessionRel::getMemoryId)
                .toList();
        List<UserMemory> memories = userMemoryRepository.findByIdIn(memoryIds);

        for (Attribute attribute : attributes) {
            if (attribute == null || attribute.getType() == null || attribute.getType().isBlank()) continue;
            boolean merged = false;
            for (UserMemory memory : memories) {
                boolean sameType = memory.getAttributes().stream()
                        .anyMatch(a -> attribute.getType().equals(a.getType()));
                if (!sameType) continue;

                List<Attribute> replaced = new ArrayList<>();
                for (Attribute existing : memory.getAttributes()) {
                    replaced.add(attribute.getType().equals(existing.getType())
                            ? mergeAttribute(existing, attribute) : existing);
                }
                userMemoryRepository.save(new UserMemory(memory.getId(), memory.getUserId(), memory.getCategory(),
                        memory.getContent(), memory.getSubject(), memory.getSubjectProfile(), memory.getEventDate(),
                        memory.getValidFrom(), memory.getValidTo(), memory.getLocation(), memory.getConfidence(),
                        replaced, memory.getSourceRawLogId(), memory.getCreatedAt()));
                merged = true;
                break;
            }

            if (!merged) {
                UserMemory saved = userMemoryRepository.save(new UserMemory(
                        null, sub.getUserId(), MemoryCategory.USER_INFO,
                        "目标信息更新：" + attribute.getType(), "self", null, null, null, null, null, 1.0,
                        List.of(attribute), null, Instant.now()));
                if (!relRepository.existsByMemoryIdAndSubSessionId(saved.getId(), sub.getId())) {
                    relRepository.save(new MemorySessionRel(null, saved.getId(), sub.getId(), Instant.now()));
                }
            }
        }
    }

    private Attribute mergeAttribute(Attribute existing, Attribute patch) {
        Map<String, Object> base = objectMapper.convertValue(existing, new TypeReference<>() {});
        Map<String, Object> updates = objectMapper.convertValue(patch, new TypeReference<>() {});
        updates.values().removeIf(Objects::isNull);
        base.putAll(updates);
        base.put("type", patch.getType());
        return objectMapper.convertValue(base, Attribute.class);
    }

    private String buildFocusContext(ScenarioDomain domain, List<String> effectiveFocus) {
        if (domain == null || effectiveFocus == null || effectiveFocus.isEmpty()) return "";
        Map<String, String> labelByKey = new LinkedHashMap<>();
        for (ScenarioDomain.FocusArea f : domain.focusAreas()) labelByKey.put(f.key(), f.label());
        List<String> parts = new ArrayList<>();
        for (String key : effectiveFocus) {
            String label = labelByKey.get(key);
            parts.add(key + (label == null ? "" : "=" + label));
        }
        return String.join("、", parts);
    }

    private void adjustDynamicTasks(SubSession sub, Long subSessionId, String newMessage) {
        adjustDynamicTasks(sub, subSessionId, newMessage, null, List.of());
    }

    private void adjustDynamicTasks(SubSession sub, Long subSessionId, String newMessage,
                                    ScenarioDomain domain, List<String> effectiveFocus) {
        List<LlmPort.TaskItem> planned = planDynamicTasks(sub, subSessionId, newMessage, domain, effectiveFocus);
        replaceDynamicTasks(subSessionId, planned);
    }

    /** 调用模型规划动态任务（不落库），供确认弹窗预览与确认后复用。 */
    private List<LlmPort.TaskItem> planDynamicTasks(SubSession sub, Long subSessionId, String newMessage,
                                                   ScenarioDomain domain, List<String> effectiveFocus) {
        List<Task> existing = taskRepository.findBySubSessionId(subSessionId);
        List<Task> dynamicTasks = existing.stream().filter(t -> t.getModuleKey() == null).toList();

        List<LlmPort.TaskItem> current = dynamicTasks.stream()
                .map(t -> new LlmPort.TaskItem(t.getContent(),
                        t.getDueDate() == null ? "" : t.getDueDate().toString(),
                        t.getFocusArea() == null ? "" : t.getFocusArea(),
                        t.getDetail() == null ? "" : t.getDetail(),
                        t.getRecurrence() == null ? "" : t.getRecurrence()))
                .toList();
        String collected = sub.getCollectedInfo() == null ? "" : sub.getCollectedInfo();
        String focusContext = buildFocusContext(domain, effectiveFocus);
        String messageWithContext = newMessage
                + (focusContext.isBlank() ? "" : "\n【可分配的关注项(key=名称)】\n" + focusContext)
                + (collected.isBlank() ? "" : "\n【已收集信息】\n" + collected);
        try {
            LlmPort.AdjustTasksResult result = llmPort.adjustTasks(sub.getSessionDesc(), current, messageWithContext);
            return result.tasks() == null ? List.of() : result.tasks();
        } catch (Exception e) {
            log.warn("动态任务调整失败 sub={} err={}", subSessionId, e.getMessage());
            return List.of();
        }
    }

    private void replaceDynamicTasks(Long subSessionId, List<LlmPort.TaskItem> items) {
        List<Task> dynamicTasks = taskRepository.findBySubSessionId(subSessionId).stream()
                .filter(t -> t.getModuleKey() == null).toList();
        for (Task task : dynamicTasks) taskRepository.delete(task);
        if (items == null) return;
        for (LlmPort.TaskItem item : items) {
            LocalDate due = Task.parseDueDate(item.dueDate());
            String recurrence = item.recurrence() == null ? "" : item.recurrence().trim();
            java.time.LocalTime remindTime = Task.parseRemindTime(item.remindTime());
            taskRepository.save(Task.createScheduled(subSessionId, item.content(), item.detail(),
                    null, null, item.focusArea(), due, due, null,
                    recurrence.isBlank() ? null : recurrence, remindTime));
        }
    }

    private void markCompletedByKeywords(Long subSessionId, List<String> keywords) {
        for (Task task : taskRepository.findBySubSessionId(subSessionId)) {
            if (task.isCompleted()) continue;
            for (String keyword : keywords) {
                if (keyword != null && !keyword.isBlank() && task.getContent().contains(keyword.trim())) {
                    task.markCompleted();
                    taskRepository.save(task);
                    rawChatLogRepository.save(new RawChatLog(null, null, SessionType.SUB, subSessionId,
                            "assistant", "✅ 已完成：" + task.getContent(), null, Instant.now()));
                    break;
                }
            }
        }
    }
}
