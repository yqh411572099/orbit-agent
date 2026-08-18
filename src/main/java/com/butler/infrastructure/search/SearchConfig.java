package com.butler.infrastructure.search;

import com.butler.domain.service.WebSearchPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SearchConfig {
    @Bean
    public WebSearchPort webSearchPort(ObjectMapper mapper, @Value("${search.api-key:}") String apiKey) {
        return new DoubaoWebSearchAdapter(mapper, apiKey);
    }
}
