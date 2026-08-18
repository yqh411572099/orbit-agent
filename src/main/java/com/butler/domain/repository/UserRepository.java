package com.butler.domain.repository;

import com.butler.domain.model.User;
import java.util.Optional;

public interface UserRepository {
    User save(User user);
    Optional<User> findById(Long id);
    Optional<User> findByUsername(String username);
    boolean existsByUsername(String username);
}
