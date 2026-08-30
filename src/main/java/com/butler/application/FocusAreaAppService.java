package com.butler.application;

import com.butler.domain.model.SubSession;
import com.butler.domain.model.Task;
import com.butler.domain.repository.SubSessionRepository;
import com.butler.domain.repository.TaskRepository;
import com.butler.domain.scenario.ScenarioDomain;
import com.butler.domain.scenario.ScenarioRegistry;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FocusAreaAppService {
    private final SubSessionRepository subSessionRepository;
    private final ScenarioRegistry scenarioRegistry;
    private final TimelineAppService timelineAppService;
    private final TaskRepository taskRepository;

    public FocusAreaAppService(SubSessionRepository subSessionRepository,
                               ScenarioRegistry scenarioRegistry,
                               TimelineAppService timelineAppService,
                               TaskRepository taskRepository) {
        this.subSessionRepository = subSessionRepository;
        this.scenarioRegistry = scenarioRegistry;
        this.timelineAppService = timelineAppService;
        this.taskRepository = taskRepository;
    }

    @Transactional
    public String updateFocusAreas(Long subSessionId, List<String> requested) {
        SubSession sub = subSessionRepository.findById(subSessionId)
                .orElseThrow(() -> new IllegalArgumentException("子对话不存在: " + subSessionId));
        if (!scenarioRegistry.supports(sub.getScenarioType())) return "ok";

        ScenarioDomain domain = scenarioRegistry.get(sub.getScenarioType());
        Map<String, String> customLabels = CustomFocusLabels.read(sub);
        ScenarioStateSupport.ScenarioState state = ScenarioStateSupport.parse(domain, sub.getCollectedInfo(), customLabels);

        Set<String> before = new LinkedHashSet<>(ScenarioDomain.resolveEffectiveFocusAreas(
                domain.focusAreas(), state.focusAreas()));
        List<String> effective = ScenarioDomain.resolveEffectiveFocusAreas(
                domain.focusAreas(), requested == null ? List.of() : requested);
        Set<String> removed = new LinkedHashSet<>(before);
        effective.forEach(removed::remove);

        sub.setCollectedInfo(ScenarioStateSupport.render(domain, state.collected(), effective, customLabels));
        subSessionRepository.save(sub);

        // resync 会清理内置时间轴任务；动态任务（moduleKey=null，由对话生成）需这里单独清理。
        int dynamicRemoved = 0;
        if (!removed.isEmpty()) {
            for (Task t : taskRepository.findBySubSessionId(subSessionId)) {
                if (t.getModuleKey() != null) continue;
                if (t.getFocusArea() != null && removed.contains(t.getFocusArea())) {
                    taskRepository.archiveAndDelete(t, sub.getUserId(), "FOCUS_AREA_DISABLED");
                    dynamicRemoved++;
                }
            }
        }

        String note = timelineAppService.resync(sub, domain, state.collected(), effective, "已更新关注项");
        if (dynamicRemoved > 0) {
            note = (note == null || note.isBlank() ? "" : note)
                    + "已移除 " + dynamicRemoved + " 项该关注项的待办。";
        }
        return note;
    }

    /**
     * 写入自定义关注项 key→中文label，并重新渲染 collectedInfo（不触发时间轴变动）。
     * value 为 null/空白时表示删除该 key（用于清理脏 key）。
     */
    @Transactional
    public void putCustomLabels(Long subSessionId, Map<String, String> labels) {
        if (labels == null || labels.isEmpty()) return;
        SubSession sub = subSessionRepository.findById(subSessionId)
                .orElseThrow(() -> new IllegalArgumentException("子对话不存在: " + subSessionId));
        if (!scenarioRegistry.supports(sub.getScenarioType())) return;
        ScenarioDomain domain = scenarioRegistry.get(sub.getScenarioType());
        Map<String, String> customLabels = CustomFocusLabels.read(sub);
        labels.forEach((k, v) -> {
            if (k == null || k.isBlank()) return;
            if (v == null || v.isBlank()) customLabels.remove(k);
            else customLabels.put(k, v);
        });
        CustomFocusLabels.write(sub, customLabels);
        ScenarioStateSupport.ScenarioState state =
                ScenarioStateSupport.parse(domain, sub.getCollectedInfo(), customLabels);
        sub.setCollectedInfo(ScenarioStateSupport.render(domain, state.collected(), state.focusAreas(), customLabels));
        subSessionRepository.save(sub);
    }
}
