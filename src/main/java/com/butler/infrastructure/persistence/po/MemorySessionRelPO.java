package com.butler.infrastructure.persistence.po;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "memory_session_rel",
        uniqueConstraints = @UniqueConstraint(columnNames = {"memory_id", "sub_session_id"}),
        indexes = @Index(name = "idx_rel_sub", columnList = "sub_session_id"))
public class MemorySessionRelPO {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "memory_id", nullable = false)
    private Long memoryId;
    @Column(name = "sub_session_id", nullable = false)
    private Long subSessionId;
    @Column(name = "bind_time", nullable = false)
    private Instant bindTime = Instant.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getMemoryId() { return memoryId; }
    public void setMemoryId(Long memoryId) { this.memoryId = memoryId; }
    public Long getSubSessionId() { return subSessionId; }
    public void setSubSessionId(Long subSessionId) { this.subSessionId = subSessionId; }
    public Instant getBindTime() { return bindTime; }
    public void setBindTime(Instant bindTime) { this.bindTime = bindTime; }
}
