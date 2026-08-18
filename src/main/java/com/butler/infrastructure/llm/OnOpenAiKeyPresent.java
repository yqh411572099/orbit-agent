package com.butler.infrastructure.llm;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

public class OnOpenAiKeyPresent implements Condition {
    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        String key = context.getEnvironment().getProperty("spring.ai.openai.api-key", "");
        return key != null && !key.isBlank();
    }
}
