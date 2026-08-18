package com.butler.domain.attribute;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 结构化属性的抽象基类。
 *
 * <p>设计目标：通用字段保持精简，场景特有结构通过“域自定义子类”表达确定性；
 * LLM 提炼出的未定义字段/未知类型则通过 extras 透传，保证扩展性。</p>
 *
 * <p>序列化时以 {@code type} 字段标识具体类型，反序列化按 type 路由到已知子类；
 * 未知 type 落到 {@link GenericAttribute} 原样保留。</p>
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY,
        property = "type", visible = true, defaultImpl = GenericAttribute.class)
@JsonSubTypes({
        @JsonSubTypes.Type(value = MeasureAttribute.class, name = MeasureAttribute.TYPE),
        @JsonSubTypes.Type(value = StatusAttribute.class, name = StatusAttribute.TYPE),
        @JsonSubTypes.Type(value = RelationAttribute.class, name = RelationAttribute.TYPE),
        @JsonSubTypes.Type(value = VenueAttribute.class, name = VenueAttribute.TYPE)
})
public abstract class Attribute {

    /** 类型标识，例如 "measure"、"pregnancy.profile"、"exam.target"。 */
    @com.fasterxml.jackson.annotation.JsonProperty("type")
    public abstract String getType();

    /** 透传 LLM 额外给出但子类未声明的字段。 */
    private final Map<String, Object> extras = new LinkedHashMap<>();

    @JsonAnyGetter
    public Map<String, Object> getExtras() {
        return extras;
    }

    @JsonAnySetter
    public void setExtra(String name, Object value) {
        extras.put(name, value);
    }
}
