package com.butler.domain.attribute;

import java.util.List;

/**
 * 描述一种可被 LLM 提炼的属性类型，作为场景的“属性目录”喂给模型。
 *
 * @param type        属性类型标识，需与序列化 JSON 里的 type 一致
 * @param description 一句话说明何时提炼该属性
 * @param fields      字段 schema（name/schemaType/required/description），指导模型输出
 */
public record AttributeDescriptor(String type, String description, List<FieldSpec> fields) {

    public record FieldSpec(String name, String schemaType, boolean required, String description) {}
}
