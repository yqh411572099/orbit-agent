package com.butler.infrastructure.persistence.converter;

import com.butler.domain.attribute.Attribute;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.List;

@Converter
public class AttributeListConverter implements AttributeConverter<List<Attribute>, String> {

    private static final ObjectMapper MAPPER = AttributeObjectMapper.get();
    private static final TypeReference<List<Attribute>> TYPE = new TypeReference<>() {};

    @Override
    public String convertToDatabaseColumn(List<Attribute> attributes) {
        if (attributes == null || attributes.isEmpty()) return null;
        try {
            return MAPPER.writeValueAsString(attributes);
        } catch (Exception e) {
            throw new IllegalStateException("序列化 attribute 失败", e);
        }
    }

    @Override
    public List<Attribute> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) return List.of();
        try {
            return MAPPER.readValue(dbData, TYPE);
        } catch (Exception e) {
            throw new IllegalStateException("反序列化 attribute 失败: " + dbData, e);
        }
    }
}
