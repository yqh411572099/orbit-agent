package com.butler.infrastructure.persistence.jpa;

import com.butler.infrastructure.persistence.po.UserPO;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserJpaRepository extends JpaRepository<UserPO, Long> {
    Optional<UserPO> findByUsername(String username);
    boolean existsByUsername(String username);
}
