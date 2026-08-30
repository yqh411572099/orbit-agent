package com.butler.infrastructure.llm;

import com.butler.domain.attribute.Attribute;
import java.util.List;
import java.util.function.Consumer;

public interface LlmPort {

    /**
     * 模型档位：主模型负责对话正文、工具规划、事件抽取、记忆提炼等重任务；
     * 轻量模型（light）负责上下文锚定、建目标意图判定、确认回复分类等结构化轻任务。
     * 轻量模型未单独配置时回退主模型（包月制下默认同模型），将来可用配置切到更小/更快/更省的模型。
     */

    CreateGoalResult createGoal(String userGoal, String scenarioType);

    AdjustTasksResult adjustTasks(String sessionDesc, List<TaskItem> currentTasks, String newMessage);

    /**
     * 到点提醒时，针对“需 LLM 介入”的任务动态生成本次推送内容（如结合近几日饮食给今日食谱）。
     * recentDialog 为该子对话最近的用户/管家对话行（已正序）。返回生成的正文；无法生成返回空串。
     */
    String composeReminder(String sessionDesc, String taskContent, String aiBrief,
                           List<String> recentDialog, java.time.LocalDate today);

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
                                      List<String> domainRuleHints,
                                      String collectedInfo, String newMessage, String existingMetrics,
                                      String assistantReply, String groundingText);

    /**
     * 感知层“上下文锚定”：在回答/抽取之前，把用户这句话里的相对时间、指代、简称
     * 解析成绝对对象（哪个自然日、哪一餐、哪个人物、哪条待办/关注项、哪个地点、哪个量化指标），
     * 并区分本轮是“新发生/要记账的事件”还是“仅回顾/询问历史”。
     * 主对话/子对话通用，与具体场景无关；场景只影响可选的锚点提示。
     * 返回结构化锚点；无法锚定的字段留空，绝不编造。
     */
    Grounding groundContext(String userMessage, List<ChatMessage> recentMessages,
                            String situationText, String focusCatalogText,
                            List<ToolDef> tools, ToolExecutor toolExecutor);

    /** 工具执行回调：锚定器需要外部信息（节日公历日期、地点归属、坐标等）时按名调用对应工具。 */
    @FunctionalInterface
    interface ToolExecutor {
        String execute(String toolName, String argumentsJson);
    }


    /**
     * 一次上下文锚定结果。
     * @param anchorTimeText  给模型/下游阅读的“本轮时间锚点”自然语言说明（绝对日期+餐次/时段）。
     * @param events          本轮解析出的、应当被记账/处理的事件；每个事件带绝对日期与归类。
     * @param isRetrospective 这句话是否只是在回顾/询问过去，而非新汇报一条要落库的数据。
     */
    record Grounding(String anchorTimeText, List<GroundedEvent> events, boolean isRetrospective, String note) {
        public Grounding() { this("", List.of(), false, ""); }
    }
    /**
     * 一个被锚定到绝对对象的事件。
     * @param kind      事件类别（meal/food/weight/exercise/study/task/fact/...），仅作下游参考。
     * @param subject   主体（self/partner/...），无法判断给 self。
     * @param date      事件实际发生/被测量的自然日 yyyy-MM-dd（绝对，已消解“今天/昨天/今早”等）。
     * @param period    时段/餐次（早餐/午餐/晚餐/上午/晚上 等），无法判断留空。
     * @param refObject 指代落到的具体对象（哪条待办/关注项/指标/地点），无法判断留空。
     * @param summary   该事件一句话事实（如“今早吃了 2 个鸡蛋、一杯豆浆”）。
     * @param isNew     是否为本轮新发生、需要记账/处理；仅回顾为 false。
     */
    record GroundedEvent(String kind, String subject, String date, String period,
                         String refObject, String summary, boolean isNew) {}

    /** 一个可视化指标卡定义：模型决定展示哪个指标、单位、用什么图表。 */
    /**
     * 一张指标卡/图。key=卡片唯一标识（series 只有一条时可与指标同名）；
     * series=这张图里的多条序列（如“每日热量消耗构成”含静息/运动/总消耗三条）；
     * 单指标图 series 给一条即可。chartType=line/bar/pie。
     */
    record MetricDef(String key, String label, String unit, String chartType, List<MetricSeries> series) {
        public MetricDef(String key, String label, String unit, String chartType) {
            this(key, label, unit, chartType, List.of(new MetricSeries(key, label)));
        }
    }
    /** 图内的一条序列。key 用于匹配数据点，label 为图例名。 */
    record MetricSeries(String key, String label) {}

    /** 一条指标数据点（用户汇报的数值）。date 为 yyyy-MM-dd，缺省取今天。key 对应某个 series 的 key。 */
    record MetricPointIn(String key, Double value, String date) {}

    /**
     * @param fieldUpdates      关键目标字段的新值，如 {"dueDate":"2027-05-01"}
     * @param completedKeywords 已完成里程碑关键词
     * @param enableFocusAreas  新增关注项 key
     * @param disableFocusAreas 关闭关注项 key
     * @param note              一句话说明
     */
    record ScenarioEvent(java.util.Map<String,String> fieldUpdates, List<String> completedKeywords,
                         List<String> enableFocusAreas, List<String> disableFocusAreas, String note,
                         boolean affectsTasks, List<MetricDef> metricDefs, List<MetricPointIn> metricPoints,
                         List<String> metricRemove) {
        public ScenarioEvent(java.util.Map<String,String> fieldUpdates, List<String> completedKeywords,
                             List<String> enableFocusAreas, List<String> disableFocusAreas, String note) {
            this(fieldUpdates, completedKeywords, enableFocusAreas, disableFocusAreas, note, false, List.of(), List.of(), List.of());
        }
        public ScenarioEvent(java.util.Map<String,String> fieldUpdates, List<String> completedKeywords,
                             List<String> enableFocusAreas, List<String> disableFocusAreas, String note,
                             boolean affectsTasks) {
            this(fieldUpdates, completedKeywords, enableFocusAreas, disableFocusAreas, note, affectsTasks, List.of(), List.of(), List.of());
        }
    }

    record TaskItem(String content, String dueDate, String focusArea, String detail, String recurrence,
                    String remindTime, String aiBrief) {
        public TaskItem(String content, String dueDate, String focusArea) { this(content, dueDate, focusArea, "", "", ""); }
        public TaskItem(String content, String dueDate, String focusArea, String detail, String recurrence) {
            this(content, dueDate, focusArea, detail, recurrence, "", "");
        }
        public TaskItem(String content, String dueDate, String focusArea, String detail, String recurrence, String remindTime) {
            this(content, dueDate, focusArea, detail, recurrence, remindTime, "");
        }
    }
    record CreateGoalResult(String sessionDesc, List<TaskItem> tasks, List<MetricDef> metricDefs) {
        public CreateGoalResult(String sessionDesc, List<TaskItem> tasks) {
            this(sessionDesc, tasks, List.of());
        }
    }
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
