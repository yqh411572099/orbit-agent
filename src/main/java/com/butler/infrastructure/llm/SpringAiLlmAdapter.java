package com.butler.infrastructure.llm;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import reactor.core.publisher.Flux;

public class SpringAiLlmAdapter implements LlmPort {

    private static final Logger log = LoggerFactory.getLogger(SpringAiLlmAdapter.class);
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private final ChatClient chatClient;
    /** 轻量级模型入口：锚定/意图判定/分类等结构化轻任务走它；未配置则回退主模型。 */
    private final ChatClient lightChatClient;
    private final ObjectMapper mapper;
    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final java.net.http.HttpClient http = java.net.http.HttpClient.newBuilder()
            .connectTimeout(java.time.Duration.ofSeconds(10)).build();

    public SpringAiLlmAdapter(ChatClient.Builder builder, ObjectMapper mapper) {
        this(builder, mapper, "", "", "", "");
    }

    public SpringAiLlmAdapter(ChatClient.Builder builder, ObjectMapper mapper,
                              String apiKey, String baseUrl, String model) {
        this(builder, mapper, apiKey, baseUrl, model, "");
    }

    public SpringAiLlmAdapter(ChatClient.Builder builder, ObjectMapper mapper,
                              String apiKey, String baseUrl, String model, String lightModel) {
        this.chatClient = builder.build();
        this.lightChatClient = (lightModel == null || lightModel.isBlank())
                ? this.chatClient
                : builder.defaultOptions(org.springframework.ai.openai.OpenAiChatOptions.builder()
                        .withModel(lightModel.trim()).build()).build();
        this.mapper = mapper;
        this.apiKey = apiKey == null ? "" : apiKey;
        this.baseUrl = (baseUrl == null || baseUrl.isBlank())
                ? "https://ark.cn-beijing.volces.com/api/plan/v3" : baseUrl;
        this.model = (model == null || model.isBlank()) ? "ark-code-latest" : model;
    }

    @Override
    public CreateGoalResult createGoal(String userGoal, String scenarioType) {
        String prompt = """
                你是长期目标规划助手。根据用户目标，生成该场景的会话场景描述(session_desc)和初始待办任务列表。
                场景类型：%s
                用户目标：%s
                请为每个待办推断一个明确的到期/执行日期(yyyy-MM-dd)。今天是%tF，所有日期不得早于今天；若某步骤按计划应已发生则不要生成该任务。无法确定日期则为空字符串。并把每个任务归类到最匹配的关注项 key(focusArea，无匹配则为空字符串)。
                对“每天/每日/每周”这类要长期重复跟进的习惯或辅导（如每日饮食/食谱建议、每日训练、每日心情问候），要建成周期任务：
                recurrence 填 daily/weekly/monthly，dueDate 填今天(yyyy-MM-dd)，remindTime 填 24 小时制 HH:mm（用户说了几点就用几点，没说按场景给合理默认，如饮食建议 08:00、睡前问候 21:00）；一次性里程碑不要填 recurrence。
                aiBrief 字段：判断这条待办到点时是否需要“结合近况动态生成本次内容”（典型是周期性、辅导/建议类，如每天给今日食谱/训练动作/心情问候）。
                需要则用一句话写清要生成什么、参考什么近况（如“结合用户近几日饮食记录和减重目标，给出今日三餐建议，避免与近期重复”）；一次性里程碑、纯提醒、无需变化的待办 aiBrief 留空字符串。
                严格只输出一个JSON对象，不要任何解释、前后缀或代码块标记。
                格式：{"sessionDesc":"...","tasks":[{"content":"...","dueDate":"2026-08-07","focusArea":"prenatal_checkup","aiBrief":""},{"content":"每日饮食建议","dueDate":"2026-08-25","focusArea":"diet","recurrence":"daily","remindTime":"08:00","aiBrief":"结合近几日饮食记录给今日三餐建议"}]}
                metricDefs：如果这个目标有“需要长期追踪、适合在子对话里展示数值卡+趋势图”的可量化指标（如减重的体重/体脂率、考证的模考分数、学习时长），给出卡片数组：
                每张卡 key=小写下划线卡片标识，label=中文名，unit=单位，chartType=line/bar/pie，series=图内序列数组[{key,label}]（单指标给一条且 key 与卡片同名；一张图放多个相关指标时给多条，如热量消耗含静息/运动/总消耗）。
                输出 JSON 追加字段："metricDefs":[{"key":"weight","label":"当前体重","unit":"kg","chartType":"line","series":[{"key":"weight","label":"当前体重"}]}]
                """.formatted(scenarioType, userGoal, LocalDate.now(ZONE));
        Map<String, Object> m = callForObject(prompt);
        return new CreateGoalResult(str(m.get("sessionDesc")), toTaskItems(m.get("tasks")),
                toMetricDefs(m.get("metricDefs")));
    }

