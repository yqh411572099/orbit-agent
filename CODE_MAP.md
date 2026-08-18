# 智能管家代码地图

## 分层结构
- `src/main/java/com/butler/perception`：接口与流式入口，包括 REST API、SSE、DTO；只做协议转换，不写业务规则。
- `src/main/java/com/butler/application`：用例编排层，负责聊天、创建目标、记忆提炼、任务查询、关注项更新、清理等应用流程。
- `src/main/java/com/butler/domain`：领域模型、仓储接口、权限规则、场景扩展点；不依赖 Spring/JPA。
- `src/main/java/com/butler/infrastructure`：JPA 持久化、Spring AI 适配器、定时任务、政策抓取等技术实现。
- `src/main/resources/static/index.html`：单页前端，渲染主对话、子对话、任务分组、记忆与目标创建表单。

## 核心入口
- `ChatAppService`：统一聊天入口。主对话先识别创建目标意图；子对话消息归档后交给 `ConversationAppService` 处理状态影响。
- 主对话建目标前，若场景声明 `researchBeforeCreate()`，先走联网调研 → 推送 `goal_proposal` 确认卡 → 用户确认后再建目标；待确认方案存在 `PendingGoalProposalStore`。
- `ConversationAppService`：子对话消息的统一影响管线：解析当前场景状态 → LLM 事件提取 → 更新 collectedInfo → 重算时间轴 → 合并结构化记忆 → 标记完成 → 必要时调整动态任务。
- `GoalAppService`：创建目标。解析场景字段、默认/依赖关注项，创建 mission/sub_session，优先使用场景确定性时间轴，没有时再使用 LLM 初始任务。
- `MemoryExtractionAppService`：2 小时增量记忆提炼任务，合并所有场景属性 schema，写入中立 `user_memory` 和 `memory_session_rel`。
- `PendingGoalProposalStore`：主对话“待确认后再建目标”的方案暂存（内存 + TTL）。
- `asr/`：浏览器 → 后端 → 火山单流 ASR 的 WebSocket 二进制帧代理（`AsrWebSocketHandler` + `AsrFrameCodec`），建会话后等服务端 ACK 再放行音频。

## 场景扩展点
- `ScenarioDomain`：新增目标场景的核心接口。每个场景独立描述字段、关注项、属性目录、强类型 Attribute、确定性时间轴。
- `domain/scenario/builtin/PregnancyScenario.java`：孕期场景，基于预产期和关注项生成孕周时间轴。
- `domain/scenario/builtin/ExamPreparationScenario.java`：考研场景，基于考试日倒排复习时间轴。
- `domain/scenario/builtin/CertPreparationScenario.java`：考证场景，基于考试日倒排备考时间轴。
- `domain/attribute/Attribute.java`：结构化属性基类；每个场景定义自己的 Attribute 子类，未知字段通过 extras 透传。

## 通用状态模型
- 用户输入只有两类业务影响：
  - 创建/更新目标及时间轴：关键字段、里程碑完成、关注项增减、确定性任务重排。
  - 更新个人信息和记忆：场景 Attribute upsert，长期记忆由定时提炼补充。
- `ScenarioStateSupport`：在 `SubSession.collectedInfo` 文本与 `collected/focusAreas` 结构化状态之间转换。
- `TimelineAppService`：通用时间轴同步器，按 `PlannedTask.title` 幂等创建/更新/删除任务，保留已完成任务。

## 持久化
- 仓储接口在 `domain/repository`，JPA 实现在 `infrastructure/persistence/adapter` 和 `jpa`。
- 核心表：`raw_chat_log`、`sub_session`、`mission`、`task`、`user_memory`、`memory_session_rel`、`main_session`。
- 换数据库只替换 persistence adapter/JPA 配置，不修改 domain/application。

