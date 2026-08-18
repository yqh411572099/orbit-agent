package com.butler.application.tool;

import com.butler.domain.agent.ToolCategory;
import java.util.List;
import org.springframework.stereotype.Component;

/** 联网检索大类：通用网页搜索，后续可加官网/政策专题等子工具。 */
@Component
public class WebSearchCategory extends ToolCategory {
    public WebSearchCategory(WebSearchTool webSearchTool) {
        super("WebSearch", "从开放互联网检索最新信息：政策、补贴、办事流程、机构动态", List.of(webSearchTool));
    }
}