    @Override
    public String composeReminder(String sessionDesc, String taskContent, String aiBrief,
                                  List<String> recentDialog, LocalDate today) {
        String dialog = recentDialog == null || recentDialog.isEmpty() ? "（暂无近期对话）"
                : String.join("\n", recentDialog);
        String prompt = """
                你是用户的长期目标管家，现在到了一条定时跟进事项的推送时间。请结合用户近况，生成本次要主动推送给用户的内容。
                今天是 %tF。
                跟进事项：%s
                本次要生成的内容指令：%s
                目标场景：%s
                近期对话（供参考，避免与近期重复、要承接用户近况）：
                %s
                要求：
                - 直接输出可发给用户的正文（中文，口语、简洁、可执行），不要 JSON、不要标题、不要前后缀、不要说“这是AI生成”；
                - 给出具体可执行的建议/清单，不要泛泛而谈；若是食谱/训练等，直接给今天的具体内容；
                - 不要编造用户没提供的健康数据；信息不足就给通用建议并温和提示可补充的信息；
                - 控制在 150 字以内。
                """.formatted(today, taskContent, aiBrief,
                sessionDesc == null ? "" : sessionDesc, dialog);
        String content = chatClient.prompt().user(prompt).call().content();
        return content == null ? "" : content.strip();
    }

    @Override
    public AdjustTasksResult adjustTasks(String sessionDesc, List<TaskItem> currentTasks, String newMessage) {
        String prompt = """
                你是事项内任务调整助手。根据新信息更新待办任务列表：新增、删除、调整日期。
                场景描述：%s
                当前任务：%s
                新信息：%s
                输出规则：
                - 日期格式 yyyy-MM-dd，今天是%tF；一次性任务日期不得早于今天；无法确定具体日期则 dueDate 留空。
                - 对“每天/每日提醒X”这类周期性习惯，生成一条任务，dueDate 填今天（或下一个执行日），recurrence 填 daily；“每周”填 weekly，“每月”填 monthly，非周期留空。
                - 对周期性任务，remindTime 必填，为 24 小时制的 HH:mm：把用户说的“早上8点/晚上9点半/饭后”等自然语言换算成 HH:mm（晚上8点半=20:30，早上7点=07:00）；用户未指定时刻则填 "09:00"。一次性任务 remindTime 留空。
                - focusArea 只填“关注项 key”，必须从新信息中【可分配的关注项(key=名称)】清单里选等号左边的 key，严禁填中文名称；清单里没有匹配项就留空字符串。
                - detail 可填一句执行要点/准备事项，没有留空。
                - aiBrief：该待办到点需要结合近况动态生成本次内容（如每天给今日食谱/训练建议）时，用一句话写清生成什么；一次性里程碑、纯提醒留空字符串。已存在任务若原 aiBrief 仍适用请原样保留。
                - 如果新信息改变了关键日期（如预产期/检查日期变更），相应重排相关任务；用户明确停止/不再做的事项从列表删除。
                只输出 JSON：{"tasks":[{"content":"...","dueDate":"2026-08-07","focusArea":"skin_care","detail":"...","recurrence":"daily","remindTime":"20:30","aiBrief":"..."}]}
                """.formatted(sessionDesc, currentTasks, newMessage, LocalDate.now(ZONE));
        Map<String, Object> m = callForObject(prompt);
        return new AdjustTasksResult(toTaskItems(m.get("tasks")));
    }

