package com.butler.infrastructure.llm;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** 未配置模型 key 时的降级实现。 */
public class NoopLlmAdapter implements LlmPort {
    @Override
    public CreateGoalResult createGoal(String userGoal, String scenarioType) {
        return new CreateGoalResult("场景：" + scenarioType + "；目标：" + userGoal,
                List.of(new TaskItem("推进 " + userGoal, "", "", "", "")));
    }
    @Override
    public AdjustTasksResult adjustTasks(String sessionDesc, List<TaskItem> currentTasks, String newMessage) {
        return new AdjustTasksResult(currentTasks.isEmpty() ? List.of(new TaskItem("跟进：" + newMessage, "", "", "", "", "")) : currentTasks);
    }
    @Override
    public String composeReminder(String sessionDesc, String taskContent, String aiBrief,
                                  List<String> recentDialog, java.time.LocalDate today) {
        return "";
    }
    @Override
    public ExtractResult extractAndAssociate(String rawConversation, List<String> activeSessionDescs, String attributeSchema, String existingMemories) {
        String memory = rawConversation == null ? "" : rawConversation.lines().findFirst().orElse("");
        if (memory.isBlank()) return new ExtractResult(List.of());
        return new ExtractResult(List.of(new ExtractedMemory(memory, "CONTEXT",
                "self", "", "", "", "", "", 1.0, List.of(), List.of())));
    }
    @Override
    public ScenarioEvent extractScenarioEvent(String scenarioType, List<String> keyFieldHints,
                                             String collectedInfo, String newMessage) {
        return new ScenarioEvent(java.util.Map.of(), List.of(), List.of(), List.of(), "");
    }
    @Override
    public GoalIntent detectGoalIntent(List<ChatMessage> recentMessages, String userMessage, List<String> scenarioCatalog) {
        return new GoalIntent(false, "", "", Map.of(), List.of(), List.of(), "（未配置模型 Key）" + userMessage);
    }
    @Override
    public ProposalReply classifyProposalReply(String proposalSummary, String userMessage) {
        return new ProposalReply("unrelated", "");
    }

    @Override
    public String streamChat(String systemPrompt, List<ChatMessage> messages, Consumer<String> onChunk) {
        String reply = "（未配置模型 Key，当前为降级回复）你说的是：" +
                messages.stream().reduce((a, b) -> b).map(ChatMessage::content).orElse("");
        for (char c : reply.toCharArray()) {
            onChunk.accept(String.valueOf(c));
        }
        return reply;
    }
    @Override
    public ToolChatResult toolChat(String systemPrompt, List<ChatMessage> messages, List<ToolDef> tools) {
        String reply = "（未配置模型 Key，当前为降级回复）" +
                messages.stream().reduce((a, b) -> b).map(ChatMessage::content).orElse("");
        return new ToolChatResult(reply, List.of());
    }
}
