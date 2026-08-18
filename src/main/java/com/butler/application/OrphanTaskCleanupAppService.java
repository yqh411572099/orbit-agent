package com.butler.application;

import com.butler.domain.model.SubSession;
import com.butler.domain.model.Task;
import com.butler.domain.repository.SubSessionRepository;
import com.butler.domain.repository.TaskRepository;
import com.butler.domain.scenario.ScenarioDomain;
import com.butler.domain.scenario.ScenarioRegistry;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 清理“关注项已取消，但任务仍残留”的孤儿任务。
 * 对每个子对话，按当前生效关注项集合比对：任务 focusArea 不在集合内的（含对话生成的动态任务）一律归档删除。
 */
@Service
public class OrphanTaskCleanupAppService {

    private final SubSessionRepository subSessionRepository;
    private final TaskRepository taskRepository;
    private final ScenarioRegistry scenarioRegistry;

    public OrphanTaskCleanupAppService(SubSessionRepository subSessionRepository,
                                       TaskRepository taskRepository,
                                       ScenarioRegistry scenarioRegistry) {
        this.subSessionRepository = subSessionRepository;
        this.taskRepository = taskRepository;
        this.scenarioRegistry = scenarioRegistry;
    }

    /** 预览：返回将被清理的任务标题（按子对话分组），不删除。 */
    @Transactional
    public List<OrphanView> preview(Long userId) {
        return run(userId, false);
    }

    @Transactional
    public List<OrphanView> cleanup(Long userId) {
        return run(userId, true);
    }

    private List<OrphanView> run(Long userId, boolean apply) {
        List<OrphanView> result = new ArrayList<>();
        for (SubSession sub : subSessionRepository.findByUserId(userId)) {
            if (!scenarioRegistry.supports(sub.getScenarioType())) continue;
            ScenarioDomain domain = scenarioRegistry.get(sub.getScenarioType());
            ScenarioStateSupport.ScenarioState state = ScenarioStateSupport.parse(domain, sub.getCollectedInfo(), com.butler.application.CustomFocusLabels.read(sub));
            Set<String> effective = new LinkedHashSet<>(ScenarioDomain.resolveEffectiveFocusAreas(
                    domain.focusAreas(), state.focusAreas()));
            List<String> removed = new ArrayList<>();
            for (Task t : taskRepository.findBySubSessionId(sub.getId())) {
                String fa = t.getFocusArea();
                if (fa == null || fa.isBlank()) continue;
                if (effective.contains(fa)) continue;
                removed.add(t.getContent() + " [" + fa + "]");
                if (apply) {
                    taskRepository.archiveAndDelete(t, userId, "CLEANUP_ORPHAN_TASK");
                }
            }
            if (!removed.isEmpty()) {
                result.add(new OrphanView(sub.getId(), sub.getScenarioType(), removed));
            }
        }
        return result;
    }

    public record OrphanView(Long subSessionId, String scenarioType, List<String> tasks) {}
}
