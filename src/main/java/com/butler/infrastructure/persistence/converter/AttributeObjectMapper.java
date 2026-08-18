package com.butler.infrastructure.persistence.converter;

import com.butler.domain.attribute.Attribute;
import com.butler.domain.scenario.ScenarioDomain;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.NamedType;

/** 共享的 Attribute 序列化 Mapper：在启动时由 Spring 注册各场景的强类型子类。 */
public final class AttributeObjectMapper {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private AttributeObjectMapper() {}

    public static ObjectMapper get() {
        return MAPPER;
    }

    public static void register(Iterable<? extends ScenarioDomain> domains) {
        for (ScenarioDomain domain : domains) {
            for (Class<? extends Attribute> clazz : domain.attributeClasses()) {
                try {
                    String name = clazz.getDeclaredConstructor().newInstance().getType();
                    MAPPER.registerSubtypes(new NamedType(clazz, name));
                } catch (Exception e) {
                    throw new IllegalStateException("Attribute 类需提供 public 无参构造: " + clazz, e);
                }
            }
        }
    }
}
