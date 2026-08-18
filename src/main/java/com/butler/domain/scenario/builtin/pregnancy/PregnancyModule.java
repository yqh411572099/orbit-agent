package com.butler.domain.scenario.builtin.pregnancy;

import com.butler.domain.scenario.ScenarioDomain.Audience;
import java.util.List;

/**
 * 孕期时间轴上的一个“模块/泳道”。
 *
 * <p>刚性模块（mandatory=true，如孕检、建档）无论用户是否关注都生成；
 * 可选模块（如待产包、月嫂）仅在用户启用对应 focusArea 时叠加到时间轴。</p>
 */
public record PregnancyModule(
        String key,
        String name,
        Audience owner,
        boolean mandatory,
        String focusArea,
        String description,
        List<Milestone> milestones
) {
    /**
     * @param title       待办标题
     * @param detail      提醒时附带的知识/准备事项
     * @param dueWeek     执行/截止孕周（主任务日期锚点）
     * @param leadWeeks   提前多少周生成“开始准备”任务；0 表示不生成前置任务
     * @param nextHint    完成后给用户的下一步提示；为空表示无自动推进提示
     */
    public record Milestone(
            String key,
            String title,
            String detail,
            int dueWeek,
            int leadWeeks,
            String nextHint
    ) {
        public Milestone(String title, String detail, int dueWeek, int leadWeeks, String nextHint) {
            this(null, title, detail, dueWeek, leadWeeks, nextHint);
        }
    }
}
