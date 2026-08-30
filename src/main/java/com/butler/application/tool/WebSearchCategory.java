package com.butler.application.tool;

import com.butler.domain.agent.ToolCategory;
import java.util.List;
import org.springframework.stereotype.Component;

/** 联网检索大类：通用网页搜索，后续可加官网/政策专题等子工具。 */
@Component
public class WebSearchCategory extends ToolCategory {
    public WebSearchCategory(WebSearchTool webSearchTool) {
        super("WebSearch", "从开放互联网检索模型之外的信息：当答案依赖会变化的事实、需要来源依据、或用户要求联网/核实时使用", List.of(webSearchTool));
    }
}
