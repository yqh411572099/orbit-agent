package com.butler.domain.scenario;

import com.butler.domain.attribute.Attribute;
import java.util.LinkedHashMap;
import com.butler.domain.attribute.AttributeDescriptor;
import com.butler.domain.attribute.MeasureAttribute;
import com.butler.domain.attribute.StatusAttribute;
import com.butler.domain.attribute.VenueAttribute;
import java.time.LocalDate;
import java.util.List;

/**
 * 场景域：每一类长期目标（考研、孕期、考证...）是一个独立的域。
 * 新增场景只需实现该接口并注册为 Spring Bean，记忆提炼、关联逻辑、前端表单自动适配。
 */
public interface ScenarioDomain {

    String type();

    String displayName();

    /** 一句话描述这个技能能做什么，用于主对话向用户介绍。 */
    String description();

    /** 该场景的默认 session_desc 模板，用于关联判定。 */
    String defaultSessionDesc();

    /** 创建目标前需要向用户收集的信息字段。 */
    List<CollectField> collectFields();

    /** 场景内置的重点关注项（无需用户自己列举），前端以多选卡片呈现。 */
    default List<FocusArea> focusAreas() {
        return List.of();
    }

    /** 该域支持的属性目录，用于提示 LLM 按 schema 提炼结构化属性。 */
    default List<AttributeDescriptor> attributeCatalog() {
        return List.of();
    }

    /** 该域强类型属性类（注册到 Jackson 多态反序列化）。 */
    default List<Class<? extends Attribute>> attributeClasses() {
        return List.of();
    }

    /** 由创建时收集的字段构造结构化属性（默认按语义类型映射到通用属性，场景可覆写为本域强类型）。 */
    default List<Attribute> attributesFromCollected(java.util.Map<String, String> collected) {
        if (collected == null) return List.of();
        List<Attribute> attrs = new java.util.ArrayList<>();
        for (CollectField f : collectFields()) {
            String v = collected.get(f.key());
            if (v == null || v.isBlank()) continue;
            switch (f.semanticType()) {
                case MEASURE -> attrs.add(new MeasureAttribute(f.key(), v, f.unit()));
                case STATUS -> attrs.add(new StatusAttribute(v));
                case LOCATION -> attrs.add(new VenueAttribute(v));
                default -> {}
            }
        }
        return attrs;
    }

    /** 由对话/表单增量字段构造结构化属性，默认复用创建字段到 Attribute 的映射规则。 */
    default List<Attribute> attributesFromUpdates(java.util.Map<String, String> updates) {
        return attributesFromCollected(updates == null ? java.util.Map.of() : updates);
    }

    /** 提供给 LLM 的关键字段提示，稳定使用 key，避免场景字段散落在提示词外。 */
    default List<String> keyFieldHints() {
        return collectFields().stream()
                .map(f -> f.key() + "(" + f.label() + ")")
                .toList();
    }

    /** 由创建时收集的字段构造主体画像 JSON（如 {"role":"准爸爸","relatedParty":"孕妇"}），默认返回 null。 */
    default String subjectProfileFromCollected(java.util.Map<String, String> collected) {
        return null;
    }

    /** 创建目标前规范化收集字段，例如由孕周+参照日期确定性换算预产期，默认不修改。 */
    default java.util.Map<String, String> normalizeCollected(String goal,
                                                             java.util.Map<String, String> collected,
                                                             LocalDate today) {
        return collected == null ? java.util.Map.of() : collected;
    }

    /** 对话增量字段写入前规范化，默认不修改。 */
    default java.util.Map<String, String> normalizeUpdates(String message,
                                                           java.util.Map<String, String> updates,
                                                           LocalDate today) {
        return updates == null ? java.util.Map.of() : updates;
    }

    /** 从用户初始描述生成阶段规划/待办（可由 LLM 结果覆盖）。 */
    List<String> initialTasks(String goal);

