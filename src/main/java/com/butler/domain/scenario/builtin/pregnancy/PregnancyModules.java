package com.butler.domain.scenario.builtin.pregnancy;

import com.butler.domain.scenario.ScenarioDomain.Audience;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 孕期内置时间轴模块目录。
 *
 * <p>必选模块（孕检、生产、产后照护）无论用户是否勾选都生成；
 * 可选模块（体重、饮食营养、待产准备、知识学习）按用户启用的 focusArea 叠加。
 * 所有时间以“孕周”为锚点，由 {@link PregnancyClock} 换算成日期；每个里程碑带两个时间：
 * leadWeeks 决定提醒开始日期，dueWeek 决定执行/截止日期。其余关注项由用户自定义或对话动态生成。</p>
 */
public final class PregnancyModules {

    private PregnancyModules() {}

    public static List<PregnancyModule> all() {
        return List.of(
                prenatalCheckups(),
                birth(),
                postpartumCare(),
                weight(),
                dietNutrition(),
                birthBag(),
                knowledge()
        );
    }

    public static Map<String, PregnancyModule> optionalByFocusArea() {
        Map<String, PregnancyModule> map = new LinkedHashMap<>();
        for (PregnancyModule m : all()) {
            if (!m.mandatory() && m.focusArea() != null) {
                map.put(m.focusArea(), m);
            }
        }
        return map;
    }

    /** 所有可被“用户实际预约日期”覆盖的里程碑 key（稳定标识，供时间轴重排使用）。 */
    public static java.util.List<String> milestoneKeys() {
        java.util.List<String> keys = new java.util.ArrayList<>();
        for (PregnancyModule m : all()) {
            for (PregnancyModule.Milestone ms : m.milestones()) {
                if (ms.key() != null && !keys.contains(ms.key())) {
                    keys.add(ms.key());
                }
            }
        }
        return keys;
    }

    /** 里程碑 key 对应的“预约日期”收集字段名，通用约定：milestone_<key>_date。 */
    public static String appointmentField(String milestoneKey) {
        return "milestone_" + milestoneKey + "_date";
    }

    /** 必选：孕/产检时间表。 */
    private static PregnancyModule prenatalCheckups() {
        return new PregnancyModule("prenatal_checkups", "孕/产检时间表", Audience.BOTH, true,
                "prenatal_checkup", "按孕周锚定的必做产检项目与时间窗",
                List.of(
                        new PregnancyModule.Milestone("early_ultrasound", "早孕B超：确认宫内孕、胎心胎芽",
                                "做经阴道/腹部B超，确认宫内妊娠、单胎/多胎、胎心胎芽；可同步查HCG、孕酮、甲状腺功能。",
                                8, 2,
                                "早孕B超完成后，接下来预约11~13+6周的NT检查（错过无法补做）。"),
                        new PregnancyModule.Milestone("nt", "NT检查（颈项透明层）",
                                "NT需在11~13+6周完成，错过无法补做；建议提前2~4周预约，检查无需空腹、需憋尿。",
                                12, 2,
                                "NT完成后，与医生确认唐筛/无创DNA（NIPT）方案，15~20周做中期唐筛。"),
                        new PregnancyModule.Milestone("screening_nipt", "唐筛 / 无创DNA（NIPT）选择",
                                "15~20周做中期唐筛；35岁以上或高风险建议直接选无创DNA（12~22周）或遵医嘱做羊穿。提前决定并预约。",
                                16, 3,
                                "唐筛/无创完成后，重点准备20~24周的大排畸B超，通常需提前1个月预约。"),
                        new PregnancyModule.Milestone("anomaly_scan", "大排畸（系统超声/三维四维）",
                                "20~24周做胎儿结构畸形筛查，是最重要的一次B超，通常需提前1个月预约，检查时间较长建议有人陪同。",
                                22, 4,
                                "大排畸完成后，预约24~28周的糖耐量试验（OGTT），检查需空腹。"),
                        new PregnancyModule.Milestone("ogtt", "糖耐量试验（OGTT）",
                                "24~28周筛查妊娠糖尿病，需空腹、喝糖水后抽3次血（0/1/2小时），前一晚10点后禁食。",
                                26, 1,
                                "糖耐完成后，30~32周做小排畸B超，32~34周起开始胎心监护。"),
                        new PregnancyModule.Milestone("growth_scan", "小排畸 + 开始胎心监护",
                                "30~32周做第二次排畸B超评估胎儿生长、胎位、羊水胎盘；32~34周起每次产检加做胎心监护。",
                                31, 1,
                                "小排畸后进入孕晚期，36周起每周产检，留意胎位与入盆情况。"),
                        new PregnancyModule.Milestone("late_checkup", "孕晚期产检（胎位/胎心/骨盆评估）",
                                "36周后每周产检一次，评估胎位、入盆、血压体重，37周足月后随时可能发动。",
                                36, 1,
                                "已足月，待产包与入院路线就位，留意见红/破水/规律宫缩等临产信号。")
                ));
    }

