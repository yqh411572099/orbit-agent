package com.butler.infrastructure.persistence.jpa;

import com.butler.infrastructure.persistence.po.MissionPO;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MissionJpaRepository extends JpaRepository<MissionPO, Long> {
}
