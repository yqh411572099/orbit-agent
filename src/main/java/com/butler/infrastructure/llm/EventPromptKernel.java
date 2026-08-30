package com.butler.infrastructure.llm;

/**
 * 事件提取提示词的“内核 + 能力手册”。
 *
 * <p>由模型自己做能力路由（不靠后端关键词预裁剪）：模型先读用户消息，判断本轮涉及哪些能力，
 * 在输出 JSON 的 {@code capabilities} 数组里声明选中的能力，并且【只】对选中的能力填写对应字段；
 * 未选中的能力对应字段必须省略或为空。这样详细规则虽常驻，但模型按需取用，避免无关能力误触发。
 * 场景专属规则不在此，由各 ScenarioDomain 的 eventRuleHints 提供。</p>
 */
final class EventPromptKernel {

    private EventPromptKernel() {}

    /** 能力标识（同时是 capabilities 数组的取值与字段归属）。 */
    static final String CAP_FIELD = "field_update";       // 关键字段/里程碑日期变更
    static final String CAP_COMPLETE = "complete";        // 标记完成
    static final String CAP_FOCUS = "focus";              // 关注项增删
    static final String CAP_TASK = "task";                // 待办/提醒调整
    static final String CAP_METRIC = "metric";            // 图表建/写/删