    /** 必选：生产相关（建档、生育登记、生产医院、入院分娩）。 */
    private static PregnancyModule birth() {
        return new PregnancyModule("birth", "生产相关", Audience.BOTH, true,
                "birth", "建档、生育登记、生产医院选择与入院分娩安排",
                List.of(
                        new PregnancyModule.Milestone("filing", "选定产检/建档医院并完成建档",
                                "多数医院要求孕6~12周建档，名额紧张需尽早。准备：夫妻身份证、结婚证、户口本/居住证、医保卡、早孕B超单、近期验血单；确认该医院能否在此生产。",
                                12, 4,
                                "建档完成后，按医院预约节奏推进产检，并同步办理生育登记/生育保险。"),
                        new PregnancyModule.Milestone("benefits", "办理生育登记并确认生育保险待遇",
                                "先在社区/政务APP办理生育登记（原准生证）；有职工社保的提前办理生育保险就医确认，产检和分娩费用可直接结算或产后报销。各地另有一次性产检补贴/生育津贴，以当地医保局、卫健委和社区通知为准。",
                                12, 4,
                                "生育登记办好后，保留好所有产检发票和病历，产后按当地流程申领生育津贴。"),
                        new PregnancyModule.Milestone("hospital_choice", "考察并确定生产医院与病房",
                                "对比医院产科水平、无痛分娩、LDR/单/双人间、探视与陪护政策、费用；热门病房需提前数月预约。最好与产检建档医院一致，避免转院麻烦。",
                                24, 4,
                                "确定医院后了解病房预约时间和无痛分娩流程，孕晚期再确认一次。"),
                        new PregnancyModule.Milestone("birth_plan", "确认分娩方式与入院流程",
                                "和医生确认顺产/剖宫产条件、无痛分娩申请流程；明确入院要带的证件资料、急诊入口、联系人与陪护安排，足月后随时可能发动。",
                                34, 2, null)
                ));
    }

    /** 必选：产后照护（月嫂/月子、新生儿护理、证件办理）。 */
    private static PregnancyModule postpartumCare() {
        return new PregnancyModule("postpartum_care", "产后照护", Audience.BOTH, true,
                "postpartum_care", "月嫂/月子预订、新生儿护理准备与产后证件办理",
                List.of(
                        new PregnancyModule.Milestone("maternity_booking", "筛选并签约月嫂/月子中心",
                                "好的月嫂/月子中心需提前3~6个月预订。明确预算、服务天数、是否带新生儿、通乳/月子餐能力，查证件看口碑，签合同写明违约与替换条款；建议孕24~28周前敲定。",
                                24, 4, null),
                        new PregnancyModule.Milestone("newborn_prep", "学习新生儿护理并备好用品",
                                "提前学习喂奶/拍嗝/洗澡/换尿布、黄疸与脐带观察；备齐纸尿裤、包被、衣物、安全座椅等，安排好产后夜间分工。",
                                32, 2, null),
                        new PregnancyModule.Milestone("postpartum_paperwork", "备齐产后证件办理材料",
                                "出生医学证明（起名后办理）、上户口、新生儿医保参保（建议出生90天内参保可追溯报销）、生育津贴申领。提前准备双方证件与银行卡。",
                                36, 2, null)
                ));
    }

