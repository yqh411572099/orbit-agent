package com.butler.agent;

import com.butler.domain.agent.AgentTool;
import com.butler.domain.agent.ToolContext;

/** 测试用假工具：记录收到的参数，返回固定/可注入结果。 */
class FakeTool implements AgentTool {
    final String name;
    final String description;
    String receivedArgs;
    ToolContext receivedCtx;
    String result = "OK";
    RuntimeException toThrow;

    FakeTool(String name, String description) {
        this.name = name;
        this.description = description;
    }

    FakeTool returns(String r) { this.result = r; return this; }
    FakeTool throwsOnExec(RuntimeException e) { this.toThrow = e; return this; }

    @Override public String name() { return name; }
    @Override public String description() { return description; }
    @Override public String parametersSchema() {
        return "{\"type\":\"object\",\"properties\":{\"query\":{\"type\":\"string\"}}}";
    }
    @Override public String execute(String argumentsJson, ToolContext context) throws Exception {
        this.receivedArgs = argumentsJson;
        this.receivedCtx = context;
        if (toThrow != null) throw toThrow;
        return name + ":" + result;
    }
}
