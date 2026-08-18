package com.butler.infrastructure.llm;

import com.butler.domain.attribute.Attribute;
import com.butler.domain.scenario.ScenarioRegistry;
import com.butler.infrastructure.persistence.converter.AttributeObjectMapper;
import com.fasterxml.jackson.databind.jsontype.NamedType;
import com.fasterxml.jackson.databind.module.SimpleModule;
import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 把各场景域的强类型 Attribute 注册到：
 * 1) Spring 容器内的 ObjectMapper（LLM 提炼解析使用）；
 * 2) 静态 AttributeObjectMapper（JPA JSON 转换器使用）。
 */
@Configuration
public class AttributeJacksonConfig {

    private final ScenarioRegistry scenarioRegistry;

    public AttributeJacksonConfig(ScenarioRegistry scenarioRegistry) {
        this.scenarioRegistry = scenarioRegistry;
    }

    @PostConstruct
    void initStatic() {
        AttributeObjectMapper.register(scenarioRegistry.all());
    }

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer attributeSubtypeCustomizer() {
        return builder -> {
            List<NamedType> types = new ArrayList<>();
            for (var domain : scenarioRegistry.all()) {
                for (Class<? extends Attribute> clazz : domain.attributeClasses()) {
                    try {
                        String name = clazz.getDeclaredConstructor().newInstance().getType();
                        types.add(new NamedType(clazz, name));
                    } catch (Exception e) {
                        throw new IllegalStateException("Attribute 类需提供 public 无参构造: " + clazz, e);
                    }
                }
            }
            if (!types.isEmpty()) {
                SimpleModule module = new SimpleModule("attributeSubtypes");
                module.registerSubtypes(types.toArray(new NamedType[0]));
                builder.modulesToInstall(module);
            }
        };
    }
}
