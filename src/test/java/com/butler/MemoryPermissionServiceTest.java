package com.butler;

import com.butler.domain.model.*;
import com.butler.domain.repository.MemorySessionRelRepository;
import com.butler.domain.repository.UserMemoryRepository;
import com.butler.domain.service.MemoryPermissionService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MemoryPermissionServiceTest {

    private final UserMemoryRepository memoryRepo = mock(UserMemoryRepository.class);
    private final MemorySessionRelRepository relRepo = mock(MemorySessionRelRepository.class);
    private final MemoryPermissionService service = new MemoryPermissionService(memoryRepo, relRepo);

    @Test
    void mainSessionReadsAllMemories() {
        when(memoryRepo.findByUserId(1L)).thenReturn(List.of(
                new UserMemory(10L, 1L, MemoryCategory.FACT, "记忆A", null, Instant.now()),
                new UserMemory(11L, 1L, MemoryCategory.FACT, "记忆B", null, Instant.now())));

        List<UserMemory> result = service.readableMemories(1L, SessionType.MAIN, null);

        assertEquals(2, result.size());
        verify(memoryRepo).findByUserId(1L);
        verifyNoInteractions(relRepo);
    }

    @Test
    void subSessionReadsOnlyBoundMemories() {
        when(relRepo.findBySubSessionId(100L)).thenReturn(List.of(
                new MemorySessionRel(1L, 10L, 100L, Instant.now())));
        when(memoryRepo.findByIdIn(List.of(10L))).thenReturn(List.of(
                new UserMemory(10L, 1L, MemoryCategory.FACT, "记忆A", null, Instant.now())));

        List<UserMemory> result = service.readableMemories(1L, SessionType.SUB, 100L);

        assertEquals(1, result.size());
        assertEquals("记忆A", result.get(0).getContent());
    }

    @Test
    void subSessionWithoutBindingsReadsNothing() {
        when(relRepo.findBySubSessionId(200L)).thenReturn(List.of());

        List<UserMemory> result = service.readableMemories(1L, SessionType.SUB, 200L);

        assertTrue(result.isEmpty());
        verify(memoryRepo, never()).findByUserId(anyLong());
    }
}
