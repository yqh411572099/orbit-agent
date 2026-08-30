package com.butler.infrastructure.persistence.adapter;

import com.butler.domain.model.MainSession;
import com.butler.domain.repository.MainSessionRepository;
import com.butler.infrastructure.persistence.jpa.MainSessionJpaRepository;
import com.butler.infrastructure.persistence.po.MainSessionPO;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class MainSessionRepositoryAdapter implements MainSessionRepository {
    private final MainSessionJpaRepository jpa;
    public MainSessionRepositoryAdapter(MainSessionJpaRepository jpa) { this.jpa = jpa; }

    @Override
    public Optional<MainSession> findByUserId(Long userId) {
        return jpa.findByUserId(userId).map(po -> new MainSession(po.getId(), po.getUserId(), po.getCreatedAt(),
                po.getCity(), po.getLatitude(), po.getLongitude(),
                com.butler.domain.model.InfoSourceMode.from(po.getInfoSourceMode())));
    }

    @Override
    public List<MainSession> findAll() {
        return jpa.findAll().stream()
                .map(po -> new MainSession(po.getId(), po.getUserId(), po.getCreatedAt(),
                        po.getCity(), po.getLatitude(), po.getLongitude(),
                        com.butler.domain.model.InfoSourceMode.from(po.getInfoSourceMode())))
                .toList();
    }

    @Override
    public MainSession save(MainSession session) {
        MainSessionPO po = new MainSessionPO();
        po.setId(session.getId());
        po.setUserId(session.getUserId());
        po.setCreatedAt(session.getCreatedAt());
        po.setCity(session.getCity());
        po.setLatitude(session.getLatitude());
        po.setLongitude(session.getLongitude());
        po.setInfoSourceMode(session.getInfoSourceMode().name());
        MainSessionPO saved = jpa.save(po);
        return new MainSession(saved.getId(), saved.getUserId(), saved.getCreatedAt(),
                saved.getCity(), saved.getLatitude(), saved.getLongitude(),
                com.butler.domain.model.InfoSourceMode.from(saved.getInfoSourceMode()));
    }
}
