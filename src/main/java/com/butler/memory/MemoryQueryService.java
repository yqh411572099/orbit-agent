package com.butler.memory;

import com.butler.domain.model.SessionType;
import com.butler.domain.model.UserMemory;
import com.butler.domain.service.MemoryPermissionService;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class MemoryQueryService {

    private final MemoryPermissionService permissionService;

    public MemoryQueryService(MemoryPermissionService permissionService) {
        this.permissionService = permissionService;
    }

    public List<UserMemory> query(Long userId, SessionType sessionType, Long subSessionId) {
        return permissionService.readableMemories(userId, sessionType, subSessionId);
    }
}
