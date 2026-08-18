package com.butler.domain.scenario;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class ScenarioRegistry {

    private final Map<String, ScenarioDomain> domains;

    public ScenarioRegistry(List<ScenarioDomain> domains) {
        this.domains = domains.stream()
                .collect(Collectors.toMap(ScenarioDomain::type, Function.identity()));
    }

    public ScenarioDomain get(String type) {
        ScenarioDomain domain = domains.get(type);
        if (domain == null) {
            throw new IllegalArgumentException("未注册的场景类型: " + type);
        }
        return domain;
    }

    public boolean supports(String type) {
        return domains.containsKey(type);
    }

    public List<ScenarioDomain> all() {
        return domains.values().stream()
                .sorted(Comparator.comparing(ScenarioDomain::type))
                .toList();
    }
}