## 自测脚本
- `scripts/verify.sh`：结构化记忆与三场景创建主路径。
- `scripts/verify_scenario_intent.sh`：对话触发字段更新、时间轴重排、结构化记忆更新。
- `scripts/verify_pregnancy.sh`：孕期刚性/可选关注项、提前量、任务详情、完成推进、依赖关系。
- `scripts/verify_resync.sh`：关注项增删、预产期变更后的时间轴重排和记忆同步。

## 通用能力 vs 域定制边界（重要）

设计目标：引擎与业务知识分离。新增一个目标场景只需实现 `ScenarioDomain`，通用层不改。

### 通用层（不感知具体场景，跨所有目标复用）
- **三层架构**：perception（REST/SSE/DTO）、application（用例编排）、domain（模型与扩展点）、infrastructure（JPA/LLM/调度/外部服务）。
- **对话与流式**：`ChatAppService` 的聊天管线、SSE 事件（chunk/reasoning/goal_created/goal_proposal/change_proposal/done/error）、思考过程折叠与持久化、主/子对话权限隔离。
- **记忆系统**：2 小时增量提炼、中立记忆库 `user_memory`、多对多关联 `memory_session_rel`、结构化属性 Attribute（基类 + extras 扩展）。
- **时间轴与任务引擎**：`TimelineAppService.resync()` 按域返回的 `PlannedTask` 幂等创建/更新/删除、保留已完成、关键字段变更后重排；任务的提醒时间/分组/历史折叠逻辑通用。
- **定时任务（确定性，不依赖 LLM）**：
  - `ReminderService`（固定间隔）：到提醒时间把待办推进到对应子对话。
  - `MemoryExtractionJob`（每 2 小时 cron）：增量记忆提炼与关联绑定。
- **工具体系**：`ToolCategory` 大类 + 子工具、参数 schema 自动合并、按信息可靠性分流（结构化权威数据用工具、时效文本用联网/知识库）、工具结果优先于模型猜测。内置 GeoService（高德地址解析/周边 POI）、知识库、WebSearch 等均为场景无关能力。
- **持久化抽象**：仓储接口在 domain，实现可替换；换库只改 adapter。

### 域定制层（每个 ScenarioDomain 自己定义）
- **时间锚点与"处境摘要" `situation()`**：基于 collected 中的关键日期，确定性算出"现在处于什么阶段"，注入系统提示。
  - 孕期：预产期 → 当前孕周（X周+Y天）、距预产期天数。
  - 考研/考证：考试日 → 剩余天数、当前复习阶段（基础/强化/冲刺等）、考前提醒。
- **时间轴模板 `plannedTasks()`**：该场景有哪些确定性节点（产检/建档/报名/模考等）、提前量、关注项归属。
- **收集字段 `collectFields()`**：创建目标时需要哪些必填/选填信息（预产期、考试日、城市等）。
- **关注项 `focusAreas()`**：可选模块、受众、是否必选、依赖关系。
- **结构化属性 `attributeClasses()`/`attributeCatalog()`**：该场景需要长期结构化记忆的对象类型。
- **初始任务、字段归一化、意图解释等**：与该场景业务知识相关的规则。

### 判断标准（避免 case-by-case 打补丁）
- 一个改动如果**只对某个场景的业务知识成立**（孕周公式、复习阶段阈值、产检节点），放进该域的 `ScenarioDomain` 实现。
- 一个改动如果**对所有场景或"人如何获取信息"普遍成立**（先定位参照点再查周边、工具结果优先、按可靠性选来源、时间轴幂等重排），放进通用层。
- 任何写死的场景关键词（如把"妇产医院"硬编码进工具路由）都视为坏味道，应改为通用意图判断。
- 还可声明建前调研开关 `researchBeforeCreate()` 与要点 `researchBrief()`，场景只声明“要不要查/查什么”，调研流程在通用层。
- `domain/scenario/builtin/CertPreparationScenario.java`：考证场景，基于考试日倒排备考时间轴（含报名/查成绩/领证节点、报名城市字段、单个必选关注块），并开启建前联网调研。
