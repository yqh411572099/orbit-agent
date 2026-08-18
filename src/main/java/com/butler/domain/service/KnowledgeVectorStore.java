package com.butler.domain.service;

import java.util.List;

/**
 * 知识向量库端口：写入知识条目的向量、按语义相似度检索。
 * 换向量库（Milvus / pgvector / LanceDB / 内存实现）只需换实现，应用层不变。
 * 向量与元数据 id 对齐：KnowledgeEntry.id。
 */
public interface KnowledgeVectorStore {

    /** 写入或更新一条知识的向量（upsert）。 */
    void upsert(Long knowledgeId, List<Float> vector);

    /** 批量写入。 */
    void upsertAll(List<Long> knowledgeIds, List<List<Float>> vectors);

    /** 返回与 query 向量最相似的知识 id（按相似度降序，最多 topK 条）。 */
    List<Long> search(List<Float> queryVector, int topK);

    /** 与上面一致，但可限定在某个知识 id 子集内检索（如某用户/某子会话范围）。 */
    List<Long> searchWithin(List<Float> queryVector, List<Long> scopeIds, int topK);

    /** 删除一条向量（知识条目被删除时调用）。 */
    void delete(Long knowledgeId);

    /** 集合是否已就绪（可连接、已建表）。 */
    boolean ready();
}
