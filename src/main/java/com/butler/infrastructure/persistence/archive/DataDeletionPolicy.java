package com.butler.infrastructure.persistence.archive;

import com.butler.domain.model.UserType;
import com.butler.domain.repository.UserRepository;
import org.springframework.stereotype.Component;

/**
 * 删除策略：根据 app_user.user_type 判断。
 * 测试用户(TEST)可直接物理删除；普通/高级用户等真实数据必须先归档再删除。
 */
@Component
public class DataDeletionPolicy {

    private final UserRepository userRepository;

    public DataDeletionPolicy(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public boolean isTestUser(Long userId) {
        if (userId == null) return false;
        return userRepository.findById(userId)
                .map(u -> u.getUserType() == UserType.TEST)
                .orElse(false);
    }
}
