package com.butler.application;

import com.butler.domain.model.*;
import com.butler.domain.model.MemorySessionRel;
import com.butler.domain.model.MemoryCategory;
import com.butler.domain.repository.*;
import com.butler.domain.scenario.ScenarioDomain;
import com.butler.domain.scenario.ScenarioRegistry;
import com.butler.infrastructure.llm.LlmPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GoalAppService {

    private final MissionRepository missionRepository;
    private final SubSessionRepository subSessionRepository;
    private final TaskRepository taskRepository;
    private final MainSessionRepository mainSessionRepository;
    private final ScenarioRegistry scenarioRegistry;
    private final LlmPort llmPort;
    private final UserMemoryRepository userMemoryRepository;
    private final MemorySessionRelRepository relRepository;
    private final ObjectMapper objectMapper;

    public GoalAppService(MissionRepository missionRepository,
                          SubSessionRepository subSessionRepository,
                          TaskRepository taskRepository,
                          MainSessionRepository mainSessionRepository,
                          ScenarioRegistry scenarioRegistry,
                          LlmPort llmPort,
                          UserMemoryRepository userMemoryRepository,
                          MemorySessionRelRepository relRepository,
                          ObjectMapper objectMapper) {
        this.missionRepository = missionRepository;
        this.subSessionRepository = subSessionRepository;
        this.taskRepository = taskRepository;
        this.mainSessionRepository = mainSessionRepository;
        this.scenarioRegistry = scenarioRegistry;
        this.llmPort = llmPort;
        this.userMemoryRepository = userMemoryRepository;
        this.relRepository = relRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public SubSession createGoal(Long userId, String scenarioType, String title, String goal) {
        return createGoal(userId, scenarioType, title, goal, Map.of(), List.of());
    }

    @Transactional
    public SubSession createGoal(Long userId, String scenarioType, String title, String goal,
                                 Map<String, String> collected) {
        return createGoal(userId, scenarioType, title, goal, collected, List.of());
    }

    @Transactional
    public SubSession createGoal(Long userId, String scenarioType, String title, String goal,
                                 Map<String, String> collected, List<String> focusAreas) {
        return createGoal(userId, scenarioType, title, goal, collected, focusAreas, Map.of());
    }

    @Transactional
    public SubSession createGoal(Long userId, String scenarioType, String title, String goal,
                                 Map<String, String> collected, List<String> focusAreas,
                                 Map<String, String> focusLabels) {
        ScenarioDomain domain = scenarioRegistry.get(scenarioType);
        ensureMainSession(userId);

        LocalDate today = LocalDate.now(java.time.ZoneId.of("Asia/Shanghai"));
        collected = domain.normalizeCollected(goal, collected, today);
        List<String> selectedFocus = resolveFocusAreas(domain, focusAreas);
        String enrichedGoal = enrichGoal(goal, domain, collected, selectedFocus, focusLabels);
        Mission mission = missionRepository.save(
                new Mission(null, userId, title == null || title.isBlank() ? goal : title, scenarioType, Instant.now()));

        LlmPort.CreateGoalResult result = llmPort.createGoal(enrichedGoal, scenarioType);
        String sessionDesc = result.sessionDesc() == null || result.sessionDesc().isBlank()
                ? buildDefaultSessionDesc(domain, collected, selectedFocus) : result.sessionDesc();

        SubSession sub = subSessionRepository.save(new SubSession(
                null, userId, mission.getId(), scenarioType, sessionDesc,
                buildCollectedInfo(domain, collected, selectedFocus),
                SubSessionStatus.ACTIVE, Instant.now()));

        if (result.metricDefs() != null && !result.metricDefs().isEmpty()) {
            try {
                sub.setMetricDefs(objectMapper.writeValueAsString(result.metricDefs().stream()
                        .map(d -> new MetricAppService.Def(d.key(), d.label(), d.unit(), d.chartType()))
                        .toList()));
                sub = subSessionRepository.save(sub);
            } catch (Exception ignored) {}
        }

        List<ScenarioDomain.PlannedTask> planned = domain.plannedTasks(collected, selectedFocus, today);
        if (!planned.isEmpty()) {
            // 场景提供了确定性时间轴（如孕期孕周计划）：优先使用，时间锚点与提前量不依赖 LLM。
            for (ScenarioDomain.PlannedTask pt : planned) {
                taskRepository.save(Task.createScheduled(sub.getId(), pt.title(), pt.detail(),
                        pt.nextHint(), pt.moduleKey(), pt.focusArea(), pt.remindDate(), pt.dueDate(), pt.milestoneKey()));
            }
        } else {
            List<LlmPort.TaskItem> tasks = result.tasks() == null || result.tasks().isEmpty()
                    ? domain.initialTasks(goal).stream().map(c -> new LlmPort.TaskItem(c, "", "")).toList()
                    : result.tasks();
            for (LlmPort.TaskItem t : tasks) {
                LocalDate due = Task.parseDueDate(t.dueDate());
                java.time.LocalTime remindTime = Task.parseRemindTime(t.remindTime());
                String recurrence = t.recurrence() == null ? "" : t.recurrence().trim();
                taskRepository.save(Task.createDynamic(sub.getId(), t.content(),
                        t.detail() == null ? "" : t.detail(), t.focusArea(), due,
                        recurrence.isBlank() ? null : recurrence, remindTime,
                        t.aiBrief() == null ? "" : t.aiBrief().trim()));
            }
        }
        persistCollectedAsMemories(domain, sub, userId, collected);
        return sub;
    }

    /** 把创建时收集的字段和关注项整理成可读文本，作为子任务收集的用户信息持久化。 */
    private String buildCollectedInfo(ScenarioDomain domain, Map<String, String> collected, List<String> selectedFocus) {
        StringBuilder sb = new StringBuilder();
        if (collected != null) {
            for (ScenarioDomain.CollectField f : domain.collectFields()) {
                String v = collected.get(f.key());
                if (v != null && !v.isBlank()) {
                    sb.append(f.label()).append("：").append(v).append("\n");
                }
            }
        }
        if (!selectedFocus.isEmpty()) {
            sb.append("重点关注项：");
            List<String> labels = new ArrayList<>();
            for (String key : selectedFocus) {
                domain.focusAreas().stream().filter(f -> f.key().equals(key)).findFirst()
                        .ifPresent(f -> labels.add(f.label()));
            }
            sb.append(String.join("、", labels)).append("\n");
        }
        return sb.toString().trim();
    }

    /** 未显式选择时，使用场景默认勾选的关注项；始终只保留该场景存在的 key。 */
    private List<String> resolveFocusAreas(ScenarioDomain domain, List<String> focusAreas) {
        if (focusAreas != null && !focusAreas.isEmpty()) {
            return ScenarioDomain.resolveEffectiveFocusAreas(domain.focusAreas(), focusAreas);
        }
        List<String> defaults = domain.focusAreas().stream()
                .filter(ScenarioDomain.FocusArea::defaultSelected)
                .map(ScenarioDomain.FocusArea::key)
                .toList();
        return ScenarioDomain.resolveEffectiveFocusAreas(domain.focusAreas(), defaults);
    }

    private String buildDefaultSessionDesc(ScenarioDomain domain, Map<String, String> collected,
                                           List<String> selectedFocus) {
        StringBuilder sb = new StringBuilder(domain.defaultSessionDesc());
        String role = collected == null ? null : collected.get("role");
        if (role != null && !role.isBlank()) {
            sb.append("当前用户身份：").append(role).append("。");
        }
        if (!selectedFocus.isEmpty()) {
            sb.append("重点关注：");
            List<String> labels = new ArrayList<>();
            for (String key : selectedFocus) {
                domain.focusAreas().stream().filter(f -> f.key().equals(key)).findFirst()
                        .ifPresent(f -> labels.add(f.label()));
            }
            sb.append(String.join("、", labels)).append("。");
        }
        return sb.toString();
    }

    private String enrichGoal(String goal, ScenarioDomain domain, Map<String, String> collected,
                              List<String> selectedFocus, Map<String, String> focusLabels) {
        StringBuilder sb = new StringBuilder(goal == null ? "" : goal);
        if (collected != null) {
            for (ScenarioDomain.CollectField f : domain.collectFields()) {
                String v = collected.get(f.key());
                if (v != null && !v.isBlank()) {
                    sb.append("\n").append(f.label()).append("：").append(v);
                }
            }
        }
        if (!selectedFocus.isEmpty()) {
            sb.append("\n重点关注项：");
            List<String> labels = new ArrayList<>();
            for (String key : selectedFocus) {
                domain.focusAreas().stream().filter(f -> f.key().equals(key)).findFirst()
                        .ifPresent(f -> {
                            labels.add(f.label() + "（" + f.description() + "）");
                        });
            }
            sb.append(String.join("；", labels)).append("。");
            sb.append("\n可用关注项 key 列表（生成任务时请把每项任务归类到最匹配的关注项 key）：");
            for (ScenarioDomain.FocusArea f : domain.focusAreas()) {
                sb.append("\n- ").append(f.key()).append("=").append(f.label());
            }
            if (focusLabels != null) {
                for (String key : selectedFocus) {
                    String label = focusLabels.get(key);
                    if (label != null && !label.isBlank()
                            && domain.focusAreas().stream().noneMatch(f -> f.key().equals(key))) {
                        sb.append("\n- ").append(key).append("=").append(label);
                    }
                }
            }
            sb.append("\n请围绕上述重点关注项生成可执行的初始待办，为每项推断明确的到期日并标注所属关注项 key。");
        }
        return sb.toString();
    }

    /** 把创建目标时收集的“人/时/地/数值”沉淀为结构化长期记忆并绑定到该子对话。 */
    private void persistCollectedAsMemories(ScenarioDomain domain, SubSession sub, Long userId,
                                            Map<String, String> collected) {
        if (collected == null || collected.isEmpty()) return;

        LocalDate eventDate = null;
        String location = null;
        StringBuilder content = new StringBuilder("创建目标时收集的信息：");
        for (ScenarioDomain.CollectField f : domain.collectFields()) {
            String value = collected.get(f.key());
            if (value == null || value.isBlank()) continue;
            content.append(f.label()).append("=").append(value).append("；");
            if (f.semanticType() == ScenarioDomain.SemanticType.TIME) {
                eventDate = Task.parseDueDate(value);
            }
            if (f.semanticType() == ScenarioDomain.SemanticType.LOCATION) {
                location = value;
            }
        }

        UserMemory mem = userMemoryRepository.save(new UserMemory(
                null, userId, MemoryCategory.USER_INFO, content.toString(),
                "self", domain.subjectProfileFromCollected(collected),
                eventDate, null, null, location, 1.0,
                domain.attributesFromCollected(collected), null, Instant.now()));
        relRepository.save(new MemorySessionRel(null, mem.getId(), sub.getId(), Instant.now()));
    }

    private void ensureMainSession(Long userId) {
        mainSessionRepository.findByUserId(userId)
                .orElseGet(() -> mainSessionRepository.save(new MainSession(null, userId, Instant.now())));
    }
}
