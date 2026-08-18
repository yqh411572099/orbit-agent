package com.butler.domain.model;

import java.time.Instant;

public class MemorySessionRel {
    private final Long id;
    private final Long memoryId;
    private final Long subSessionId;
    private final Instant bindTime;

    public MemorySessionRel(Long id, Long memoryId, Long subSessionId, Instant bindTime) {
        this.id = id;
        this.memoryId = memoryId;
        this.subSessionId = subSessionId;
        this.bindTime = bindTime;
    }

    public Long getId() { return id; }
    public Long getMemoryId() { return memoryId; }
    public Long getSubSessionId() { return subSessionId; }
    public Instant getBindTime() { return bindTime; }
}
