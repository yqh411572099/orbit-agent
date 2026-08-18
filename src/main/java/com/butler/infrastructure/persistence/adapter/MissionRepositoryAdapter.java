package com.butler.infrastructure.persistence.adapter;

import com.butler.domain.model.Mission;
import com.butler.domain.repository.MissionRepository;
import com.butler.infrastructure.persistence.jpa.MissionJpaRepository;
import com.butler.infrastructure.persistence.po.MissionPO;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class MissionRepositoryAdapter implements MissionRepository {
    private final MissionJpaRepository jpa;
    public MissionRepositoryAdapter(MissionJpaRepository jpa) { this.jpa = jpa; }

    @Override
    public Mission save(Mission m) {
        MissionPO po = new MissionPO();
        po.setId(m.getId());
        po.setUserId(m.getUserId());
        po.setTitle(m.getTitle());
        po.setScenarioType(m.getScenarioType());
        po.setCreatedAt(m.getCreatedAt());
        MissionPO saved = jpa.save(po);
        return new Mission(saved.getId(), saved.getUserId(), saved.getTitle(), saved.getScenarioType(), saved.getCreatedAt());
    }

    @Override
    public Optional<Mission> findById(Long id) {
        return jpa.findById(id).map(po -> new Mission(po.getId(), po.getUserId(), po.getTitle(), po.getScenarioType(), po.getCreatedAt()));
    }
}