    /**
     * 理解一次用户输入（对话或表单变更），产出一组与具体场景无关的“意图指令”。
     *
     * <p>这是场景适配的核心扩展点：场景内部可调用 LLM + 规则，把自然语言翻译成
     * {@link ScenarioIntent}（关键字段更新、里程碑完成、关注项增减、结构化记忆 upsert），
     * 应用层据此统一执行，不再对任何具体场景写 if/instanceof。</p>
     *
     * @param message       用户本次输入文本（表单变更也会被转成文本指令）
     * @param collected     子对话当前已收集的关键字段（如 dueDate）
     * @param focusAreas    当前生效的关注项 key
     * @return 意图集合；无变化返回空列表
     */
    default List<ScenarioIntent> interpret(String message, java.util.Map<String, String> collected,
                                          List<String> focusAreas) {
        return List.of();
    }

    /** 通用 LLM 事件解释入口：场景可覆写为规则+模型混合逻辑，默认只做标准字段事件提取。 */
    default List<ScenarioIntent> interpret(String message, java.util.Map<String, String> collected,
                                          List<String> focusAreas, EventExtractor extractor) {
        DomainEvent event = extractor.extract(keyFieldHints(), message);
        if (event == null) return List.of();
        return List.of(new ScenarioIntent(
                event.fieldUpdates() == null ? java.util.Map.of() : event.fieldUpdates(),
                event.completedMilestones() == null ? List.of() : event.completedMilestones(),
                event.enableFocusAreas() == null ? List.of() : event.enableFocusAreas(),
                event.disableFocusAreas() == null ? List.of() : event.disableFocusAreas(),
                attributesFromUpdates(event.fieldUpdates()),
                event.note() == null ? "" : event.note(),
                event.affectsTasks()));
    }

    @FunctionalInterface
    interface EventExtractor {
        DomainEvent extract(List<String> keyFieldHints, String message);
    }

    record DomainEvent(java.util.Map<String, String> fieldUpdates,
                       List<String> completedMilestones,
                       List<String> enableFocusAreas,
                       List<String> disableFocusAreas,
                       String note,
                       boolean affectsTasks) {
        public DomainEvent(java.util.Map<String, String> fieldUpdates,
                           List<String> completedMilestones,
                           List<String> enableFocusAreas,
                           List<String> disableFocusAreas,
                           String note) {
            this(fieldUpdates, completedMilestones, enableFocusAreas, disableFocusAreas, note, false);
        }
    }

    /**
     * 一次输入对“目标/时间轴”和“记忆”两个影响面的统一指令。所有字段均可空/空列表。
     *
     * @param updatedFields       目标关键字段的新值（如 dueDate=2027-05-01）；用于触发时间轴重算与 collectedInfo 更新
     * @param completedMilestones 已完成的里程碑标题关键词（匹配任务标题即标记完成）
     * @param enableFocusAreas    需要新增启用的关注项 key
     * @param disableFocusAreas   需要关闭的关注项 key
     * @param memoryUpserts       需要写入/更新的结构化属性（按 type 定位记忆中的 attribute）
     * @param note                给用户的一句话反馈（为空则不主动回复）
     */
    record ScenarioIntent(java.util.Map<String, String> updatedFields,
                          List<String> completedMilestones,
                          List<String> enableFocusAreas,
                          List<String> disableFocusAreas,
                          List<Attribute> memoryUpserts,
                          String note,
                          boolean affectsTasks) {
        public ScenarioIntent(java.util.Map<String, String> updatedFields,
                              List<String> completedMilestones,
                              List<String> enableFocusAreas,
                              List<String> disableFocusAreas,
                              List<Attribute> memoryUpserts,
                              String note) {
            this(updatedFields, completedMilestones, enableFocusAreas, disableFocusAreas,
                    memoryUpserts, note, false);
        }
        public static ScenarioIntent empty() {
            return new ScenarioIntent(java.util.Map.of(), List.of(), List.of(), List.of(), List.of(), "");
        }
        public boolean isEmpty() {
            return (updatedFields == null || updatedFields.isEmpty())
                    && (completedMilestones == null || completedMilestones.isEmpty())
                    && (enableFocusAreas == null || enableFocusAreas.isEmpty())
                    && (disableFocusAreas == null || disableFocusAreas.isEmpty())
                    && (memoryUpserts == null || memoryUpserts.isEmpty())
                    && (note == null || note.isBlank())
                    && !affectsTasks;
        }
    }

