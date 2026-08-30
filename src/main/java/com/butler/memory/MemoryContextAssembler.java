package com.butler.memory;

import com.butler.domain.attribute.AttributeRenderer;
import com.butler.application.MetricAppService;
import com.butler.application.ScenarioStateSupport;
import com.butler.domain.model.*;
import com.butler.domain.repository.MissionRepository;
import com.butler.domain.repository.SubSessionRepository;
import com.butler.domain.repository.TaskRepository;
import com.butler.domain.scenario.ScenarioDomain;
import com.butler.domain.scenario.ScenarioRegistry;
import com.butler.domain.service.MemoryPermissionService;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * 对话上下文组装器：把“记忆 + 子任务目标 + 收集信息 + 待办任务”按结构拼成 system prompt。
 *
 * <p>主对话上下文只包含全局长期记忆（按分类分组）。
 * 子对话上下文额外包含：当前子任务目标、该子任务收集的用户信息、待办定时任务，以及绑定到该子对话的记忆。</p>
 */
@Service
public class MemoryContextAssembler {

    private final MemoryPermissionService permissionService;
    private final SubSessionRepository subSessionRepository;
    private final TaskRepository taskRepository;
    private final MissionRepository missionRepository;
    private final ScenarioRegistry scenarioRegistry;
    private final MetricAppService metricAppService;

    public MemoryContextAssembler(MemoryPermissionService permissionService,
                                  SubSessionRepository subSessionRepository,
                                  TaskRepository taskRepository,
                                  MissionRepository missionRepository,
                                  ScenarioRegistry scenarioRegistry,
                                  MetricAppService metricAppService) {
        this.permissionService = permissionService;
        this.subSessionRepository = subSessionRepository;
        this.taskRepository = taskRepository;
        this.missionRepository = missionRepository;
        this.scenarioRegistry = scenarioRegistry;
        this.metricAppService = metricAppService;
    }

    public String assemble(Long userId, SessionType type, Long subSessionId) {
        if (type == SessionType.SUB && subSessionId != null) {
            return assembleSubSession(userId, subSessionId);
        }
        return assembleMain(userId);
    }

    private String assembleMain(Long userId) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是用户的长期智能管家「主对话」。你可以闲聊、介绍系统支持的技能、帮助创建新目标、回顾所有事项。\n");
        appendMemories(sb, permissionService.readableMemories(userId, SessionType.MAIN, null));
        return sb.toString();
    }

    private String assembleSubSession(Long userId, Long subSessionId) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是用户某一长期目标的专属助手，专注处理该单一事项。\n");

        SubSession sub = subSessionRepository.findById(subSessionId).orElse(null);
        if (sub != null) {
            String goalTitle = missionRepository.findById(sub.getMissionId())
                    .map(Mission::getTitle).orElse("");
            sb.append("\n【当前子任务目标】\n");
            if (!goalTitle.isBlank()) {
                sb.append("- 目标：").append(goalTitle).append("\n");
            }
            if (sub.getSessionDesc() != null && !sub.getSessionDesc().isBlank()) {
                sb.append("- 场景描述：").append(sub.getSessionDesc()).append("\n");
            }
            if (sub.getCollectedInfo() != null && !sub.getCollectedInfo().isBlank()) {
                String visible = visibleCollected(sub);
                if (visible != null && !visible.isBlank()) {
                    sb.append("\n【本子任务收集的用户信息】\n").append(visible).append("\n");
                }
            }

            // 已确认的结构化指标当前值（图表/指标卡数据，用户确认后才写入）。
            // 这是“当前事实”的权威来源；用户当轮新报、尚未确认的值不在此列，不能当作当前值。
            try {
                List<MetricAppService.Point> points = metricAppService.latest(subSessionId);
                List<String> confirmed = points.stream()
                        .filter(p -> p.value() != null)
                        .map(p -> "- " + p.label() + "：" + p.value()
                                + (p.unit() == null || p.unit().isBlank() ? "" : " " + p.unit())
                                + (p.date() == null ? "" : "（" + p.date() + "）"))
                        .toList();
                if (!confirmed.isEmpty()) {
                    sb.append("\n【已确认的当前指标值（以此为准，用户当轮新报但未确认的数值不算）】\n");
                    confirmed.forEach(c -> sb.append(c).append("\n"));
                }
            } catch (Exception ignored) {
                // 指标读取失败不阻断对话
            }
        }

        List<Task> tasks = taskRepository.findBySubSessionId(subSessionId).stream()
                .filter(t -> !t.isCompleted())
                .toList();
        if (!tasks.isEmpty()) {
            sb.append("\n【待办/定时任务】\n");
            for (Task t : tasks) {
                sb.append("- ").append(t.getContent());
                if (t.getDueDate() != null) {
                    sb.append("（到期日：").append(t.getDueDate()).append("）");
                }
                sb.append("\n");
            }
            sb.append("请在对话中适时提醒用户这些待办，并根据用户反馈更新或完成它们。\n");
        }

        appendMemories(sb, permissionService.readableMemories(userId, SessionType.SUB, subSessionId));
        return sb.toString();
    }

    private String visibleCollected(SubSession sub) {
        if (!scenarioRegistry.supports(sub.getScenarioType())) return sub.getCollectedInfo();
        ScenarioDomain domain = scenarioRegistry.get(sub.getScenarioType());
        Map<String,String> customLabels = com.butler.application.CustomFocusLabels.read(sub);
        ScenarioStateSupport.ScenarioState state = ScenarioStateSupport.parse(domain, sub.getCollectedInfo(), customLabels);
        return ScenarioStateSupport.renderVisible(domain, state.collected(), state.focusAreas(), customLabels);
    }

    private void appendMemories(StringBuilder sb, List<UserMemory> memories) {
        if (memories == null || memories.isEmpty()) {
            return;
        }
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
        Map<MemoryCategory, List<UserMemory>> grouped = new LinkedHashMap<>();
        for (MemoryCategory c : MemoryCategory.values()) {
            grouped.put(c, new java.util.ArrayList<>());
        }
        for (UserMemory m : memories) {
            if (!m.isValidOn(today)) continue;
            grouped.get(m.getCategory()).add(m);
        }
        sb.append("\n【长期记忆】（已按主体/时间/地点结构化；过期的临时上下文已自动忽略）\n");
        for (Map.Entry<MemoryCategory, List<UserMemory>> e : grouped.entrySet()) {
            if (e.getValue().isEmpty()) continue;
            sb.append("- ").append(e.getKey().getLabel()).append("：\n");
            for (UserMemory m : e.getValue()) {
                sb.append("  · ").append(m.getContent());
                StringBuilder meta = new StringBuilder();
                if (m.getSubject() != null) meta.append(" 主体=").append(m.getSubject());
                if (m.getSubjectProfile() != null && !m.getSubjectProfile().isBlank())
                    meta.append(" 主体画像=").append(m.getSubjectProfile());
                if (m.getEventDate() != null) meta.append(" 时间=").append(m.getEventDate());
                if (m.getLocation() != null) meta.append(" 地点=").append(m.getLocation());
                if (m.getValidTo() != null) meta.append(" 有效期至=").append(m.getValidTo());
                String attrs = AttributeRenderer.toText(m.getAttributes());
                if (!attrs.isBlank()) meta.append(" ").append(attrs);
                sb.append(meta).append("\n");
            }
        }
    }
}
