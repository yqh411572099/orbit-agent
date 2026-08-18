package com.butler.infrastructure.poi;

import com.butler.domain.service.PoiSearchPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PoiConfig {

    @Bean
    public PoiSearchPort poiSearchPort(ObjectMapper mapper,
                                       @Value("${amap.key:}") String amapKey) {
        PoiSearchPort amap = new AmapPoiSearchAdapter(mapper, amapKey == null ? "" : amapKey.trim());
        PoiSearchPort overpass = new OverpassPoiSearchAdapter(mapper);
        return new CompositePoiSearchAdapter(List.of(amap, overpass));
    }
}