    /**
     * 完整规则手册：内核铁律 + 能力清单 + 各能力详解。
     * 模型据此自选能力；%s 处由调用方拼接“已有指标卡”动态信息。
     */
    static final String MANUAL = """
            【作答方式】先像正常助手一样理解并回答用户（该算的算、该解释的解释、该给建议的给建议），回答内容不受下面字段影响。
            【作答后的收尾检查】回答完后再判断：这轮对话里有没有“应当沉淀到系统里”的变化——某个数值值得进图表跟踪、
            某个关键日期/信息变了、某件事完成了、某个关注项要开关、某条待办要调整。把命中的能力 id 放进输出的 "capabilities" 数组，
            并【只】对命中的能力填写对应字段；没命中的能力字段一律省略或给空。按语义判断、保守，拿不准就不填。
            注意：正文里的表格/结论不会自动入库。只有当【已经存在对应图表】、或【用户明确要求新建图表】时，
            才把数值通过 metric 字段落库；若用户只是随口问/让你算一次、系统里又没有对应图表，不要主动建图、也不要写数据点，正常用正文回答即可。
            数值一致性：metricPoints 里的数值必须直接取自“助手本轮回答”里已经算出的结果，不要在抽取时重新估算或改动；助手回答算出了几个指标，就写几个数据点。
            序列完整性：写入某张卡时，把该卡已定义、且本轮助手回答中能确定数值的序列【尽量填齐】，不要只写其中一条而把同卡其它可确定的序列留空；
            助手回答里没有数值的序列才留空（不要编造）。
            能力清单（id —— 收尾时什么时候命中 —— 对应字段）：
            - %s：用户报了关键信息/日期变化（孕周、预产期、考试日、里程碑预约日期、个人画像等）—— fieldUpdates
            - %s：用户表示某检查/任务/里程碑已经做完 —— completedKeywords
            - %s：用户明确要新增/开启或关闭/取消某类“持续关注或提醒” —— enableFocusAreas / disableFocusAreas
            - %s：用户要新增/修改/删除/调整待办、提醒、提醒时间或周期 —— affectsTasks=true
            - %s：用户【明确要求】新建/调整/合并/删除某个图表，或往【已存在】的图表汇报/描述可量化、可推算数值的数据 —— metricDefs / metricPoints / metricRemove
            纯咨询、提问、闲聊、查政策、让你解释或推荐等，不选任何能力（capabilities 为空、所有变更字段为空、affectsTasks=false）。

            【铁律】
            - 只输出约定 JSON，不要解释。字段 key 必须使用“关键字段”里给出的 key，不要自造。
            - 日期字段统一 yyyy-MM-dd，缺年份按今天所在年份判断；无法精确换算也尽量给日期。

            【%s 字段更新】关键日期/信息变化写入 fieldUpdates（key=给定字段 key，value=新值）。

            【%s 标记完成】把已完成事项的关键词放入 completedKeywords。

            【%s 关注项】只有用户明确表达“要新增/开启某类持续提醒/关注”才 enableFocusAreas；只有明确“不再关注/关闭某类提醒”才 disableFocusAreas。
            一次性的日期/状态更新不是关注项变更。关键字段里以“(关注项:xxx)”标注的是内置关注项，直接用其 key；
            新增的非内置关注项用 "custom_key|中文名称"（custom_key 小写下划线蛇形，中文名用用户表述），内置项不带名称后缀。无增删意图两数组为空。

            【%s 待办】确有新增/修改/删除/调整待办或提醒时间周期时 affectsTasks=true，否则 false。

            【%s 图表】
            - metricDefs（新建/调整/合并图表）：【仅当用户明确要求】新建/调整/合并图表时才给出（如“加个体重图”“把摄入和缺口合成一张图”“把这张图的列改成X/Y/Z”）；
              用户没明确要求就不要给 metricDefs，不要因为“数值值得跟踪”或“某列缺失”就主动建卡或给已存在的卡增删序列。
              图表的字段列（series）在创建时即确定，之后写数据只能写入【已存在】的序列，不要新增/修改/补齐列；若用户报的数值在卡里没有对应列，该数值不写入（也不要改表结构）。
              建卡时就要把这张图【应该长期跟踪的序列一次性定义齐全】（例如“每日热量消耗构成”应含 总热量消耗/静息消耗/运动消耗；“摄入与缺口”应含 每日摄入/热量缺口），不要漏建。每张图对象：
              key=小写下划线唯一标识，label=中文名，unit=单位（kcal/斤/kg/分…），chartType=line（趋势）/bar（离散对比）/pie（构成占比），
              series=图内序列数组 [{key,label}]：单指标给一条（key 与卡片 key 同名）；一张图放多个相关指标给多条。
            - metricPoints（写数据点）：先理解输入，凡能确定或合理推算出某量化指标具体数值的（数值来自用户直报或你据事实推算），算出数值；
              再拿数值匹配“已有指标卡”的各条序列，语义对应哪条就写哪条（key 逐字复用该序列 series key，不要自造近似 key）。
              已有同义卡片/序列就写入它，不要新建同义卡片；若系统里【没有】对应图表且用户也没明确要求建图，则不要写 metricPoints（也不要主动建图），该数值仅在正文回答即可。
              图表数据只能来自 metricPoints，正文里的 markdown 表格/文字结论不会入库；得出该上图的数值就必须经 metricPoints 落库，不能只在正文陈述。
              但 metricPoints 的数值必须有依据：要么是用户本轮明确报出的数字，要么是本轮助手回答里基于用户给出的事实实际算出的结果；
              【严禁】在没有任何用户数据时凭空捏造数值——尤其当用户只是在“建图/改字段列/删图/问图”而没有报任何数值时，metricPoints 必须为空，绝不要顺手填入估算的示例数据。
              value=数字，date=yyyy-MM-dd（没说就今天），单位跟随图表口径。
              若输入既无数值也无可推算事实（如空泛地说“更新下数据”），metricPoints 为空，正文不要伪造表格或谎称已更新，应向用户询问取得数值所需信息。
            - metricRemove（删图表）：用户明确要删除/不再展示某张图时，给出要删除的“卡片 key”（metricDefs 的卡片 key，不是序列 key）；
              多张图合并为新图时，旧卡片 key 放入 metricRemove 并在 metricDefs 给出新卡。无删除意图返回空数组。
            """.formatted(
            CAP_FIELD, CAP_COMPLETE, CAP_FOCUS, CAP_TASK, CAP_METRIC,
            CAP_FIELD, CAP_COMPLETE, CAP_FOCUS, CAP_TASK, CAP_METRIC);

    /** 输出 JSON 的字段骨架（提示词里展示），含 capabilities。 */
    static final String JSON_SCHEMA = """
            {"capabilities":["field_update","focus","metric"],"fieldUpdates":{"字段key":"新值"},\
            "completedKeywords":["已完成事项关键词"],"enableFocusAreas":["新增关注项key"],"disableFocusAreas":["关闭关注项key"],\
            "affectsTasks":false,"note":"一句话说明变更",\
            "metricDefs":[{"key":"card_key","label":"卡片名","unit":"kcal","chartType":"line","series":[{"key":"series_key","label":"序列名"}]}],\
            "metricPoints":[{"key":"series_key","value":1500,"date":"2026-08-27"}],"metricRemove":["old_card_key"]}""";
}
