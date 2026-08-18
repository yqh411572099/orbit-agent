package com.butler.application.tool;

import com.butler.domain.agent.ToolCategory;
import java.util.List;
import org.springframework.stereotype.Component;

/** 知识库大类：检索已沉淀知识 + 管理待确认候选。 */
@Component
public class KnowledgeBaseCategory extends ToolCategory {
    public KnowledgeBaseCategory(KnowledgeSearchTool knowledgeSearchTool,
                                 KnowledgeManageTool knowledgeManageTool) {
        super("KnowledgeBase", "检索已沉淀的内部知识并管理知识沉淀：领域常识、办事指南、本目标时间轴与关注项说明",
                List.of(knowledgeSearchTool, knowledgeManageTool));
    }
}
