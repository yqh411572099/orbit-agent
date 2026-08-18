package com.butler.perception.api.dto;

import jakarta.validation.constraints.NotBlank;

public record ChatRequest(
        Long userId,
        @NotBlank String sessionType,
        Long subSessionId,
        @NotBlank String message
) {}
