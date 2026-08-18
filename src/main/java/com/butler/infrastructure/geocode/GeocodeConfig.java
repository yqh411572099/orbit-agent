package com.butler.infrastructure.geocode;

import com.butler.domain.service.GeocodePort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GeocodeConfig {

    @Bean
    public GeocodePort geocodePort(ObjectMapper mapper, @Value("${amap.key:}") String amapKey) {
        return new WebGeocodeAdapter(mapper, amapKey);
    }
}
