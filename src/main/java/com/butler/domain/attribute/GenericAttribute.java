package com.butler.domain.attribute;

import java.util.LinkedHashMap;
import java.util.Map;

/** 兜底属性：未知 type 或场景自定义类型，原样保留全部字段（含 type 自身声明字段）。 */
public class GenericAttribute extends Attribute {
    private String type;

    public GenericAttribute() {}
    public GenericAttribute(String type, Map<String, Object> fields) {
        this.type = type;
        if (fields != null) getExtras().putAll(fields);
    }
    @Override public String getType() { return type; }
    public void setType(String type) { this.type = type; }
}
