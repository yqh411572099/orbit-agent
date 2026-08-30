package com.butler.domain.repository;

import com.butler.domain.model.MemorySessionRel;
import java.util.List;

public interface MemorySessionRelRepository {
    MemorySessionRel save(MemorySessionRel rel);
    List<MemorySessionRel> findByMemoryId(Long memoryId);
    List<MemorySessionRel> findBySubSessionId(Long subSessionId);
    List<MemorySessionRel> findAll();
    boolean existsByMemoryIdAndSubSessionId(Long memoryId, Long subSessionId);
    void deleteBySubSessionId(Long subSessionId);
    void deleteByMemoryId(Long memoryId);

    void archiveAndDeleteBySubSessionId(Long subSessionId, Long userId, String reason);
    void archiveAndDeleteByMemoryId(Long memoryId, Long userId, String reason);
}
