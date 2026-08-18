package com.butler.infrastructure.persistence.archive;

import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface EntityArchiveJpaRepository extends JpaRepository<EntityArchivePO, Long> {
    @Transactional
    int deleteByArchivedAtBefore(Instant cutoff);
}
