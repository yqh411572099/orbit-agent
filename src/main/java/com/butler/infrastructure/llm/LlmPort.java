package com.butler.infrastructure.llm;

import com.butler.domain.attribute.Attribute;
import java.util.List;
import java.util.function.Consumer;

public interface LlmPort {

    CreateGoalResult createGoal(String userGoal, String scenarioType);

    AdjustTasksResult adjustTasks(String sessionDesc, List<TaskItem> currentTasks, String newMessage);

    /** 从原始对话中提炼多条结构化记忆并判定关联子会话。attributeSchema 为各场景的属性目录文本。 */
    ExtractResult extractAndAssociate(String rawConversation, List<String> activeSessionDescs, String attributeSchema, String existingMemories);

    /** 流式闲聊/问答。systemPrompt 为组装好的长期记忆上下文，messages 为最近对话。返回完整回复。 */
    String streamChat(String systemPrompt, List<ChatMessage> messages, Consumer<String> onChunk);

    /** 判定用户是否想创建目标，并尽量收集结构化字段。 */
    GoalIntent detectGoalIntent(List<ChatMessage> recentMessages, String userMessage, List<String> scenarioCatalog);

    /**
     * 从子对话新消息中提取与场景相关的通用事件（关键字段变更/里程碑完成/关注项增减）。
     * 字段以“场景字段 key=新值”的形式表达，由各场景自己解释，不含任何孕期专有字段。
     */
    ScenarioEvent extractScenarioEvent(String scenarioType, List<String> keyFieldHints,
                                      String collectedInfo, String newMessage);

    /**
     * @param fieldUpdates      关键目标字段的新值，如 {"dueDate":"2027-05-01"}
     * @param completedKeywords 已完成里程碑关键词
     * @param enableFocusAreas  新增关注项 key
     * @param disableFocusAreas 关闭关注项 key
     * @param note              一句话说明
     */
    record ScenarioEvent(java.util.Map<String,String> fieldUpdates, List<String> completedKeywords,
                         List<String> enableFocusAreas, List<String> disableFocusAreas, String note,
                         boolean affectsTasks) {
        public ScenarioEvent(java.util.Map<String,String> fieldUpdates, List<String> completedKeywords,
                             List<String> enableFocusAreas, List<String> disableFocusAreas, String note) {
            this(fieldUpdates, completedKeywords, enableFocusAreas, disableFocusAreas, note, false);
        }
    }

    record TaskItem(String content, String dueDate, String focusArea, String detail, String recurrence, String remindTime) {
        public TaskItem(String content, String dueDate, String focusArea) { this(content, dueDate, focusArea, "", "", ""); }
        public TaskItem(String content, String dueDate, String focusArea, String detail, String recurrence) {
            this(content, dueDate, focusArea, detail, recurrence, "");
        }
    }
    record CreateGoalResult(String sessionDesc, List<TaskItem> tasks) {}
    record AdjustTasksResult(List<TaskItem> tasks) {}

    /**
     * 一条提炼后的记忆。attributes 为场景定义的强类型属性集合（带 type，可透传额外字段）。
     */
    record ExtractedMemory(String content, String category,
                          String subject, String subjectProfile, String eventDate,
                          String validFrom, String validTo, String location,
                          Double confidence, List<Attribute> attributes,
                          List<Integer> associatedIndexes) {}

    /**
     * @param supersededIndexes 本次对话使哪些“已有记忆”变得矛盾/过期（索引指向提炼时传入的已有记忆列表）。
     */
    record ExtractResult(List<ExtractedMemory> memories, List<Integer> supersededIndexes) {
        public ExtractResult(List<ExtractedMemory> memories) { this(memories, List.of()); }
    }
    /**
     * 对话消息。role 可为 user/assistant/system/tool；
     * assistant 发起工具调用时填 toolCalls；tool 结果消息填 toolCallId。
     */
    record ChatMessage(String role, String content, List<ToolCall> toolCalls, String toolCallId) {
        public ChatMessage(String role, String content) { this(role, content, null, null); }
    }
    record GoalIntent(boolean wantsToCreate, String scenarioType, String title,
                      java.util.Map<String,String> collected, List<String> missingFields,
                      List<String> focusAreas, String reply) {}

    /** 用户面对“待确认目标方案”时的回复意图。 */
    record ProposalReply(String action, String instruction) {
        public boolean isConfirm() { return "confirm".equalsIgnoreCase(action); }
        public boolean isCancel() { return "cancel".equalsIgnoreCase(action); }
        public boolean isModify() { return "modify".equalsIgnoreCase(action); }
    }

    /** 判定用户对一份待确认目标方案的回复是确认/修改/取消/无关。 */
    ProposalReply classifyProposalReply(String proposalSummary, String userMessage);


    /**
     * 带工具的一轮对话（非流式）：模型返回正文，或返回要调用的工具调用。
     * 编排器据此执行工具并把结果回传，直到模型给出正文。
     */
    ToolChatResult toolChat(String systemPrompt, List<ChatMessage> messages, List<ToolDef> tools);

    /** 给模型暴露的一个工具/大类定义。 */
    record ToolDef(String name, String description, String parametersSchemaJson) {}

    /** 一次模型返回：要么有 content，要么有 toolCalls。 */
    record ToolChatResult(String content, List<ToolCall> toolCalls) {
        public boolean hasToolCalls() { return toolCalls != null && !toolCalls.isEmpty(); }
    }
    record ToolCall(String id, String name, String argumentsJson) {}
}
