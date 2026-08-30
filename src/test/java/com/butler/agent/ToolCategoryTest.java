package com.butler.agent;

import static org.junit.jupiter.api.Assertions.*;

import com.butler.domain.agent.ToolCategory;
import com.butler.domain.agent.ToolContext;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 大类路由/分发/schema 合并的契约测试：改 ToolCategory 时保证这些行为不回归。 */
class ToolCategoryTest {

    private ToolContext ctx() {
        return new ToolContext(7L, "main", null, null, Map.of(), LocalDate.now());
    }

    @Test
    void modelSelectsSubToolViaSubTool() throws Exception {
        FakeTool a = new FakeTool("alpha", "甲工具");
        FakeTool b = new FakeTool("beta", "乙工具");
        ToolCategory cat = new ToolCategory("Cat", "大类", List.of(a, b));

        String out = cat.execute("{\"sub_tool\":\"beta\",\"query\":\"找东西\"}", ctx());

        assertEquals("beta:OK", out);
        assertNull(a.receivedArgs, "未被选中的子工具不应执行");
        assertNotNull(b.receivedArgs);
        assertTrue(b.receivedArgs.contains("找东西"), "query 应透传给子工具: " + b.receivedArgs);
    }

    @Test
    void unknownSubToolFallsBackToDefaultRouting() throws Exception {
        FakeTool a = new FakeTool("alpha", "甲工具");
        FakeTool b = new FakeTool("beta", "乙工具");
        ToolCategory cat = new ToolCategory("Cat", "大类", List.of(a, b));

        // sub_tool 传了不存在的名字 -> 回退到默认(第一个)子工具
        String out = cat.execute("{\"sub_tool\":\"not_exist\",\"query\":\"q\"}", ctx());

        assertEquals("alpha:OK", out);
        assertNotNull(a.receivedArgs);
    }

    @Test
    void singleChildRoutesDirectlyWithoutSubTool() throws Exception {
        FakeTool only = new FakeTool("solo", "唯一子工具");
        ToolCategory cat = new ToolCategory("Solo", "只有一个子工具", List.of(only));

        String out = cat.execute("{\"query\":\"hello\"}", ctx());

        assertEquals("solo:OK", out);
        assertNotNull(only.receivedCtx);
    }

    @Test
    void schemaMergesChildPropertiesAndOffersSubToolEnumWhenMultiple() {
        FakeTool a = new FakeTool("alpha", "甲");
        FakeTool b = new FakeTool("beta", "乙");
        ToolCategory multi = new ToolCategory("Cat", "大类", List.of(a, b));
        String schema = multi.parametersSchema();
        assertTrue(schema.contains("\"sub_tool\""), "多子工具应暴露 sub_tool: " + schema);
        assertTrue(schema.contains("alpha") && schema.contains("beta"), "enum 应含子工具名");
        assertTrue(schema.contains("query"), "应合并 query 兜底参数");

        ToolCategory single = new ToolCategory("Solo", "大类", List.of(a));
        assertFalse(single.parametersSchema().contains("sub_tool"), "单子工具不需要 sub_tool");
    }

    @Test
    void descriptionListsChildrenForModel() {
        ToolCategory cat = new ToolCategory("Cat", "大类说明",
                List.of(new FakeTool("alpha", "甲工具"), new FakeTool("beta", "乙工具")));
        String d = cat.description();
        assertTrue(d.contains("alpha") && d.contains("甲工具"), "描述应含子工具名与说明: " + d);
    }

    @Test
    void invalidArgumentsJsonDoesNotLeakException() {
        FakeTool a = new FakeTool("alpha", "甲");
        ToolCategory cat = new ToolCategory("Cat", "大类", List.of(a));
        // 坏 JSON 应抛异常给上层统一兜底，而不是静默错误结果
        assertThrows(Exception.class, () -> cat.execute("{not json", ctx()));
    }
}