    @Override
    public ExtractResult extractAndAssociate(String rawConversation, List<String> activeSessionDescs,
                                            String attributeSchema, String existingMemories) {
        String prompt = """
                你是长期记忆提炼与关联助手。
                从原始对话中提取所有具备长期价值、需要持久保存的信息，每条归为一个分类：
                - USER_INFO：用户基础档案（年龄、孕周/预产期、健康基线、身份角色等）
                - FACT：稳定不变的绝对事实（已发生/确定，如“已做无创DNA”“第一胎”）
                - PREFERENCE：偏好/喜好/沟通风格（如“不喜欢喝牛奶”“希望回复简短”）
                - CONTEXT：短期情景，会随时间变化（如“未来3个月无法学习”“最近孕吐严重”）
                通用字段（拿不准留空，不要编造）：
                - subject：主体标识，如 self/partner/baby/family/doctor；“我(准爸爸)青霉素过敏”主体是 self
                - subjectProfile：主体画像 JSON，如 {"role":"准爸爸","relatedParty":"孕妇"}
                - eventDate：事件日期 yyyy-MM-dd（预产期、检查日、考试日等）
                - validFrom/validTo：有效期；“未来3个月”“最近两周”等临时上下文务必推算 validTo，今天是%1$s
                - location：地点（医院/城市/机构/考点）
                - confidence：0~1，用户明确陈述为1.0，模型推断小于1.0
                - attributes：结构化属性数组，每个对象必须含 type。尽量使用下面“可用属性类型”中定义的 type 和字段；
                  若有价值但目录里没有完全匹配的类型，可自定义 type 并自由加字段（会被原样保留）。
                对每条记忆判断与哪些活跃子会话相关（会影响该场景计划/安排/状态就算相关），返回索引(从0开始)；都不相关返回空数组。
                过滤闲聊、临时问句、无长期价值内容；没有有效信息返回空数组。
                【待确认信息不沉淀】对话里被标注为“待确认/待锁定/临时记录/等你确认”的数值或状态（如刚报但尚未确认的体重、目标、日期），
                以及助手正文里“当前/已记录”之外的临时口径，都不是既成事实，不要提取为记忆；只有用户明确确认、或系统已锁定生效的信息才作为事实。
                【记忆冲突处理】下面列出了用户当前已有的有效记忆（带编号）。如果本次对话提供了更新/更正/矛盾的信息，使其中某些旧记忆已经过时或错误，
                请在 superseded_indexes 中返回这些旧记忆的编号；例如旧记忆说“预产期未确定”，本次确认了预产期，就把那条旧记忆编号列入。仅列出确实被取代的，不要列仍有效的。
                当前已有记忆（带编号）：
                %s
                可用属性类型（字段 schema）：
                %s
                原始对话：
                %s
                活跃子会话场景描述（带索引）：
                %s
                只输出 JSON，格式：{"memories":[...],"superseded_indexes":[0,2]}
                memories 元素格式：{"content":"...","category":"USER_INFO","subject":"self","subjectProfile":"","eventDate":"2026-12-15","validFrom":"","validTo":"","location":"","confidence":1.0,"attributes":[{"type":"pregnancy.profile","dueDate":"2026-12-15","gestationalWeek":7}],"associatedIndexes":[0]}
                """.formatted(
                        LocalDate.now(ZONE),
                        existingMemories == null || existingMemories.isBlank() ? "（无）" : existingMemories,
                        attributeSchema == null ? "" : attributeSchema,
                        rawConversation, renderIndexed(activeSessionDescs));
        Map<String, Object> m = callForObject(prompt);
        return new ExtractResult(toExtractedMemories(m.get("memories")), toIntList(m.get("superseded_indexes")));
    }

    @SuppressWarnings("unchecked")
    private List<ExtractedMemory> toExtractedMemories(Object o) {
        if (!(o instanceof List<?> list)) return List.of();
        List<ExtractedMemory> result = new ArrayList<>();
        for (Object e : list) {
            if (!(e instanceof Map<?, ?> mm)) continue;
            String content = str(mm.get("content"));
            if (content.isBlank()) continue;
            String category = str(mm.get("category"));
            Double confidence = toDouble(mm.get("confidence"));
            result.add(new ExtractedMemory(content, category,
                    str(mm.get("subject")), toJsonString(mm.get("subjectProfile")),
                    str(mm.get("eventDate")), str(mm.get("validFrom")), str(mm.get("validTo")),
                    str(mm.get("location")), confidence,
                    toAttributes(mm.get("attributes")),
                    toIntList(mm.get("associatedIndexes"))));
        }
        return result;
    }

    @Override
    public String streamChat(String systemPrompt, List<ChatMessage> messages, Consumer<String> onChunk) {
        List<Message> springMessages = new ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            springMessages.add(new SystemMessage(systemPrompt));
        }
        for (ChatMessage m : messages) {
            springMessages.add(switch (m.role()) {
                case "assistant" -> new AssistantMessage(m.content());
                case "system" -> new SystemMessage(m.content());
                default -> new UserMessage(m.content());
            });
        }

