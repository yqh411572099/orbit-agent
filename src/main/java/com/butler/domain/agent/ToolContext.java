package com.butler.domain.agent;

import java.time.LocalDate;
import java.util.Map;

/** 一次对话回合的运行时上下文，供工具取用用户/子对话信息（坐标、已收集字段等）。 */
public record ToolContext(
        Long userId,
        String sessionType,
        Long subSessionId,
        String scenarioType,
        Map<String, String> collected,
        LocalDate today,
        boolean researchOnly) {
    public ToolContext(Long userId, String sessionType, Long subSessionId, String scenarioType,
                       Map<String, String> collected, LocalDate today) {
        this(userId, sessionType, subSessionId, scenarioType, collected, today, false);
    }

    public String collected(String key) {
        return collected == null ? null : collected.get(key);
    }
}
