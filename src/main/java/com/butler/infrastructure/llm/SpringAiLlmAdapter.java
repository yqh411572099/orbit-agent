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
    private final ObjectMapper mapper;
    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final java.net.http.HttpClient http = java.net.http.HttpClient.newBuilder()
            .connectTimeout(java.time.Duration.ofSeconds(10)).build();

    public SpringAiLlmAdapter(ChatClient.Builder builder, ObjectMapper mapper) {
        this(builder, mapper, "", "", "");
    }

    public SpringAiLlmAdapter(ChatClient.Builder builder, ObjectMapper mapper,
                              String apiKey, String baseUrl, String model) {
        this.chatClient = builder.build();
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
                metricDefs：如果这个目标有“需要长期追踪、适合在子对话里展示数值卡+趋势图”的可量化指标（如减重的体重/体脂率、考证的模考分数、学习时长），给出指标定义数组：
                key=小写下划线标识，label=中文名，unit=单位，chartType=趋势用 line、占比用 pie、离散对比用 bar；没有则为空数组。
                输出 JSON 追加字段："metricDefs":[{"key":"weight","label":"当前体重","unit":"kg","chartType":"line"}]
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
                - focusArea 为任务所属关注项 key（用户新增的关注项用其 key），无匹配留空。
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
        Map<String, Object> m = callForObject(prompt);
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
        Map<String, Object> m = callForObject(prompt);
        return new ProposalReply(str(m.get("action")), str(m.get("instruction")));
    }

    @Override
    public ScenarioEvent extractScenarioEvent(String scenarioType, List<String> keyFieldHints,
                                             String collectedInfo, String newMessage) {
        String fields = keyFieldHints == null ? "" : String.join("、", keyFieldHints);
        String prompt = """
                你是长期目标管家的“事件提取器”。根据用户最新消息，判断是否发生了需要调整目标计划的事件。
                场景类型：%s
                该场景的关键字段：%s
                已知收集信息：%s
                用户消息：%s
                只输出 JSON，不要解释：
                {"fieldUpdates":{"字段key":"新值"},"completedKeywords":["已完成事项关键词"],"enableFocusAreas":["新增关注项key"],"disableFocusAreas":["关闭关注项key"],"affectsTasks":true/false,"note":"一句话说明变更"}
                规则：
                - 关键日期变更（如预产期/考试日改到X、根据B超现在是N周）放入 fieldUpdates，日期用 yyyy-MM-dd；无法精确换算日期时也尽量给出日期，今天是%tF。
                - 用户确定某里程碑节点的实际预约/执行日期时（如“NT约到8月20号”“8月7日做早孕B超”“建档定在X日”），把日期写入对应的 milestone_<节点key>_date 字段（关键字段列表里以“预约/实际执行日期”结尾的那些），不要写到预产期/孕周字段；日期用 yyyy-MM-dd，缺年份按今天所在年份判断。
                - 严禁仅凭某检查的预约日期反推预产期或当前孕周（例如“NT约到8月20号”不等于“今天是12周”）；只有用户明确说“今天/现在孕N周”或“预产期改到X”时才更新 dueDate/currentWeek。
                - 用户表示某项检查/任务已完成，把该事项关键词放入 completedKeywords。
                - enableFocusAreas/disableFocusAreas 由你做语义判断，不要做关键词正则匹配：只有当用户明确表达“要新增/开启某个关注项、希望持续被提醒某类事项”时，才把对应关注项 key 放入 enableFocusAreas；只有当用户明确表达“不再关注/关闭某类提醒”时才放入 disableFocusAreas。
                - 例：“给我建个关注项，每天提醒我抹妊娠油”应判定为新增“皮肤与身体护理”关注项；而“NT约到8月20号”“今天做了大排畸”只是日期/状态更新，不得新增关注项。
                - 关键字段列表里以"(关注项:xxx)"标注的是该场景内置关注项，启用/关闭时直接用其 key。
                - 用户要新增的关注项不在内置列表里时，enableFocusAreas 用 "custom_key|中文名称" 格式：custom_key 用小写英文蛇形命名（如 skin_care），中文名称即用户表述的关注项名（如 皮肤与身体护理）。内置项不要带名称后缀。
                - 没有增删意图时两个数组都为空。
                - affectsTasks（布尔）：只有当用户这句话确实在“新增/修改/删除/调整待办、提醒、关注项，或改动提醒时间/周期”时才为 true；纯咨询、提问、闲聊、查询政策/怎么办理、让你解释或推荐等都为 false。判断要保守，拿不准就 false。
                - metricDefs：当用户希望在子对话里持续看到某个可量化指标卡/图表（如“右上角展示我的体重”“记录每天体重变化”“追踪我的模考分数”）时，给出该指标定义数组：
                  key=小写下划线指标标识（weight/body_fat/mock_score…），label=中文名（当前体重/体脂率/模考分数），unit=单位（kg、百分号、分），
                  chartType=适合的图表类型：随时间变化的单值趋势用 line（体重、分数），对比构成占比用 pie，少量离散对比用 bar；没有这类诉求返回空数组。
                - metricPoints：用户这次明确汇报了可记录的数值（如“今天体重78.5公斤”“这次考了128分”）时，给出数据点数组：
                  key=对应 metricDefs 的 key（未定义就用合适的新 key），value=数值（数字），date=该数据日期 yyyy-MM-dd（用户说“今天/没说日期”就用今天）。只是表达想记录的意愿而没给数值，不要编造，返回空数组。
                  unit 与 value 保持一致：用户用什么单位报数（斤/kg/分…），指标定义的 unit 就用该单位、value 就填用户报的原始数值，不要自行换算单位。
                - 没有任何变化时所有字段返回空（affectsTasks 为 false）。
                输出 JSON 在原字段基础上追加："metricDefs":[{"key":"weight","label":"当前体重","unit":"kg","chartType":"line"}],"metricPoints":[{"key":"weight","value":78.5,"date":"2026-08-27"}]
                """.formatted(scenarioType, fields, collectedInfo == null ? "" : collectedInfo, newMessage, LocalDate.now(ZONE));
        Map<String, Object> m = callForObject(prompt);
        java.util.Map<String, String> fieldUpdates = toStringMap(m.get("fieldUpdates"));
        return new ScenarioEvent(fieldUpdates,
                toStrList(m.get("completedKeywords")),
                toStrList(m.get("enableFocusAreas")),
                toStrList(m.get("disableFocusAreas")),
                str(m.get("note")),
                Boolean.TRUE.equals(m.get("affectsTasks")),
                toMetricDefs(m.get("metricDefs")),
                toMetricPoints(m.get("metricPoints")));
    }

    private Map<String, Object> callForObject(String prompt) {
        String content = chatClient.prompt().user(prompt).call().content();
        log.debug("LLM raw response: {}", content);
        String json = stripCodeFence(content);
        try {
            return mapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            throw new IllegalStateException("LLM 返回非合法 JSON: " + content, e);
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
            result.add(new MetricDef(key, str(mm.get("label")), str(mm.get("unit")), chart));
        }
        return result;
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
