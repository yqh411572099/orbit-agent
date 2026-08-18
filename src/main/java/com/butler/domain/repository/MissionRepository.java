package com.butler.domain.repository;

import com.butler.domain.model.Mission;
import java.util.Optional;

public interface MissionRepository {
    Mission save(Mission mission);
    Optional<Mission> findById(Long id);
}
