package com.butler.infrastructure.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.autoconfigure.openai.OpenAiAutoConfiguration;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;

@Configuration
public class LlmConfig {

    @Configuration
    @Conditional(OnOpenAiKeyPresent.class)
    @Import(OpenAiAutoConfiguration.class)
    static class SpringAiEnabledConfig {
    }

    @Bean
    public LlmPort llmPort(Environment env,
                           ObjectProvider<ChatClient.Builder> builderProvider,
                           ObjectMapper mapper) {
        String apiKey = env.getProperty("spring.ai.openai.api-key", "");
        String baseUrl = env.getProperty("spring.ai.openai.base-url", "");
        String model = env.getProperty("spring.ai.openai.chat.options.model", "");
        String lightModel = env.getProperty("spring.ai.openai.chat.options.light-model", "");
        ChatClient.Builder builder = builderProvider.getIfAvailable();
        if (apiKey != null && !apiKey.isBlank() && builder != null) {
            return new SpringAiLlmAdapter(builder, mapper, apiKey, baseUrl, model, lightModel);
        }
        return new NoopLlmAdapter();
    }
}
