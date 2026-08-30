package com.butler.domain.scenario.builtin;

import com.butler.domain.attribute.Attribute;
import com.butler.domain.model.Task;
import com.butler.domain.scenario.ScenarioDomain;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 通用计划场景：不内置固定字段/关注项/时间轴。
 * 由 LLM 基于用户诉求自行分析目标拆解、关键参数与分块关注项，确认后创建，
 * 后续任务/记忆沿用通用的动态任务与结构化属性（extras 透传）机制。
 */
@Component
public class GenericPlanScenario implements ScenarioDomain {

    @Override
    public String type() { return "generic"; }

    @Override
    public String displayName() { return "通用计划"; }

    @Override
    public String description() {
        return "告诉我你想做什么（健身、旅行、学一项技能、筹办活动…），我来分析拆解并制定专属跟进计划。";
    }

    @Override
    public String defaultSessionDesc() {
        return "用户的一项自定义长期目标，由 AI 根据用户诉求拆解关键参数、关注方面与阶段任务并持续跟进。";
    }

    @Override
    public List<CollectField> collectFields() {
        // 不预设固定字段：目标诉求就是种子，创建前由模型分析产出定制参数。
        return List.of();
    }

    @Override
    public List<Attribute> attributesFromCollected(java.util.Map<String, String> collected) {
        return List.of();
    }

    @Override
    public List<String> initialTasks(String goal) {
        return List.of("明确目标与衡量标准", "拆解关键阶段", "安排近期第一步");
    }

    @Override
    public boolean researchBeforeCreate() { return true; }

    @Override
    public String researchBrief() {
        return """
                这是一个没有固定模板的通用目标，请基于用户诉求做规划，不要套用考研/孕期/考证等专门模板：
                1. 用一句话概括目标（title 简短、goalText 说清想要的结果与大致时间）；
                2. 在 collected 中提炼可量化/可追踪的关键参数（如目标日期、频率、地点、人物角色、预算、数量），key 用英文驼峰；
                3. focusAreas 给出 2-6 个需要分块跟进的“方面”（例如健身计划：训练、饮食、作息、测量记录）；
                4. sections 用中文分 2-5 组展示：目标拆解、关键参数、阶段里程碑/时间节点、注意事项；
                5. 能从诉求里确定的日期就给出 yyyy-MM-dd；不确定的不要编，uncertain=true 标注待用户确认；
                6. 不要编造外部链接，materials 给空数组。""";
    }

}
