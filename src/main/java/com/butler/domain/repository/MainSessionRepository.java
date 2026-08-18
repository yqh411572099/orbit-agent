package com.butler.domain.repository;

import com.butler.domain.model.MainSession;
import java.util.List;
import java.util.Optional;

public interface MainSessionRepository {
    Optional<MainSession> findByUserId(Long userId);
    List<MainSession> findAll();
    MainSession save(MainSession session);
}
