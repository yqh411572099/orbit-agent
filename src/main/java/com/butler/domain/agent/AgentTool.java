package com.butler.domain.agent;

/**
 * 智能管家可调用的工具。实现可以是具体工具，也可以是工具大类（{@link ToolCategory}）。
 * 模型看到的是“工具名 + 说明 + 参数 schema”，自行决定何时调用、传什么参数。
 */
public interface AgentTool {

    /** 工具/大类名，模型据此调用。 */
    String name();

    /** 给模型看的说明。 */
    String description();

    /** 入参 JSON Schema。 */
    String parametersSchema();

    /** 执行工具，argumentsJson 为模型传入的参数，返回给模型的文本结果。 */
    String execute(String argumentsJson, ToolContext context) throws Exception;
}