        Flux<String> flux = chatClient.prompt()
                .messages(springMessages)
                .stream()
                .content();

        StringBuilder full = new StringBuilder();
        flux.doOnNext(chunk -> {
                full.append(chunk);
                onChunk.accept(chunk);
            })
            .blockLast();
        return full.toString();
    }

    @Override
    public GoalIntent detectGoalIntent(List<ChatMessage> recentMessages, String userMessage, List<String> scenarioCatalog) {
        String catalog = String.join("\n", scenarioCatalog);
        StringBuilder conv = new StringBuilder();
        for (ChatMessage m : recentMessages) {
            conv.append(m.role()).append(": ").append(m.content()).append("\n");
        }
        String prompt = """
                你是智能管家的意图识别模块。判断用户这句话是否想创建一个新的长期目标/计划。
                可选场景类型，每个字段格式为 type | displayName | [必填/选填] key(label)：
                %s
                规则：
                1. collected 必须使用字段的英文 key，不要用中文 label。
                2. missingFields 只列出“必填”且用户未提供的字段 label；选填字段缺失不要列入。
                3. 只要所有必填字段都已提供，就把 wantsToCreate 设为 true 并准备创建，不要因为选填字段缺失而追问。
                4. 如果用户给了某日的孕周（如“7月26日是孕7周+1d”），请据此推算预产期（预产期≈该日期+（40周-当前孕周））并填入 collected 的对应日期字段，不要再追问预产期。
                5. 对于 select 类型字段（如孕期 role 身份），collected 的值必须从该字段给出的可选项中选一个原文；若用户表明“我是准爸爸/帮老婆问”，role 设为“准爸爸（男方）”，否则默认“准妈妈（女方）”。
                6. focusAreas：只有用户明确选择、点名或确认的关注项才返回 key；不要仅根据诉求自动补关注项。用户未明确提及则返回空数组（系统会使用默认/强制关注项）。
                7. 若用户想创建计划/目标，但不属于孕期、考研、考证等专门类型，scenarioType 设为 "generic"（通用计划，由后续分析拆解）；不要因为没有完全匹配的专门类型就返回空。
                输出 JSON：
                {"wantsToCreate":true/false,
                 "scenarioType":"匹配的 type，无法确定则为空字符串",
                 "title":"目标标题（简短）",
                 "collected":{key:"value"},
                 "missingFields":["仅缺失的必填字段 label"],
                 "focusAreas":["相关关注项 key"],
                 "reply":"自然语言回复：必填信息不足就追问；齐全就说正在创建"}
                只输出 JSON，不要输出其他内容。
                最近对话（用于判断用户是否在继续之前要创建目标的话题）：
                %s
                用户最新消息：%s
                """.formatted(catalog, conv.toString(), userMessage);
        Map<String, Object> m = callForObject(prompt, true);
        @SuppressWarnings("unchecked")
        Map<String, String> collected = (Map<String, String>) m.getOrDefault("collected", Map.of());
        return new GoalIntent(
                Boolean.TRUE.equals(m.get("wantsToCreate")),
                str(m.get("scenarioType")),
                str(m.get("title")),
                collected,
                toStrList(m.get("missingFields")),
                toStrList(m.get("focusAreas")),
                str(m.get("reply")));
    }

    @Override
    public ProposalReply classifyProposalReply(String proposalSummary, String userMessage) {
        String prompt = """
                用户此前收到一份“待确认的目标方案”，现在又说了一句话。请判断这句话的意图。
                待确认方案摘要：%s
                用户最新消息：%s
                只输出 JSON，不要解释：{"action":"confirm|modify|cancel|unrelated","instruction":"若为modify，用一句话概括用户要改什么；否则为空"}
                判定规则：
                - “确认/可以/没问题/就这样建/开始吧”等明确同意 → confirm；
                - “算了/不建了/取消/不要了” → cancel；
                - 补充、修改方案中的信息（如改考试时间、级别、城市），或提出新的要求 → modify，instruction 写明改动；
                - 与确认方案无关的闲聊/提问 → unrelated。
                """.formatted(proposalSummary == null ? "" : proposalSummary, userMessage);
        Map<String, Object> m = callForObject(prompt, true);
        return new ProposalReply(str(m.get("action")), str(m.get("instruction")));
    }

    @Override
    public ScenarioEvent extractScenarioEvent(String scenarioType, List<String> keyFieldHints,
                                             List<String> domainRuleHints,
                                             String collectedInfo, String newMessage, String existingMetrics,
                                             String assistantReply, String groundingText) {
        String fields = keyFieldHints == null ? "" : String.join("、", keyFieldHints);
        String domainRules = (domainRuleHints == null || domainRuleHints.isEmpty())
                ? "" : "\n本场景专属规则（优先遵守）：\n" + domainRuleHints.stream().map(h -> "- " + h).collect(java.util.stream.Collectors.joining("\n"));
        if (existingMetrics == null || existingMetrics.isBlank()) existingMetrics = "（暂无）";
        String msg = newMessage == null ? "" : newMessage;
        String grounding = (groundingText == null || groundingText.isBlank())
                ? "" : "\n【感知层上下文锚定（已消解时间/指代，时间归属以此为准）】\n" + groundingText + "\n";

        // 模型自选能力：能力手册常驻，模型先在 capabilities 声明本轮用到的能力，只填对应字段（不做后端关键词路由）。
        String replyBlock = (assistantReply == null || assistantReply.isBlank())
                ? "（本轮无助手回答，请仅依据用户消息判断）"
                : "助手本轮对用户的最终回答（其中已经算出的数值/结论是权威结果，结构化字段里的数值必须与此一致，不要另行估算）：\n"
                  + assistantReply;
        String prompt = """
                你是长期目标管家的“事件提取器”。根据用户最新消息和助手本轮回答，判断是否发生了需要沉淀到系统里的事件。
                场景类型：%s
                该场景的关键字段：%s
                已知收集信息：%s
                用户消息：%s
                %s
                只输出 JSON，不要解释，JSON 结构如下（未选中的能力对应字段省略或给空）：
                %s
                %s
                %s
                已有指标卡（key=名称；删除/合并时据此引用 metricRemove）：%s
                %s
                今天是%tF。
                """.formatted(scenarioType, fields, collectedInfo == null ? "" : collectedInfo, msg,
                replyBlock,
                grounding,
                EventPromptKernel.JSON_SCHEMA, EventPromptKernel.MANUAL, domainRules, existingMetrics,
                LocalDate.now(ZONE));
        Map<String, Object> m = callForObject(prompt);
        log.debug("事件提取 capabilities={} scenario={}", m.get("capabilities"), scenarioType);
        java.util.Map<String, String> fieldUpdates = toStringMap(m.get("fieldUpdates"));
        return new ScenarioEvent(fieldUpdates,
                toStrList(m.get("completedKeywords")),
                toStrList(m.get("enableFocusAreas")),
                toStrList(m.get("disableFocusAreas")),
                str(m.get("note")),
                Boolean.TRUE.equals(m.get("affectsTasks")),
                toMetricDefs(m.get("metricDefs")),
                toMetricPoints(m.get("metricPoints")),
                toStrList(m.get("metricRemove")));
    }

    private Map<String, Object> callForObject(String prompt) {
        return callForObject(prompt, false);
    }

    /**
     * 结构化 JSON 调用。light=true 走轻量级模型入口（锚定/意图判定/分类等轻任务）；
     * 未配置轻模型时 lightChatClient 即主模型，行为不变。
     */
    private Map<String, Object> callForObject(String prompt, boolean light) {
        ChatClient client = light ? lightChatClient : chatClient;
        String content = client.prompt().user(prompt).call().content();
        log.debug("LLM raw response: {}", content);
        String json = stripCodeFence(content);
        try {
            return mapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            throw new IllegalStateException("LLM 返回非合法 JSON: " + content, e);
        }
    }

    public Grounding groundContext(String userMessage, List<ChatMessage> recentMessages,
                                   String situationText, String focusCatalogText,
                                   List<ToolDef> tools, ToolExecutor toolExecutor) {
        String dialog = recentMessages == null || recentMessages.isEmpty() ? "（暂无历史）"
                : recentMessages.stream()
                    .map(m -> ("user".equals(m.role()) ? "用户：" : "管家：") + m.content())
                    .collect(java.util.stream.Collectors.joining("\n"));
        String situation = (situationText == null || situationText.isBlank()) ? "（无）" : situationText;
        String focus = (focusCatalogText == null || focusCatalogText.isBlank()) ? "（无）" : focusCatalogText;
        String prompt = """
                你是长期管家的“感知层上下文锚定器”。在回答和记账之前，先把用户这句话里的相对时间、指代、简称解析成绝对对象。
                今天是 %1$tF（%1$tA，Asia/Shanghai）。
                【用户最新一句】%2$s
                【近期对话（供消解指代，不要把里面其他日期的事件当成本轮新事件）】
                %3$s
                【当前处境/已收集信息】%4$s
                【本目标已注册关注项/待办语境】%5$s
                任务：像人一样先搞清楚“这句话说的是哪件事、发生在哪一天/哪一餐、跟谁有关、指的是哪条待办或哪个指标”。
                规则：
                - 时间一律锚定到绝对自然日 yyyy-MM-dd：“今天/刚刚/今早/现在”→今天；“昨天/昨晚”→昨天；“前天”→前天；明确说的日期照用；无法确定日期的事件 date 留空，禁止一律填今天。
                - 区分“本轮新发生、需要记账/处理的事件”与“只是在回顾、询问、提及过去”：前者 isNew=true，后者 isNew=false（例如“我昨天吃了火锅帮我算热量”是要把昨天那顿记到昨天；而“昨天那顿多少卡来着？”只是询问）。
                  像“到春节能不能减到165斤”这类面向未来命名时间点的提问/规划，把该时间点也锚定：先调用工具查清它的确切日期再填 date，isNew=false。
                - 一条数据/一顿饭/一次测量只归属它实际发生的那个自然日，绝不要把历史对话里其他日期的内容并入今天。
                - period 填早餐/午餐/晚餐/加餐/上午/下午/晚上 等；subject 填 self/partner/baby 等；refObject 填指代落到的具体待办/关注项/指标/地点（无则空）。
                - 拿不准的字段留空，绝不编造；这句话若纯属闲聊/提问、没有可锚定事件，events 返回空数组。
                你可以调用工具来完成锚定，分两类：①需要外部信息才能确定的对象——例如节日/节气/考试季在今年的确切公历日期、某地点归属的区划街道、某地址的坐标——调用联网搜索/地图查实后再填，不要凭记忆猜测；②凡是计算（数字运算、日期推算、星期几、相差几天/几周等），优先去 Calculator 计算工具里找对应能力取得结果，工具确实不支持的再自行推算，不要默认心算。
                只输出一个 JSON 对象（不要代码块、不要解释）：
                {"anchorTimeText":"本轮时间锚点的一句话说明（如：今天 2026-08-30 周日 早上；下一个春节是 2027-02-06）",
                 "isRetrospective":false,
                 "note":"一句话说明这句话主要在做什么（新汇报/提问/回顾…）",
                 "events":[{"kind":"meal|food|weight|exercise|study|task|fact","subject":"self","date":"2026-08-29","period":"早餐","refObject":"","summary":"今早吃了2个鸡蛋、一杯豆浆","isNew":true}]}
                """.formatted(LocalDate.now(ZONE), userMessage == null ? "" : userMessage,
                dialog, situation, focus);
        try {
            Map<String, Object> m = groundWithTools(prompt, tools, toolExecutor);
            List<GroundedEvent> events = new ArrayList<>();
            if (m.get("events") instanceof List<?> list) {
                for (Object e : list) {
                    if (!(e instanceof Map<?, ?> mm)) continue;
                    events.add(new GroundedEvent(str(mm.get("kind")), str(mm.get("subject")),
                            str(mm.get("date")), str(mm.get("period")), str(mm.get("refObject")),
                            str(mm.get("summary")), Boolean.TRUE.equals(mm.get("isNew"))));
                }
            }
            return new Grounding(str(m.get("anchorTimeText")), events,
                    Boolean.TRUE.equals(m.get("isRetrospective")), str(m.get("note")));
        } catch (Exception e) {
            log.warn("上下文锚定失败，降级为空锚点 err={}", e.getMessage());
            return new Grounding();
        }
    }

    /**
     * 锚定工具循环：锚定器像人一样，需要外部信息（节日/节气在今年的公历日期、地点归属、坐标等）
     * 时可调用工具查实，再输出最终锚定 JSON。无工具或模型不调用工具时等价于一次性抽取。
     */
    private Map<String, Object> groundWithTools(String prompt, List<ToolDef> tools, ToolExecutor executor) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatMessage("user", prompt));
        List<ToolDef> available = (tools == null) ? List.of() : tools;
        for (int step = 0; step < 4; step++) {
            ToolChatResult result = toolChatStep(messages, available);
            if (!result.hasToolCalls()) {
                return parseObject(result.content());
            }
            messages.add(new ChatMessage("assistant", result.content() == null ? "" : result.content(),
                    result.toolCalls(), null));
            for (ToolCall call : result.toolCalls()) {
                String output;
                try {
                    output = executor == null ? "工具不可用" : executor.execute(call.name(), call.argumentsJson());
                } catch (Exception e) {
                    output = "工具执行失败：" + e.getMessage();
                }
                messages.add(new ChatMessage("tool", output, null, call.id()));
            }
        }
        ToolChatResult finalResult = toolChatStep(messages, List.of());
        return parseObject(finalResult.content());
    }

    private ToolChatResult toolChatStep(List<ChatMessage> messages, List<ToolDef> tools) {
        return toolChat("", messages, tools);
    }

    private Map<String, Object> parseObject(String content) {
        String json = stripCodeFence(content);
        try {
            return mapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            throw new IllegalStateException("锚定返回非合法 JSON: " + content, e);
        }
    }

    private String renderIndexed(List<String> descs) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < descs.size(); i++) {
            sb.append(i).append(". ").append(descs.get(i)).append("\n");
        }
        return sb.toString();
    }

    private static String str(Object o) { return o == null ? "" : o.toString(); }

    private List<String> toStrList(Object o) {
        if (o instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of();
    }

    private Map<String, String> toStringMap(Object o) {
        if (!(o instanceof Map<?, ?> map)) return Map.of();
        Map<String, String> result = new java.util.LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                result.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static String normalizeRecurrence(String raw) {
        if (raw == null) return "";
        String v = raw.trim().toLowerCase();
        return switch (v) {
            case "daily", "every_day", "每天", "每日" -> "daily";
            case "weekly", "every_week", "每周" -> "weekly";
            case "biweekly", "every_two_weeks", "每两周" -> "biweekly";
            case "monthly", "every_month", "每月" -> "monthly";
            default -> v.isBlank() || "none".equals(v) || "once".equals(v) ? "" : v;
        };
    }

    private List<TaskItem> toTaskItems(Object o) {
        if (!(o instanceof List<?> list)) return List.of();
        List<TaskItem> result = new ArrayList<>();
        for (Object e : list) {
            if (e instanceof Map<?, ?> mm) {
                String content = str(mm.get("content"));
                if (content.isBlank()) continue;
                String due = str(mm.get("dueDate"));
                if (due.isBlank()) due = str(mm.get("due_date"));
                result.add(new TaskItem(content, due, str(mm.get("focusArea")),
                        str(mm.get("detail")), normalizeRecurrence(str(mm.get("recurrence"))),
                        str(mm.get("remindTime")), str(mm.get("aiBrief"))));
            } else if (e != null) {
                result.add(new TaskItem(String.valueOf(e), "", "", "", "", ""));
            }
        }
        return result;
    }

    private List<MetricDef> toMetricDefs(Object o) {
        if (!(o instanceof List<?> list)) return List.of();
        List<MetricDef> result = new ArrayList<>();
        for (Object e : list) {
            if (!(e instanceof Map<?, ?> mm)) continue;
            String key = str(mm.get("key")).trim();
            if (key.isBlank()) continue;
            String chart = str(mm.get("chartType")).trim().toLowerCase();
            if (!chart.equals("line") && !chart.equals("bar") && !chart.equals("pie")) chart = "line";
            List<MetricSeries> series = toMetricSeries(mm.get("series"), key, str(mm.get("label")));
            result.add(new MetricDef(key, str(mm.get("label")), str(mm.get("unit")), chart, series));
        }
        return result;
    }

    private List<MetricSeries> toMetricSeries(Object o, String defaultKey, String defaultLabel) {
        if (!(o instanceof List<?> list) || list.isEmpty()) {
            return List.of(new MetricSeries(defaultKey, defaultLabel));
        }
        List<MetricSeries> result = new ArrayList<>();
        for (Object e : list) {
            if (!(e instanceof Map<?, ?> mm)) continue;
            String sk = str(mm.get("key")).trim();
            if (sk.isBlank()) continue;
            String sl = str(mm.get("label"));
            result.add(new MetricSeries(sk, sl.isBlank() ? sk : sl));
        }
        return result.isEmpty() ? List.of(new MetricSeries(defaultKey, defaultLabel)) : result;
    }

    private List<MetricPointIn> toMetricPoints(Object o) {
        if (!(o instanceof List<?> list)) return List.of();
        List<MetricPointIn> result = new ArrayList<>();
        for (Object e : list) {
            if (!(e instanceof Map<?, ?> mm)) continue;
            String key = str(mm.get("key")).trim();
            Double value = toDouble(mm.get("value"));
            if (key.isBlank() || value == null) continue;
            result.add(new MetricPointIn(key, value, str(mm.get("date"))));
        }
        return result;
    }

    private Double toDouble(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(String.valueOf(o)); } catch (Exception e) { return null; }
    }

    private String toJsonString(Object o) {
        if (o == null) return "";
        if (o instanceof String st) return st;
        try { return mapper.writeValueAsString(o); } catch (Exception e) { return ""; }
    }

    @SuppressWarnings("unchecked")
    private List<com.butler.domain.attribute.Attribute> toAttributes(Object o) {
        if (o == null) return List.of();
        try {
            return mapper.convertValue(o, new com.fasterxml.jackson.core.type.TypeReference<List<com.butler.domain.attribute.Attribute>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private List<Integer> toIntList(Object o) {
        if (o instanceof List<?> list) {
            return list.stream().map(x -> ((Number) x).intValue()).toList();
        }
        return List.of();
    }

    private static String stripCodeFence(String s) {
        if (s == null) return "{}";
        String t = s.trim();
        if (t.startsWith("```")) {
            t = t.replaceFirst("^```[a-zA-Z]*", "").replaceFirst("```$", "").trim();
        }
        return t.isBlank() ? "{}" : t;
    }

    @Override
    public ToolChatResult toolChat(String systemPrompt, List<ChatMessage> messages, List<ToolDef> tools) {
        try {
            com.fasterxml.jackson.databind.node.ObjectNode body = mapper.createObjectNode();
            body.put("model", model);
            body.put("temperature", 0.3);
            com.fasterxml.jackson.databind.node.ArrayNode msgs = body.putArray("messages");
            com.fasterxml.jackson.databind.node.ObjectNode sys = msgs.addObject();
            sys.put("role", "system");
            sys.put("content", systemPrompt == null ? "" : systemPrompt);
            for (ChatMessage m : messages) {
                com.fasterxml.jackson.databind.node.ObjectNode mo = msgs.addObject();
                mo.put("role", m.role());
                mo.put("content", m.content() == null ? "" : m.content());
                if (m.toolCalls() != null && !m.toolCalls().isEmpty()) {
                    com.fasterxml.jackson.databind.node.ArrayNode tcs = mo.putArray("tool_calls");
                    for (ToolCall tc : m.toolCalls()) {
                        com.fasterxml.jackson.databind.node.ObjectNode tco = tcs.addObject();
                        tco.put("id", tc.id() == null ? "" : tc.id());
                        tco.put("type", "function");
                        tco.putObject("function").put("name", tc.name())
                                .put("arguments", tc.argumentsJson() == null ? "{}" : tc.argumentsJson());
                    }
                }
                if (m.toolCallId() != null) mo.put("tool_call_id", m.toolCallId());
            }
            if (tools != null && !tools.isEmpty()) {
                com.fasterxml.jackson.databind.node.ArrayNode arr = body.putArray("tools");
                for (ToolDef t : tools) {
                    com.fasterxml.jackson.databind.node.ObjectNode fn = arr.addObject();
                    fn.put("type", "function");
                    com.fasterxml.jackson.databind.node.ObjectNode f = fn.putObject("function");
                    f.put("name", t.name());
                    f.put("description", t.description());
                    f.set("parameters", mapper.readTree(t.parametersSchemaJson()));
                }
            }
            java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(baseUrl + "/chat/completions"))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .timeout(java.time.Duration.ofSeconds(60))
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                    .build();
            java.net.http.HttpResponse<String> resp = http.send(req,
                    java.net.http.HttpResponse.BodyHandlers.ofString(java.nio.charset.StandardCharsets.UTF_8));
            com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(resp.body());
            com.fasterxml.jackson.databind.JsonNode choice = root.path("choices").path(0);
            com.fasterxml.jackson.databind.JsonNode msg = choice.path("message");
            List<ToolCall> calls = new java.util.ArrayList<>();
            for (com.fasterxml.jackson.databind.JsonNode tc : msg.path("tool_calls")) {
                String id = tc.path("id").asText();
                String name = tc.path("function").path("name").asText();
                String args = tc.path("function").path("arguments").asText("{}");
                calls.add(new ToolCall(id, name, args));
            }
            String content = msg.path("content").asText("");
            return new ToolChatResult(content, calls);
        } catch (Exception e) {
            log.warn("工具对话失败 err={}", e.getMessage());
            return new ToolChatResult("（工具调用失败：" + e.getMessage() + "）", List.of());
        }
    }
}
