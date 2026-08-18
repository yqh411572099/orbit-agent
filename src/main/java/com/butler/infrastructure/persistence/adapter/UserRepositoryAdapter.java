package com.butler.infrastructure.persistence.adapter;

import com.butler.domain.model.User;
import com.butler.domain.model.UserType;
import com.butler.domain.repository.UserRepository;
import com.butler.infrastructure.persistence.jpa.UserJpaRepository;
import com.butler.infrastructure.persistence.po.UserPO;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class UserRepositoryAdapter implements UserRepository {
    private final UserJpaRepository jpa;
    public UserRepositoryAdapter(UserJpaRepository jpa) { this.jpa = jpa; }

    @Override
    public User save(User user) {
        UserPO po = new UserPO();
        po.setId(user.getId());
        po.setUsername(user.getUsername());
        po.setPasswordHash(user.getPasswordHash());
        po.setNickname(user.getNickname());
        po.setUserType(user.getUserType().name());
        po.setCreatedAt(user.getCreatedAt());
        return toDomain(jpa.save(po));
    }

    @Override
    public Optional<User> findById(Long id) {
        return jpa.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<User> findByUsername(String username) {
        return jpa.findByUsername(username).map(this::toDomain);
    }

    @Override
    public boolean existsByUsername(String username) {
        return jpa.existsByUsername(username);
    }

    private User toDomain(UserPO po) {
        return new User(po.getId(), po.getUsername(), po.getPasswordHash(), po.getNickname(),
                UserType.valueOf(po.getUserType()), po.getCreatedAt());
    }
}
