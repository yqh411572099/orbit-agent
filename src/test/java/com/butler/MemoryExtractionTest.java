package com.butler;

import com.butler.application.MemoryExtractionAppService;
import com.butler.domain.attribute.AttributeDescriptor;
import com.butler.domain.model.*;
import com.butler.domain.repository.*;
import com.butler.domain.scenario.ScenarioDomain;
import com.butler.domain.scenario.ScenarioRegistry;
import com.butler.infrastructure.llm.LlmPort;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MemoryExtractionTest {

    private ScenarioRegistry registryWithEmptyCatalog() {
        ScenarioRegistry registry = mock(ScenarioRegistry.class);
        ScenarioDomain domain = mock(ScenarioDomain.class);
        when(domain.attributeCatalog()).thenReturn(List.<AttributeDescriptor>of());
        when(registry.supports(anyString())).thenReturn(true);
        when(registry.get(anyString())).thenReturn(domain);
        return registry;
    }

    @Test
    void extractsMemoryAndBindsToMatchedSubSessions() {
        RawChatLogRepository rawRepo = mock(RawChatLogRepository.class);
        UserMemoryRepository memRepo = mock(UserMemoryRepository.class);
        MemorySessionRelRepository relRepo = mock(MemorySessionRelRepository.class);
        SubSessionRepository subRepo = mock(SubSessionRepository.class);
        ScenarioRegistry registry = registryWithEmptyCatalog();
        LlmPort llm = mock(LlmPort.class);

        when(rawRepo.findByUserIdAndCreatedAtBetween(eq(1L), any(), any()))
                .thenReturn(List.of(new RawChatLog(1L, 1L, SessionType.SUB, 100L, "user",
                        "家里急事，未来3个月无法学习", null, Instant.now())));
        when(subRepo.findByUserIdAndStatus(1L, SubSessionStatus.ACTIVE))
                .thenReturn(List.of(
                        new SubSession(100L, 1L, 1L, "exam_prep", "考研", SubSessionStatus.ACTIVE, Instant.now()),
                        new SubSession(200L, 1L, 2L, "cert_prep", "考证", SubSessionStatus.ACTIVE, Instant.now()),
                        new SubSession(300L, 1L, 3L, "pregnancy", "孕期", SubSessionStatus.ACTIVE, Instant.now())));
        when(llm.extractAndAssociate(anyString(), anyList(), anyString(), anyString()))
                .thenReturn(new LlmPort.ExtractResult(List.of(
                        new LlmPort.ExtractedMemory(
                                "家庭突发急事，未来3个月无法学习", "CONTEXT",
                                "self", null, null, null, null, null,
                                1.0, List.of(), List.of(0, 1)))));
        when(memRepo.save(any())).thenAnswer(inv -> {
            UserMemory m = inv.getArgument(0);
            return new UserMemory(99L, m.getUserId(), m.getCategory(), m.getContent(), m.getSourceRawLogId(), m.getCreatedAt());
        });
        when(relRepo.existsByMemoryIdAndSubSessionId(anyLong(), anyLong())).thenReturn(false);

        MemoryExtractionAppService svc = new MemoryExtractionAppService(rawRepo, memRepo, relRepo, subRepo, registry, llm);
        int count = svc.runForUser(1L, Instant.now().minusSeconds(7200), Instant.now());

        assertEquals(1, count);
        verify(relRepo).save(argThat(r -> r.getSubSessionId().equals(100L)));
        verify(relRepo).save(argThat(r -> r.getSubSessionId().equals(200L)));
        verify(relRepo, never()).save(argThat(r -> r.getSubSessionId().equals(300L)));
    }

    @Test
    void associationFailureDegradesGracefully() {
        RawChatLogRepository rawRepo = mock(RawChatLogRepository.class);
        UserMemoryRepository memRepo = mock(UserMemoryRepository.class);
        MemorySessionRelRepository relRepo = mock(MemorySessionRelRepository.class);
        SubSessionRepository subRepo = mock(SubSessionRepository.class);
        ScenarioRegistry registry = registryWithEmptyCatalog();
        LlmPort llm = mock(LlmPort.class);

        when(rawRepo.findByUserIdAndCreatedAtBetween(anyLong(), any(), any()))
                .thenReturn(List.of(new RawChatLog(1L, 1L, SessionType.MAIN, null, "user", "hello", null, Instant.now())));
        when(llm.extractAndAssociate(anyString(), anyList(), anyString(), anyString())).thenThrow(new RuntimeException("LLM error"));

        MemoryExtractionAppService svc = new MemoryExtractionAppService(rawRepo, memRepo, relRepo, subRepo, registry, llm);
        int count = svc.runForUser(1L, Instant.now().minusSeconds(7200), Instant.now());

        assertEquals(0, count);
        verify(memRepo, never()).save(any());
    }
}
