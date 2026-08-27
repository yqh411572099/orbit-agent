package com.butler.application;

import com.butler.domain.model.*;
import com.butler.application.PendingGoalProposalStore.GoalProposal;
import com.butler.application.PendingGoalProposalStore.Section;
import com.butler.application.PendingGoalProposalStore.Row;
import com.butler.domain.agent.ToolCategory;
import com.butler.domain.agent.ToolContext;
import com.butler.domain.agent.ToolRegistry;
import com.butler.domain.repository.*;
import com.butler.domain.scenario.ScenarioDomain;
import com.butler.domain.scenario.ScenarioRegistry;
import com.butler.domain.service.MemoryPermissionService;
import com.butler.infrastructure.llm.LlmPort;
import com.butler.memory.MemoryContextAssembler;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChatAppService {

    private static final int HISTORY_LIMIT = 20;

    private final RawChatLogRepository rawChatLogRepository;
    private final SubSessionRepository subSessionRepository;
    private final MemoryPermissionService permissionService;
    private final MemoryContextAssembler memoryContextAssembler;
    private final ScenarioRegistry scenarioRegistry;
    private final GoalAppService goalAppService;
    private final LlmPort llmPort;
    private final ConversationAppService conversationAppService;
    private final ToolRegistry toolRegistry;
    private final MainSessionRepository mainSessionRepository;
    private final PendingGoalProposalStore pendingGoalProposalStore;
    private final ObjectMapper objectMapper;
    private static final int MAX_TOOL_CALLS = 5;

    /** SSE 事件回调：chunk=文本增量，goal_created=已创建子对话，done=结束。 */
    public interface ChatListener {
        void onChunk(String text);
        /** 思考过程（工具选择/检索结果摘要），前端以浅灰可折叠样式展示。 */
        default void onReasoning(String text) {}
        default void onGoalCreated(Long subSessionId, String scenarioType, String title) {}
        /** 建目标前联网调研完成，前端展示确认卡。 */
        default void onGoalProposal(String proposalId, GoalProposal proposal) {}
        /** 子对话用户输入解析出待确认的结构化变更；前端弹窗确认后再调用 apply 接口。 */
        default void onProposal(ChangePreview preview) {}
    }

    public ChatAppService(RawChatLogRepository rawChatLogRepository,
                          SubSessionRepository subSessionRepository,
                          MemoryPermissionService permissionService,
                          MemoryContextAssembler memoryContextAssembler,
                          ScenarioRegistry scenarioRegistry,
                          GoalAppService goalAppService,
                          LlmPort llmPort,
                          ConversationAppService conversationAppService,
                          ToolRegistry toolRegistry,
                          MainSessionRepository mainSessionRepository,
                          PendingGoalProposalStore pendingGoalProposalStore,
                          ObjectMapper objectMapper) {
        this.rawChatLogRepository = rawChatLogRepository;
        this.subSessionRepository = subSessionRepository;
        this.permissionService = permissionService;
        this.memoryContextAssembler = memoryContextAssembler;
        this.scenarioRegistry = scenarioRegistry;
        this.goalAppService = goalAppService;
        this.llmPort = llmPort;
        this.conversationAppService = conversationAppService;
        this.toolRegistry = toolRegistry;
        this.mainSessionRepository = mainSessionRepository;
        this.pendingGoalProposalStore = pendingGoalProposalStore;
        this.objectMapper = objectMapper;
    }

    /** 保存用户级定位（浏览器授权获取），供所有子对话/工具回退使用。 */
    @Transactional
    public void saveUserLocation(Long userId, String city, String latitude, String longitude) {
        MainSession ms = mainSessionRepository.findByUserId(userId).orElse(null);
        if (ms == null) {
            ms = new MainSession(null, userId, Instant.now(), city, latitude, longitude);
        } else {
            ms = new MainSession(ms.getId(), userId, ms.getCreatedAt(),
                    city != null ? city : ms.getCity(),
                    latitude != null ? latitude : ms.getLatitude(),
                    longitude != null ? longitude : ms.getLongitude());
        }
        mainSessionRepository.save(ms);
    }

    /** 子对话自身无坐标时，回退到用户级定位，避免重复索要。 */
    private java.util.Map<String, String> withUserLocationFallback(Long userId, java.util.Map<String, String> collected) {
        if (collected != null && isNotBlank(collected.get("latitude")) && isNotBlank(collected.get("longitude"))) {
            return collected;
        }
        MainSession ms = mainSessionRepository.findByUserId(userId).orElse(null);
        if (ms == null) return collected == null ? java.util.Map.of() : collected;
        java.util.Map<String, String> merged = new java.util.LinkedHashMap<>(collected == null ? java.util.Map.of() : collected);
        if (isBlank(merged.get("latitude")) && isNotBlank(ms.getLatitude())) merged.put("latitude", ms.getLatitude());
        if (isBlank(merged.get("longitude")) && isNotBlank(ms.getLongitude())) merged.put("longitude", ms.getLongitude());
        if (isBlank(merged.get("city")) && isNotBlank(ms.getCity())) merged.put("city", ms.getCity());
        return merged;
    }

    private static boolean isBlank(String s) { return s == null || s.isBlank(); }
    private static boolean isNotBlank(String s) { return !isBlank(s); }

    @Transactional
    public List<RawChatLog> history(Long userId, SessionType type, Long subSessionId) {
        List<RawChatLog> all = rawChatLogRepository
                .findByUserIdAndCreatedAtBetween(userId, Instant.EPOCH, Instant.now()).stream()
                .filter(l -> l.getSessionType() == type)
                .filter(l -> type == SessionType.MAIN || l.getSubSessionId() != null && l.getSubSessionId().equals(subSessionId))
                .sorted((a, b) -> a.getCreatedAt().compareTo(b.getCreatedAt()))
                .toList();
        int from = Math.max(0, all.size() - HISTORY_LIMIT);
        return all.subList(from, all.size());
    }

    private static final Logger log = LoggerFactory.getLogger(ChatAppService.class);

    public String chat(Long userId, SessionType type, Long subSessionId, String content, ChatListener listener) {
        // 用户消息独立事务先落库，后续任何异常都不回滚
        conversationAppService.saveMessage(userId, type, subSessionId, "user", content);
        // 累积本轮思考过程，随助手回复一起落库，刷新后仍可查看
        StringBuilder reasoningAcc = new StringBuilder();
        final boolean[] proposalEmitted = {false};
        ChatListener wrapped = new ChatListener() {
            @Override public void onChunk(String text) { try { listener.onChunk(text); } catch (Exception ignored) {} }
            @Override public void onReasoning(String text) {
                if (!reasoningAcc.isEmpty()) reasoningAcc.append("\n");
                reasoningAcc.append(text);
                try { listener.onReasoning(text); } catch (Exception ignored) {}
            }
            @Override public void onGoalCreated(Long sid, String scenarioType, String title) {
                try { listener.onGoalCreated(sid, scenarioType, title); } catch (Exception ignored) {}
            }
            @Override public void onGoalProposal(String proposalId, GoalProposal proposal) {
                proposalEmitted[0] = true;
                try { listener.onGoalProposal(proposalId, proposal); } catch (Exception ignored) {}
            }
            @Override public void onProposal(ChangePreview preview) {
                try { listener.onProposal(preview); } catch (Exception ignored) {}
            }
        };
        try {
            if (type == SessionType.SUB && subSessionId != null) {
                ChangePreview proposal = conversationAppService.proposeSubSessionChange(subSessionId, content);
                if (proposal != null && !proposal.isEmpty()) {
                    wrapped.onProposal(proposal);
                }
            }
            String reply = type == SessionType.MAIN
                    ? handleMain(userId, content, wrapped)
                    : streamReply(userId, type, subSessionId, content, wrapped);
            // 建目标确认卡本身就是这一轮的产出，不再额外落一条重复的助手文本
            if (!proposalEmitted[0]) {
                if (reply == null || reply.isBlank()) reply = "我暂时没有整理出回答，可以换个说法再试试。";
                conversationAppService.saveMessage(userId, type, subSessionId, "assistant", reply,
                        reasoningAcc.length() == 0 ? null : reasoningAcc.toString());
            }
            return reply;
        } catch (Exception e) {
            log.error("chat processing failed: type={} sub={} content={}", type, subSessionId, content, e);
            String err = "系统异常，请稍后重试。";
            try { listener.onChunk(err); } catch (Exception ignored) {}
            conversationAppService.saveMessage(userId, type, subSessionId, "assistant", err,
                    reasoningAcc.length() == 0 ? null : reasoningAcc.toString());
            return err;
        }
    }

    /** 主对话：先判断是否要创建目标，信息不足则追问，齐全则创建子对话。 */
    private String handleMain(Long userId, String content, ChatListener listener) {
        // 若存在待确认的建目标方案，先判断用户是确认/修改/取消，避免把确认当成新需求。
        PendingGoalProposalStore.StoredProposal pending = pendingGoalProposalStore.findLatestByUser(userId);
        if (pending != null) {
            String summary = pending.proposal().title() + " " + pending.proposal().goalText();
            LlmPort.ProposalReply reply = llmPort.classifyProposalReply(summary, content);
            if (reply.isConfirm()) {
                return confirmGoalProposal(userId, pending, listener);
            }
            if (reply.isCancel()) {
                pendingGoalProposalStore.remove(pending.id());
                String msg = "好的，已取消创建这个计划。需要时随时告诉我。";
                streamPreset(msg, listener);
                return msg;
            }
            if (reply.isModify()) {
                // 带着原方案和用户的修改意见重新调研、再确认
                ScenarioDomain domain = scenarioRegistry.get(pending.proposal().scenarioType());
                listener.onReasoning("🧠 收到修改意见，重新联网核实并更新方案…");
                GoalProposal revised = researchGoalProposal(userId, domain,
                        pending.proposal().goalText() + "\n用户补充/修改：" + content
                                + "\n（请据此更新方案，未提及的项沿用上次结论并再次核实）", listener);
                if (revised != null) {
                    var stored = pendingGoalProposalStore.put(userId, revised);
                    pendingGoalProposalStore.remove(pending.id());
                    listener.onGoalProposal(stored.id(), revised);
                    String announce = proposalAnnounce(revised);
                    streamPreset(announce, listener);
                    return announce;
                }
            }
            // unrelated：丢弃旧方案，继续按普通主对话处理
            pendingGoalProposalStore.remove(pending.id());
        }
        List<String> catalog = scenarioRegistry.all().stream()
                .map(d -> {
                    String fields = d.collectFields().stream()
                            .map(f -> {
                                String base = (f.required() ? "[必填]" : "[选填]") + " " + f.key() + "(" + f.label() + ")";
                                if (f.type() == ScenarioDomain.FieldType.SELECT && !f.options().isEmpty()) {
                                    base += " 可选值:" + String.join("/", f.options());
                                }
                                return base;
                            }).toList().toString();
                    String focus = d.focusAreas().isEmpty() ? ""
                            : " 关注项key:" + d.focusAreas().stream()
                                .map(f -> f.key() + "(" + f.label() + "[" + f.audience() + "])").toList();
                    return d.type() + " | " + d.displayName() + " | " + fields + focus;
                })
                .toList();

        List<LlmPort.ChatMessage> recent = history(userId, SessionType.MAIN, null).stream()
                .map(l -> new LlmPort.ChatMessage(l.getRole(), l.getContent()))
                .toList();
        listener.onReasoning("🧠 理解你的需求，判断是否需要创建专属计划…");
        LlmPort.GoalIntent intent = llmPort.detectGoalIntent(recent, content, catalog);
        if (intent.wantsToCreate() && intent.scenarioType() != null) {
            listener.onReasoning("识别到想创建「" + intent.scenarioType() + "」计划，正在检查信息是否齐全…");
        } else {
            listener.onReasoning("属于日常对话，直接回答。");
        }

        if (intent.wantsToCreate() && intent.scenarioType() != null
                && scenarioRegistry.supports(intent.scenarioType())) {
            ScenarioDomain domain = scenarioRegistry.get(intent.scenarioType());
            java.util.Map<String,String> collected = intent.collected() == null
                    ? java.util.Map.of() : intent.collected();
            List<String> requiredMissing = domain.collectFields().stream()
                    .filter(ScenarioDomain.CollectField::required)
                    .filter(f -> {
                        String v = collected.get(f.key());
                        if (v == null || v.isBlank()) v = collected.get(f.label());
                        return v == null || v.isBlank();
                    })
                    .map(ScenarioDomain.CollectField::label)
                    .toList();
            if (requiredMissing.isEmpty()) {
                if (domain.researchBeforeCreate()) {
                    // 需要联网调研的场景：先查清信息、出确认卡，用户确认后再建目标
                    listener.onReasoning("🧭 这类目标需要先联网核实报考信息，正在调研，请稍候…");
                    String seed = content + (collected.isEmpty() ? "" : "\n已收集：" + collected);
                    GoalProposal proposal = researchGoalProposal(userId, domain, seed, listener);
                    if (proposal == null) {
                        String fail = "我暂时没能查到足够的报考信息，你可以补充具体证书名称/考试时间，我再查一次。";
                        streamPreset(fail, listener);
                        return fail;
                    }
                    var stored = pendingGoalProposalStore.put(userId, proposal);
                    listener.onGoalProposal(stored.id(), proposal);
                    return "请核对确认卡信息";
                }
                // 必填信息齐全：直接创建子对话
                String goal = content;
                List<String> focus = intent.focusAreas() == null ? List.of() : intent.focusAreas();
                SubSession sub = goalAppService.createGoal(
                        userId, domain.type(), intent.title(), goal, collected, focus);
                String announce = """
                        好的，已为你创建「%s」专属计划（子对话 #%d）。
                        我会在这个专属会话里帮你拆解任务、跟进进度，正在为你生成初始规划…"""
                        .formatted(domain.displayName(), sub.getId());
                streamPreset(announce, listener);
                listener.onGoalCreated(sub.getId(), domain.type(), intent.title());
                return announce;
            } else {
                // 必填信息不足：追问
                String ask = intent.reply() != null && !intent.reply().isBlank()
                        ? intent.reply()
                        : "为了帮你制定「" + domain.displayName() + "」计划，我还需要了解："
                                + String.join("、", requiredMissing) + "。";
                streamPreset(ask, listener);
                return ask;
            }
        }

        // 非创建意图：正常主对话
        return streamReply(userId, SessionType.MAIN, null, content, listener);
    }

    /** 用联网工具调研目标信息，产出结构化待确认方案。 */
    private GoalProposal researchGoalProposal(Long userId, ScenarioDomain domain, String seed, ChatListener listener) {
        List<LlmPort.ToolDef> tools = toolRegistry.forScenario(domain.toolCategories()).stream()
                .map(c -> new LlmPort.ToolDef(c.name(), c.description(), c.parametersSchema()))
                .toList();
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
        String locHint = locationHint(userId);
        String toolLead = tools.isEmpty()
                ? "请基于用户诉求分析并给出方案；涉及你不确定的实时信息（政策/入口/日期），在对应行标注 uncertain。"
                : "你可以调用联网搜索/知识库工具查清楚再给方案，不要凭记忆编造日期、入口或政策。";
        String fieldsHint = domain.collectFields().isEmpty()
                ? "该场景无固定字段：collected 用于放你从诉求中提炼的关键参数（目标日期、数量、地点、人物角色等），key 用英文驼峰，value 用文本，日期用 yyyy-MM-dd。"
                : "collected 只能使用该场景收集字段的 key："
                    + domain.collectFields().stream().map(f -> f.key() + "(" + f.label() + ")").toList();
        String systemPrompt = """
                你是用户的个人目标规划助理。用户想创建一个「%s」目标。%s
                %s

                今天是 %tF。%s 最终只输出一个 JSON 对象（不要代码块、不要解释）：
                {
                  "title": "简短目标标题",
                  "goalText": "一句话描述这个目标",
                  "collected": { "字段key": "值" },
                  "focusAreas": [ "方面1", {"key":"可选","label":"方面2"} ],
                  "sections": [ {"icon":"📌","title":"分组","rows":[{"label":"...","value":"...","uncertain":false}]} ],
                  "materials": [ {"title":"...","url":"..."} ]
                }
                - focusAreas：需要分块跟进的“方面/关注项”（最多6个），每一项后续会成为一个独立的待办分组；可以是字符串，或 {"key":"英文标识","label":"中文名"}。
                - sections：用中文分 2-5 组，向用户展示你的分析（目标拆解、关键参数、里程碑/时间节点、风险或注意事项）；不确定的信息 uncertain=true 并标注“待确认”。
                - %s
                - %s
                %s
                %s
                """.formatted(domain.displayName(), toolLead, domain.researchBrief(), today,
                tools.isEmpty() ? "分析后" : "调用工具核实后",
                "materials：相关资料链接（报名入口、教材、政策官网等），无则返回空数组。",
                fieldsHint, domain.researchOutputHint(), locHint);

        List<LlmPort.ChatMessage> messages = new ArrayList<>();
        messages.add(new LlmPort.ChatMessage("user", seed));
        ToolContext ctx = new ToolContext(userId, "main", null, domain.type(),
                Map.of("today", today.toString()), today, true);
        String finalContent = "";
        int maxToolRounds = 4; // 建前调研允许的联网轮次，避免无限堆叠工具
        for (int step = 0; step < maxToolRounds; step++) {
            LlmPort.ToolChatResult result = llmPort.toolChat(systemPrompt, messages, tools);
            if (!result.hasToolCalls()) {
                finalContent = result.content() == null ? "" : result.content();
                break;
            }
            messages.add(new LlmPort.ChatMessage("assistant", result.content() == null ? "" : result.content(),
                    result.toolCalls(), null));
            // 同一轮里模型可能同时发起多个独立搜索，并行执行，避免串行累加等待时间。
            List<LlmPort.ToolCall> calls = result.toolCalls();
            for (LlmPort.ToolCall call : calls) {
                listener.onReasoning("🔧 调用 " + call.name() + "：" + summarizeArgs(call.argumentsJson()));
            }
            List<String> outputs = calls.parallelStream()
                    .map(call -> invokeTool(call, ctx))
                    .toList();
            for (int i = 0; i < calls.size(); i++) {
                LlmPort.ToolCall call = calls.get(i);
                String output = outputs.get(i);
                listener.onReasoning("📥 " + toolName(call.name()) + "返回：" + summarizeToolOutput(output));
                messages.add(new LlmPort.ChatMessage("tool", output, null, call.id()));
            }
        }
        if (finalContent.isBlank()) {
            // 工具轮次用完后，强制模型停止调用工具，基于已查到的信息只输出 JSON，保证收敛。
            listener.onReasoning("📝 已收集到信息，正在整理方案…");
            messages.add(new LlmPort.ChatMessage("user",
                    "信息已足够。请不要再调用任何工具，基于上面已经查到的结果，立刻只输出最终的 JSON 对象，不要输出解释或代码块。",
                    null, null));
            LlmPort.ToolChatResult finalized = llmPort.toolChat(systemPrompt, messages, List.of());
            finalContent = finalized.content() == null ? "" : finalized.content();
        }
        if (finalContent.isBlank()) {
            log.warn("建前调研未产出结构化方案，最后模型输出：{}", finalContent);
        }
        GoalProposal proposal = parseGoalProposal(domain.type(), finalContent);
        return enrichProposal(domain, proposal, today);
    }

    /** 对调研结果做确定性的补充：考试临近时主动反问用户是否赶本考期，而不是默默跳过。 */
    private GoalProposal enrichProposal(ScenarioDomain domain, GoalProposal p, LocalDate today) {
        if (p == null) return null;
        if (!"cert_prep".equals(domain.type()) && !"exam_prep".equals(domain.type())) return p;
        LocalDate examDate = Task.parseDueDate(p.collected().get("examDate"));
        if (examDate == null) return p;
        long days = java.time.temporal.ChronoUnit.DAYS.between(today, examDate);
        if (days >= 0 && days < 60) {
            String warn = "最近一次可报考期为 " + examDate + "，距今仅 " + days
                    + " 天，备考时间较紧。是否赶这次考期？若来不及，可点“需要修改”改报下个考期。";
            List<Section> sections = new ArrayList<>(p.sections());
            sections.add(0, new Section("⚠️", "考期确认",
                    List.of(new Row("请确认", warn, true))));
            return new GoalProposal(p.scenarioType(), p.title(), p.goalText(), p.collected(),
                    p.focusAreas(), p.focusLabels(), sections, p.materials());
        }
        return p;
    }

    private GoalProposal parseGoalProposal(String scenarioType, String content) {
        if (content == null || content.isBlank()) return null;
        String json = content.trim();
        int fence = json.indexOf("```");
        if (fence >= 0) {
            int start = json.indexOf('\n', fence);
            int end = json.lastIndexOf("```");
            if (start > 0 && end > start) json = json.substring(start + 1, end).trim();
        }
        int brace = json.indexOf('{');
        int close = json.lastIndexOf('}');
        if (brace >= 0 && close > brace) json = json.substring(brace, close + 1);
        try {
            JsonNode root = objectMapper.readTree(json);
            Map<String, String> collected = new LinkedHashMap<>();
            JsonNode c = root.path("collected");
            if (c.isObject()) c.fields().forEachRemaining(e -> {
                if (e.getValue() != null && !e.getValue().isNull()) {
                    collected.put(e.getKey(), e.getValue().asText(""));
                }
            });
            List<Section> sections = new ArrayList<>();
            for (JsonNode s : root.path("sections")) {
                List<Row> rows = new ArrayList<>();
                for (JsonNode r : s.path("rows")) {
                    rows.add(new Row(r.path("label").asText(""), r.path("value").asText(""),
                            r.path("uncertain").asBoolean(false)));
                }
                sections.add(new Section(s.path("icon").asText("📌"), s.path("title").asText(""), rows));
            }
            List<PendingGoalProposalStore.StudyMaterial> materials = new ArrayList<>();
            for (JsonNode m : root.path("materials")) {
                String t = m.path("title").asText("");
                String u = m.path("url").asText("");
                if (!t.isBlank() && !u.isBlank()) materials.add(new PendingGoalProposalStore.StudyMaterial(t, u));
            }
            List<String> focusAreas = new ArrayList<>();
            Map<String, String> focusLabels = new LinkedHashMap<>();
            for (JsonNode f : root.path("focusAreas")) {
                if (f.isTextual()) {
                    String key = "custom_" + (focusAreas.size() + 1);
                    focusAreas.add(key);
                    focusLabels.put(key, f.asText(""));
                } else if (f.isObject()) {
                    String label = f.path("label").asText(f.path("name").asText(""));
                    if (label.isBlank()) continue;
                    String key = f.has("key") && !f.path("key").asText("").isBlank()
                            ? f.path("key").asText() : "custom_" + (focusAreas.size() + 1);
                    focusAreas.add(key);
                    focusLabels.put(key, label);
                }
            }
            String title = root.path("title").asText("");
            String goalText = root.path("goalText").asText(title);
            if (title.isBlank()) return null;
            return new GoalProposal(scenarioType, title, goalText, collected, focusAreas, focusLabels, sections, materials);
        } catch (Exception e) {
            log.warn("解析建目标调研结果失败: {}", e.getMessage());
            return null;
        }
    }

    private void saveStudyMaterials(SubSession sub, List<PendingGoalProposalStore.StudyMaterial> materials) {
        if (materials == null || materials.isEmpty()) return;
        try {
            sub.setStudyMaterials(objectMapper.writeValueAsString(materials));
            subSessionRepository.save(sub);
        } catch (Exception e) {
            log.warn("保存学习资料失败: {}", e.getMessage());
        }
    }

    /** 保存 LLM 生成的自定义关注项 key→中文label 映射（通用计划等无内置关注项的场景）。 */
    private void saveCustomFocusLabels(SubSession sub, Map<String, String> focusLabels) {
        if (sub == null || focusLabels == null || focusLabels.isEmpty()) return;
        java.util.Map<String, String> existing = CustomFocusLabels.read(sub);
        existing.putAll(focusLabels);
        CustomFocusLabels.write(sub, existing);
        subSessionRepository.save(sub);
    }

    private String proposalAnnounce(GoalProposal p) {
        StringBuilder sb = new StringBuilder("我查到了「")
                .append(p.title()).append("」的相关信息，整理如下，请你核对后再创建：\n");
        for (Section s : p.sections()) {
            sb.append("\n").append(s.icon()).append(" ").append(s.title()).append("\n");
            for (Row r : s.rows()) {
                sb.append("- ").append(r.label());
                if (!r.label().isBlank()) sb.append("：");
                sb.append(r.value());
                if (r.uncertain()) sb.append("（待确认）");
                sb.append("\n");
            }
        }
        sb.append("\n确认无误就点下方“确认创建”；需要改就直接告诉我。");
        return sb.toString();
    }

    /** 用户确认待建方案：真正创建子对话。 */
    @Transactional
    public SubSession confirmGoalProposal(Long userId, String proposalId) {
        PendingGoalProposalStore.StoredProposal sp = pendingGoalProposalStore.get(proposalId);
        if (sp == null || !userId.equals(sp.userId())) {
            throw new IllegalArgumentException("待确认方案不存在或已过期");
        }
        GoalProposal p = sp.proposal();
        SubSession sub = goalAppService.createGoal(userId, p.scenarioType(), p.title(),
                p.goalText(), p.collected(), p.focusAreas(), p.focusLabels());
        saveStudyMaterials(sub, p.materials());
        saveCustomFocusLabels(sub, p.focusLabels());
        pendingGoalProposalStore.remove(proposalId);
        return sub;
    }

    /** 读取待确认方案（校验归属），供接口在确认前取标题等信息。 */
    public PendingGoalProposalStore.StoredProposal getPendingProposal(String proposalId, Long userId) {
        PendingGoalProposalStore.StoredProposal sp = pendingGoalProposalStore.get(proposalId);
        if (sp == null || !userId.equals(sp.userId())) return null;
        return sp;
    }

    /** 用户当前主对话中最近一份待确认的建目标方案（用于刷新/轮询后恢复确认卡）。 */
    public PendingGoalProposalStore.StoredProposal getLatestPendingProposal(Long userId) {
        return pendingGoalProposalStore.findLatestByUser(userId);
    }

    private String confirmGoalProposal(Long userId, PendingGoalProposalStore.StoredProposal sp,
                                       ChatListener listener) {
        GoalProposal p = sp.proposal();
        SubSession sub = goalAppService.createGoal(userId, p.scenarioType(), p.title(),
                p.goalText(), p.collected(), p.focusAreas(), p.focusLabels());
        saveStudyMaterials(sub, p.materials());
        saveCustomFocusLabels(sub, p.focusLabels());
        pendingGoalProposalStore.remove(sp.id());
        String announce = "好的，已为你创建「" + p.title() + "」专属计划（子对话 #" + sub.getId()
                + "）。我会在这个专属会话里帮你跟进报考、复习和考试节点。";
        streamPreset(announce, listener);
        listener.onGoalCreated(sub.getId(), p.scenarioType(), p.title());
        return announce;
    }

    private String streamReply(Long userId, SessionType type, Long subSessionId, String content,
                               ChatListener listener) {
        String systemPrompt = buildSystemPrompt(userId, type, subSessionId);
        List<LlmPort.ChatMessage> messages = new ArrayList<>();
        for (RawChatLog l : history(userId, type, subSessionId)) {
            if ("system".equals(l.getRole())) continue;
            messages.add(new LlmPort.ChatMessage(l.getRole(), l.getContent()));
        }
        messages.add(new LlmPort.ChatMessage("user", content));
        if (type == SessionType.SUB && subSessionId != null) {
            List<LlmPort.ToolDef> tools = toolsFor(subSessionId);
            if (!tools.isEmpty()) {
                String finalAnswer = runToolLoop(systemPrompt, messages, listener, tools, subSessionId);
                streamPreset(finalAnswer, listener);
                return finalAnswer;
            }
        }
        return llmPort.streamChat(systemPrompt, messages, listener::onChunk);
    }

    private List<LlmPort.ToolDef> toolsFor(Long subSessionId) {
        SubSession sub = subSessionRepository.findById(subSessionId).orElse(null);
        if (sub == null || !scenarioRegistry.supports(sub.getScenarioType())) return List.of();
        return toolRegistry.forScenario(scenarioRegistry.get(sub.getScenarioType()).toolCategories())
                .stream()
                .map(c -> new LlmPort.ToolDef(c.name(), c.description(), c.parametersSchema()))
                .toList();
    }

    private String runToolLoop(String systemPrompt, List<LlmPort.ChatMessage> seed, ChatListener listener,
                               List<LlmPort.ToolDef> toolDefs, Long subSessionId) {
        StringBuilder guide = new StringBuilder("\n\n【可用工具】当你需要外部或实时信息时，必须调用下面的工具获取，不要凭空编造或直接回答“查不到”：\n");
        for (LlmPort.ToolDef t : toolDefs) {
            guide.append("- ").append(t.name()).append("：").append(t.description()).append("\n");
        }
        guide.append("""
                工具选择的通用原则（按信息来源的可靠性，而非逐个问题判断）：
                1. 确定性、结构化的事实（地理位置、地址归属的区划/街道、经纬度、周边场所、距离、路线，以及系统内已注册的功能/关注项），优先调用对应的结构化工具，其返回的是权威真实数据，不要用联网搜索去猜，也不要凭语言模型记忆编造；
                   - 问“某地附近/周边/在某地找X”时，把该参照地点放进工具的 location 参数，不要默认用用户当前定位；只有用户说“我附近/周边”且没指定地点时，才以用户当前定位为中心；
                2. 只有查最新政策、补贴金额、办事流程、新闻动态等会随时间变化的文本信息时，才使用联网搜索或知识库；
                3. 一旦某个工具返回了明确结果，以该结果为准回答，不要再用其他弱来源覆盖或质疑它；
                4. 调用周边/检索类工具时，query 只传简短关键词，不要传整句话。
                调用工具后，结合工具返回的结果用中文回答用户。""");
        systemPrompt = systemPrompt + guide;
        List<LlmPort.ChatMessage> messages = new ArrayList<>(seed);
        ToolContext ctx = buildToolContext(subSessionId);
        for (int step = 0; step < MAX_TOOL_CALLS; step++) {
            LlmPort.ToolChatResult result = llmPort.toolChat(systemPrompt, messages, toolDefs);
            if (!result.hasToolCalls()) {
                listener.onReasoning("🧠 基于已有记忆/知识直接回答，本轮无需调用外部工具。");
                return result.content() == null || result.content().isBlank()
                        ? "我暂时没有查到更多信息，你可以换个说法再试试。" : result.content();
            }
            messages.add(new LlmPort.ChatMessage("assistant", result.content() == null ? "" : result.content(),
                    result.toolCalls(), null));
            for (LlmPort.ToolCall call : result.toolCalls()) {
                listener.onReasoning("🔧 调用 " + call.name() + "：" + summarizeArgs(call.argumentsJson()));
                String output = invokeTool(call, ctx);
                listener.onReasoning("📥 " + toolName(call.name()) + "返回：" + summarizeToolOutput(output));
                messages.add(new LlmPort.ChatMessage("tool", output, null, call.id()));
            }
        }
        LlmPort.ToolChatResult finalResult = llmPort.toolChat(systemPrompt, messages, toolDefs);
        return finalResult.content() == null || finalResult.content().isBlank()
                ? "我查了一些信息但还需要你补充，我们继续聊。" : finalResult.content();
    }

    private String invokeTool(LlmPort.ToolCall call, ToolContext ctx) {
        ToolCategory category = toolRegistry.get(call.name());
        if (category == null) return "没有这个工具：" + call.name();
        try {
            return category.execute(call.argumentsJson(), ctx);
        } catch (Exception e) {
            return "工具执行失败：" + e.getMessage();
        }
    }

    private String toolName(String name) {
        return switch (name) {
            case "GeoService" -> "地图";
            case "KnowledgeBase" -> "知识库";
            case "WebSearch" -> "联网";
            default -> name;
        };
    }

    private String summarizeArgs(String argumentsJson) {
        try {
            com.fasterxml.jackson.databind.JsonNode n =
                    new com.fasterxml.jackson.databind.ObjectMapper().readTree(argumentsJson == null ? "{}" : argumentsJson);
            StringBuilder sb = new StringBuilder();
            String op = n.has("sub_tool") ? n.get("sub_tool").asText() : null;
            if (op != null && !op.isBlank()) sb.append("[").append(op).append("] ");
            String q = n.has("query") ? n.get("query").asText() : "";
            String loc = n.has("location") ? n.get("location").asText() : "";
            String addr = n.has("address") ? n.get("address").asText() : "";
            if (!loc.isBlank()) sb.append("找【").append(q).append("】，以【").append(loc).append("】为中心");
            else if (!addr.isBlank()) sb.append("解析地址【").append(addr).append("】");
            else sb.append(q);
            String result = sb.toString().trim();
            return result.isEmpty() ? (argumentsJson == null ? "" : argumentsJson) : result;
        } catch (Exception e) {
            return argumentsJson == null ? "" : argumentsJson;
        }
    }

    private String summarizeToolOutput(String output) {
        if (output == null) return "";
        String oneLine = output.replaceAll("\\s+", " ").trim();
        return oneLine.length() > 160 ? oneLine.substring(0, 160) + "…" : oneLine;
    }

    private ToolContext buildToolContext(Long subSessionId) {
        SubSession sub = subSessionRepository.findById(subSessionId).orElse(null);
        if (sub == null) {
            return new ToolContext(null, "sub", subSessionId, null, Map.of(),
                    java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")));
        }
        java.util.Map<String, String> collected = Map.of();
        if (scenarioRegistry.supports(sub.getScenarioType())) {
            ScenarioDomain domain = scenarioRegistry.get(sub.getScenarioType());
            collected = ScenarioStateSupport.parse(domain, sub.getCollectedInfo(), CustomFocusLabels.read(sub)).collected();
        }
        collected = withUserLocationFallback(sub.getUserId(), collected);
        return new ToolContext(sub.getUserId(), "sub", subSessionId, sub.getScenarioType(), collected,
                java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")));
    }

    /** 把一段固定文本伪装成流式逐字输出，体验一致。 */
    private void streamPreset(String text, ChatListener listener) {
        for (char c : text.toCharArray()) {
            listener.onChunk(String.valueOf(c));
        }
    }

    private String buildSystemPrompt(Long userId, SessionType type, Long subSessionId) {
        java.time.ZonedDateTime now = java.time.ZonedDateTime.now(ZoneId.of("Asia/Shanghai"));
        String nowText = "现在是 " + now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm EEEE"))
                + "（Asia/Shanghai）。涉及“今天/本周/是否过期/距今多久/当前孕周/备考阶段”等时间判断时，一律以此刻为准，不要沿用历史对话里的旧时间。\n";
        StringBuilder sb = new StringBuilder(nowText).append(memoryContextAssembler.assemble(userId, type, subSessionId));
        if (type == SessionType.MAIN) {
            appendUserLocation(sb, userId);
            sb.append("\n当前系统已支持以下技能：\n");
            for (ScenarioDomain d : scenarioRegistry.all()) {
                sb.append("- ").append(d.type()).append("（").append(d.displayName())
                        .append("）：").append(d.description()).append("\n");
                appendFocusCatalog(sb, d);
            }
            sb.append("当用户问某类计划“有哪些关注项/功能/能力”时，只能基于上面列出的关注项名称和说明回答，不要杜撰未列出的模块。");
            sb.append("当用户想创建某类计划时，引导ta提供必要信息。回答简洁友好，使用中文。");
        } else {
            subSessionRepository.findById(subSessionId).ifPresent(sub -> {
                if (scenarioRegistry.supports(sub.getScenarioType())) {
                    ScenarioDomain domain = scenarioRegistry.get(sub.getScenarioType());
                    sb.append("\n当前场景：").append(domain.displayName()).append("\n");
                    ScenarioStateSupport.ScenarioState state0 =
                            ScenarioStateSupport.parse(domain, sub.getCollectedInfo(), CustomFocusLabels.read(sub));
                    java.util.Map<String,String> collectedForPrompt =
                            withUserLocationFallback(sub.getUserId(), state0.collected());
                    ScenarioStateSupport.ScenarioState state =
                            new ScenarioStateSupport.ScenarioState(collectedForPrompt, state0.focusAreas());
                    ScenarioDomain.Situation situation = domain.situation(
                            state.collected(), state.focusAreas(), LocalDate.now(ZoneId.of("Asia/Shanghai")));
                    if (situation.hasSummary()) {
                        sb.append("\n").append(situation.summary());
                    }
                    if (situation.alerts() != null && !situation.alerts().isEmpty()) {
                        sb.append("\n【需要你关注的提醒】\n");
                        situation.alerts().forEach(a -> sb.append("- ").append(a).append("\n"));
                    }
                    appendFocusCatalog(sb, domain);
                    sb.append("用户问关注项/功能时，只能基于这些已注册关注项回答，不要杜撰未列出的模块。\n");
                }
            });
            sb.append("\n请基于该场景和上述待办/记忆给出具体、可执行的建议，使用中文。");
        }
        return sb.toString();
    }

    /** 主对话注入用户级定位：查地区政策/补贴/报名要求时默认按此地区，不要返回外地口径。 */
    private void appendUserLocation(StringBuilder sb, Long userId) {
        MainSession ms = mainSessionRepository.findByUserId(userId).orElse(null);
        if (ms == null) return;
        boolean hasCity = isNotBlank(ms.getCity());
        boolean hasCoord = isNotBlank(ms.getLatitude()) && isNotBlank(ms.getLongitude());
        if (!hasCity && !hasCoord) return;
        sb.append("\n【用户当前位置（系统已通过浏览器授权获取，可直接使用）】\n");
        if (hasCity) sb.append("- 所在城市/区县：").append(ms.getCity()).append("\n");
        if (hasCoord) sb.append("- 精确坐标：纬度").append(ms.getLatitude())
                .append("，经度").append(ms.getLongitude()).append("\n");
        sb.append("查询地区政策、补贴、报名要求、报名/考点等具有地域性的信息时，默认以该地区为准；")
          .append("需要周边场所时直接用地图工具按上述坐标检索，不要再说获取不到位置，也不要返回其他地区的口径。\n");
    }

    /** 供建前调研提示使用：有定位则要求按该地区核实，没有则空。 */
    private String locationHint(Long userId) {
        MainSession ms = mainSessionRepository.findByUserId(userId).orElse(null);
        if (ms == null) return "";
        StringBuilder sb = new StringBuilder();
        if (isNotBlank(ms.getCity())) sb.append("用户所在城市/区县：").append(ms.getCity()).append("。");
        if (isNotBlank(ms.getLatitude()) && isNotBlank(ms.getLongitude()))
            sb.append("坐标：纬度").append(ms.getLatitude()).append("，经度").append(ms.getLongitude()).append("。");
        if (sb.length() == 0) return "";
        return sb.append("凡是有地区差异的信息（报名要求、政策、考点等），一律按该地区联网核实，不要返回外地口径。").toString();
    }

    private void appendFocusCatalog(StringBuilder sb, ScenarioDomain domain) {
        if (domain.focusAreas().isEmpty()) return;
        sb.append("  已注册关注项：\n");
        for (ScenarioDomain.FocusArea focusArea : domain.focusAreas()) {
            sb.append("  - ").append(focusArea.label()).append("：").append(focusArea.description()).append("\n");
        }
    }
}
