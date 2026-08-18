package com.butler.perception.api.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;

public record CreateGoalRequest(
        Long userId,
        @NotBlank String scenarioType,
        String title,
        @NotBlank String goal,
        Map<String, String> collected,
        List<String> focusAreas
) {}