    /**
     * 确定性时间轴任务：由场景根据收集信息与今天日期生成带提前量、带知识详情的待办。
     *
     * <p>若返回非空，应用层优先使用它（而非 LLM 临时生成的任务），保证时间锚点、提前量、
     * 必备事项不依赖模型发挥。默认不提供，保持其它场景行为不变。</p>
     *
     * @param collected  创建时收集的字段
     * @param focusAreas 用户启用的关注项 key（可选模块）
     * @param today      今天（用于按孕周/进度推算到期日）
     */
    default List<PlannedTask> plannedTasks(java.util.Map<String, String> collected,
                                          List<String> focusAreas, LocalDate today) {
        return List.of();
    }

    /** 由确定性时间轴覆盖的关注项 key；其余已启用关注项需要按用户输入动态生成任务。 */
    default java.util.Set<String> deterministicFocusAreas(java.util.Map<String, String> collected,
                                                         List<String> focusAreas, LocalDate today) {
        return plannedTasks(collected, focusAreas, today).stream()
                .map(PlannedTask::focusArea)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
    }

    /**
     * 场景随“今天”动态变化的上下文，注入到对话 system prompt。
     *
     * <p>把会随时间漂移的事实（如当前孕周、距考试天数）交给确定性代码实时计算，
     * 避免模型沿用历史对话里过期的数字。默认无额外上下文。</p>
     */
    default Situation situation(java.util.Map<String, String> collected,
                               java.util.List<String> focusAreas, LocalDate today) {
        return new Situation("", List.of());
    }

    /**
     * 当前处境：模型回答前先“看一眼日历和清单”，summary 是给模型的实时处境描述，
     * alerts 是需要主动告知用户的提醒（如过期/临近节点）。由确定性代码计算，不让模型猜。
     */
    record Situation(String summary, java.util.List<String> alerts) {
        public boolean hasSummary() { return summary != null && !summary.isBlank(); }
    }

    /**
     * 该场景挂载的工具大类名（如 GeoService / KnowledgeBase / WebSearch）。
     * “是否调用工具、查什么”由模型在对话中自行决定；场景只声明“这件事允许用哪些大类”。
     * 返回空列表表示该场景不提供外部工具。
     */
    default List<String> toolCategories() {
        return List.of();
    }

    /**
     * 创建目标前是否需要先联网调研、产出信息确认卡让用户核对后再建。
     * 适用于“证书类型多、报名/考试时间因证而异、需核实官方入口”的场景（如考证）。
     */
    default boolean researchBeforeCreate() {
        return false;
    }

    /** 给模型的调研要点提示：告诉它创建这类目标前应查清哪些信息。 */
    default String researchBrief() {
        return "";
    }

    /** 时间轴规划出的待办：主任务到期日 + 可选的“提前准备”任务日期。 */
    /**
     * 一个时间轴待办。只有一个任务条目，但带两个时间：
     * @param remindDate 提醒开始日期（提前准备时间，可为空，为空则与执行日同一天提醒）
     * @param dueDate    执行/截止日期
     */
    record PlannedTask(String title, String detail, String moduleKey, String focusArea,
                       LocalDate remindDate, LocalDate dueDate, String nextHint, String milestoneKey) {
        public PlannedTask(String title, String detail, String moduleKey, String focusArea,
                           LocalDate remindDate, LocalDate dueDate, String nextHint) {
            this(title, detail, moduleKey, focusArea, remindDate, dueDate, nextHint, null);
        }
    }

    enum FieldType { TEXT, DATE, SELECT, TEXTAREA, NUMBER, HIDDEN }

    /**
     * 字段语义类型：声明该收集字段对应“人/时/地/数值/状态”等结构化要素，
     * 便于创建目标时直接沉淀为结构化记忆，而不只是拼成文本。
     */
    enum SemanticType {
        /** 普通文本，无特殊结构化含义。 */
        TEXT,
        /** 人物/角色，如准妈妈/准爸爸。 */
        PERSON,
        /** 时间点/日期，如预产期、下次B超日。 */
        TIME,
        /** 地点/机构，如产检医院、居住城市。 */
        LOCATION,
        /** 可度量数值，如体重、孕周。 */
        MEASURE,
        /** 阶段/状态，如备孕中、备考基础阶段。 */
        STATUS
    }

    /** 适用人群：双方共同 / 女方为主 / 男方(后勤辅助)为主。 */
    enum Audience { BOTH, FEMALE, MALE }

