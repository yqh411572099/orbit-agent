package com.butler.domain.repository;

import com.butler.domain.model.UserMemory;
import java.util.List;

public interface UserMemoryRepository {
    UserMemory save(UserMemory memory);
    List<UserMemory> findByUserId(Long userId);
    List<UserMemory> findByIdIn(List<Long> ids);
    int deleteByUserId(Long userId);
    int deleteMemoryById(Long id);

    int archiveAndDeleteByUserId(Long userId, String reason);
    int archiveAndDeleteMemoryById(Long id, String reason);
}
