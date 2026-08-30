package com.butler.agent;

import static org.junit.jupiter.api.Assertions.*;

import com.butler.domain.agent.ToolCategory;
import com.butler.domain.agent.ToolRegistry;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 注册中心：全量可插拔注册、按名获取、重名/空名拦截、契约自检。 */
class ToolRegistryTest {

    private ToolCategory cat(String name, FakeTool... children) {
        return new ToolCategory(name, name + "说明", List.of(children));
    }

    @Test
    void registersAllCategoriesAndExposesThem() {
        ToolRegistry reg = new ToolRegistry(List.of(
                cat("WebSearch", new FakeTool("web_search", "搜索")),
                cat("GeoSearch", new FakeTool("nearby", "周边"), new FakeTool("geocode", "解析"))));
        assertEquals(2, reg.all().size());
        assertNotNull(reg.get("WebSearch"));
        assertNull(reg.get("NotExist"));
    }

    @Test
    void selfCheckPassesForWellFormedTools() {
        ToolRegistry reg = new ToolRegistry(List.of(
                cat("WebSearch", new FakeTool("web_search", "搜索"))));
        assertDoesNotThrow(reg::selfCheck);
    }

    @Test
    void duplicateCategoryNameRejected() {
        FakeTool t1 = new FakeTool("web_search", "搜索");
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> new ToolRegistry(List.of(cat("WebSearch", t1), cat("WebSearch", t1))));
        assertTrue(ex.getMessage().contains("重复"));
    }

    @Test
    void selfCheckFailsWhenSchemaInvalid() {
        FakeTool bad = new FakeTool("broken", "坏工具") {
            @Override public String parametersSchema() { return "{not-json"; }
        };
        ToolRegistry reg = new ToolRegistry(List.of(cat("BadCat", bad)));
        IllegalStateException ex = assertThrows(IllegalStateException.class, reg::selfCheck);
        assertTrue(ex.getMessage().contains("JSON"), ex.getMessage());
    }

    @Test
    void selfCheckFailsWhenDescriptionBlank() {
        FakeTool blank = new FakeTool("x", "") {
            @Override public String description() { return "  "; }
        };
        ToolRegistry reg = new ToolRegistry(List.of(cat("Cat", blank)));
        assertThrows(IllegalStateException.class, reg::selfCheck);
    }
}
