package com.butler.domain.service;

import java.util.List;

/**
 * 文本向量化端口。把文本转成稠密向量，供语义检索使用。
 * 换 embedding 提供商（ARK / DashScope / OpenAI / 本地模型）只需换实现。
 */
public interface EmbeddingPort {

    /** 单条文本向量化。 */
    List<Float> embed(String text);

    /** 批量向量化（减少往返）。顺序与输入一致。 */
    List<List<Float>> embedAll(List<String> texts);

    /** 向量维度，供建表/建索引使用。 */
    int dimension();

    /** 是否可用（未配置 key/endpoint 时返回 false，调用方据此降级）。 */
    boolean available();
}