    interface CollectField {
        String key();
        String label();
        String placeholder();
        boolean required();
        default FieldType type() { return FieldType.TEXT; }
        default SemanticType semanticType() { return SemanticType.TEXT; }
        default List<String> options() { return List.of(); }
        default String unit() { return ""; }

        static CollectField text(String key, String label, String placeholder, boolean required) {
            return of(key, label, placeholder, required, FieldType.TEXT, SemanticType.TEXT, List.of(), "");
        }

        static CollectField of(String key, String label, String placeholder, boolean required,
                               FieldType type, SemanticType semantic, List<String> options, String unit) {
            return new CollectField() {
                public String key() { return key; }
                public String label() { return label; }
                public String placeholder() { return placeholder; }
                public boolean required() { return required; }
                public FieldType type() { return type; }
                public SemanticType semanticType() { return semantic; }
                public List<String> options() { return options; }
                public String unit() { return unit; }
            };
        }

        static CollectField select(String key, String label, String placeholder, boolean required, List<String> options) {
            return of(key, label, placeholder, required, FieldType.SELECT, SemanticType.TEXT, options, "");
        }

        static CollectField date(String key, String label, String placeholder, boolean required) {
            return of(key, label, placeholder, required, FieldType.DATE, SemanticType.TIME, List.of(), "");
        }

        /** 隐藏字段：不展示给用户、不拼进对话上下文，仅用于持久化技术性数据（如定位经纬度）。 */
        static CollectField hidden(String key, String label) {
            return of(key, label, "", false, FieldType.HIDDEN, SemanticType.TEXT, List.of(), "");
        }

        static CollectField textarea(String key, String label, String placeholder, boolean required) {
            return of(key, label, placeholder, required, FieldType.TEXTAREA, SemanticType.TEXT, List.of(), "");
        }

        static CollectField measure(String key, String label, String placeholder, boolean required, String unit) {
            return of(key, label, placeholder, required, FieldType.NUMBER, SemanticType.MEASURE, List.of(), unit);
        }

        static CollectField location(String key, String label, String placeholder, boolean required) {
            return of(key, label, placeholder, required, FieldType.TEXT, SemanticType.LOCATION, List.of(), "");
        }

        static CollectField person(String key, String label, String placeholder, boolean required, List<String> options) {
            return of(key, label, placeholder, required, FieldType.SELECT, SemanticType.PERSON, options, "");
        }
    }

    record FocusArea(String key, String label, String description, Audience audience,
                     boolean defaultSelected, boolean mandatory, List<String> dependsOn) {
        public FocusArea(String key, String label, String description, Audience audience, boolean defaultSelected) {
            this(key, label, description, audience, defaultSelected, false, List.of());
        }
        public FocusArea(String key, String label, String description, Audience audience,
                         boolean defaultSelected, boolean mandatory) {
            this(key, label, description, audience, defaultSelected, mandatory, List.of());
        }
        public FocusArea(String key, String label, String description, Audience audience,
                         boolean defaultSelected, List<String> dependsOn) {
            this(key, label, description, audience, defaultSelected, false, dependsOn);
        }
    }

    /**
     * 计算“生效的关注项集合”：强制项 + 用户显式选中项 + 选中项的依赖项（依赖为软依赖，会一起带上）。
     * 依赖可传递（A 依赖 B、B 依赖 C，则选 A 同时带上 B、C），并自动忽略不存在的 key。
     */
    static List<String> resolveEffectiveFocusAreas(List<FocusArea> all, List<String> selected) {
        java.util.Map<String, FocusArea> byKey = new java.util.LinkedHashMap<>();
        for (FocusArea f : all) byKey.put(f.key(), f);
        java.util.Set<String> effective = new java.util.LinkedHashSet<>();
        for (FocusArea f : all) {
            if (f.mandatory()) effective.add(f.key());
        }
        java.util.List<String> queue = new java.util.ArrayList<>(selected == null ? List.of() : selected);
        while (!queue.isEmpty()) {
            String key = queue.remove(0);
            if (!effective.add(key)) continue;
            FocusArea f = byKey.get(key);
            // 内置项继续展开其软依赖；自定义关注项（不在目录里）原样保留，无依赖可展开。
            if (f != null && f.dependsOn() != null) queue.addAll(f.dependsOn());
        }
        return List.copyOf(effective);
    }
}
