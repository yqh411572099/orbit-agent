package com.butler.agent;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.butler.application.KnowledgeAppService;
import com.butler.application.tool.WebSearchTool;
import com.butler.domain.agent.ToolContext;
import com.butler.domain.service.WebSearchPort;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 联网搜索工具：参数校验、空结果兜底、结果格式化、无用户上下文不写知识库。 */
class WebSearchToolTest {

    private final WebSearchPort port = mock(WebSearchPort.class);
    private final KnowledgeAppService knowledge = mock(KnowledgeAppService.class);
    private final WebSearchTool tool = new WebSearchTool(port, knowledge);

    private ToolContext ctx(Long userId) {
        return new ToolContext(userId, "main", null, null, Map.of(), LocalDate.now(), false);
    }

    @Test
    void blankQueryAsksForKeyWord() {
        String out = assertDoesNotThrow(() -> tool.execute("{\"query\":\"   \"}", ctx(1L)));
        assertTrue(out.contains("请给出"));
        verify(port, never()).search(anyString(), anyInt());
    }

    @Test
    void emptyResultsGiveFallbackMessage() throws Exception {
        when(port.search(anyString(), anyInt())).thenReturn(List.of());
        String out = tool.execute("{\"query\":\"不存在的政策xyz\"}", ctx(1L));
        assertTrue(out.contains("未检索到"), out);
    }

    @Test
    void resultsAreFormattedWithTitleUrl() throws Exception {
        when(port.search(anyString(), anyInt())).thenReturn(List.of(
                new WebSearchPort.WebResult("杭州生育津贴办理指南", "https://gov.example/t", "摘要内容",
                        null, "政务网", "2026-08-01", 1)));
        when(knowledge.proposeFromWeb(any(), any(), anyString(), anyList())).thenReturn(List.of());
        String out = tool.execute("{\"query\":\"杭州生育津贴\"}", ctx(1L));
        assertTrue(out.contains("杭州生育津贴办理指南"));
        assertTrue(out.contains("https://gov.example/t"));
    }

    @Test
    void noUserContextDoesNotWriteKnowledge() throws Exception {
        when(port.search(anyString(), anyInt())).thenReturn(List.of(
                new WebSearchPort.WebResult("标题", "https://u", "摘要", null, "站", null, 1)));
        String out = tool.execute("{\"query\":\"q\"}", ctx(null));
        assertNotNull(out);
        verify(knowledge, never()).proposeFromWeb(any(), any(), anyString(), anyList());
    }

    @Test
    void nameAndSchemaAreStable() {
        assertEquals("web_search", tool.name());
        assertTrue(tool.parametersSchema().contains("query"));
        assertTrue(tool.description().contains("联网"));
    }
}
