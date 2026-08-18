package com.butler.infrastructure.auth;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AuthConfig {

    @Bean
    public SessionTokenService sessionTokenService(
            @Value("${butler.session.secret:butler-dev-secret-change-me}") String secret,
            @Value("${butler.session.ttl-hours:24}") long ttlHours) {
        return new SessionTokenService(secret, Duration.ofHours(ttlHours));
    }
}