    /** 可选：体重管理。 */
    private static PregnancyModule weight() {
        return new PregnancyModule("weight", "体重管理", Audience.FEMALE, false,
                "weight", "按孕前BMI设定各孕周增重目标，每周记录",
                List.of(
                        new PregnancyModule.Milestone("确定增重目标并开始每周记录体重",
                                "按孕前BMI：偏瘦总增重12.5~18kg、正常11.5~16kg、超重7~11.5kg、肥胖5~9kg。每周固定晨起空腹称重，孕中晚期每周约0.3~0.5kg。",
                                12, 0,
                                "坚持每周称重，孕中晚期复盘增速并调整饮食运动。"),
                        new PregnancyModule.Milestone("复盘体重增速并调整饮食运动",
                                "连续两周增重过快/过慢时，复查饮食结构并适度增加散步/孕妇瑜伽，必要时咨询营养科。",
                                28, 0, null)
                ));
    }

    /** 可选：饮食和营养（合并原饮食指导与营养管理）。 */
    private static PregnancyModule dietNutrition() {
        return new PregnancyModule("diet_nutrition", "饮食和营养", Audience.FEMALE, false,
                "diet_nutrition", "分阶段补充叶酸/铁/钙，搭配三餐、控糖控盐",
                List.of(
                        new PregnancyModule.Milestone("建立孕期饮食与营养方案",
                                "每日0.4~0.8mg叶酸至孕12周；少食多餐、主食粗细搭配、保证优质蛋白与蔬果；忌生鱼生肉、未熟蛋、未消毒奶、酒精、过量咖啡因。",
                                10, 0,
                                "叶酸补到孕12周，孕中晚期起关注铁、钙补充，24~28周糖耐结果出来后针对性调整。"),
                        new PregnancyModule.Milestone("关注补铁补钙，按需加DHA",
                                "孕中晚期易缺铁缺钙，可在医生指导下补充铁剂、钙剂；铁剂与钙剂错开服用，配合维C促进吸收。",
                                20, 2, null),
                        new PregnancyModule.Milestone("按糖耐结果调整控糖饮食",
                                "若诊断妊娠糖尿病，需定时定量、低GI饮食、餐后运动并监测血糖，必要时营养科就诊。",
                                28, 0, null)
                ));
    }

    /** 可选：待产准备。 */
    private static PregnancyModule birthBag() {
        return new PregnancyModule("birth_bag", "待产准备", Audience.BOTH, false,
                "birth_prep", "分妈妈/宝宝/证件三类，足月前备齐并打包就位",
                List.of(
                        new PregnancyModule.Milestone("待产包打包就位、确认入院路线",
                                "妈妈：产褥垫、卫生巾、哺乳内衣、防滑拖鞋、出院衣物；宝宝：纸尿裤、包被、衣服、奶粉备用；证件：身份证、医保卡、产检资料、银行卡。30周起开始采购，36周前打包放门口，确认去医院路线与夜间停车。",
                                36, 6, null)
                ));
    }

    /** 可选：孕产知识学习。 */
    private static PregnancyModule knowledge() {
        return new PregnancyModule("knowledge", "孕产知识学习", Audience.BOTH, false,
                "knowledge", "学习孕周变化与危险征兆，做到能判断、能提醒",
                List.of(
                        new PregnancyModule.Milestone("学习孕周变化与危险征兆",
                                "了解各孕周胎儿发育和产检重点；记住需立即就医的信号：腹痛、出血、持续头痛/视物模糊、严重水肿、胎动异常、破水、规律宫缩等。",
                                12, 0, null)
                ));
    }
}
