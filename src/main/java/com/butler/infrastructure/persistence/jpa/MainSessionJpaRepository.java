package com.butler.infrastructure.persistence.jpa;

import com.butler.infrastructure.persistence.po.MainSessionPO;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MainSessionJpaRepository extends JpaRepository<MainSessionPO, Long> {
    Optional<MainSessionPO> findByUserId(Long userId);
}
