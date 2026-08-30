# AGENTS.md — butler 工程协作规则

## 技术栈与约定
- Java 21 · Spring Boot 3.3.5 · Spring Data JPA · Spring AI（Ark/OpenAI 兼容）· H2 文件库 · DDD 分层。
- 严格分层依赖：`perception → application → domain ← infrastructure`；`domain` 不依赖框架。
- 持久化面向 `domain/repository/*` 接口；换 DB 只新增/切换 `infrastructure/persistence/adapter`，不改领域与应用层。
- 新增场景：在 `domain/scenario/builtin` 新增 `ScenarioDomain` Bean + 其强类型 Attribute，框架自动注册，不改提炼/上下文主流程。
- 认知架构遵循《智能管家 认知架构与工具体系.md》：agent 是同一个人，场景只改变"知道什么/关心什么/有哪些工具"，**不改变思考与获取信息的方式**。
- 禁止在 application/infrastructure 写 `"pregnancy".equals(...)`、`contains("附近")` 这类场景/关键词特判；"要不要查、查哪类工具"由模型按分层工具决定，场景只声明知识/处境/挂载的工具大类。
- 不提交 git（除非用户明确要求）。

## 排错的思维方式（避免字段级补丁）
模型出一个 badcase 时，默认冲动是顺着那条数据路径，在末端字段/提示词里补一句规则把它拦住（如在 metric 规则里写"不要把昨天的摄入并进今天"）。这样能让眼前 case 变绿，但下一个字段、下一个场景照样犯——这是反复踩坑的根源。

差异在解决问题的层次：
- 我的坏习惯：把"这个 case 错了"当目标，在**症状出口**（字段规则、单场景）打补丁，图最小改动、最快见效。
- 正确思路：先问"模型是在哪一步开始理解错的"。绝大多数这类问题根因在**认知入口**——实体没锚定（"吃了啥/今天早上/那个/附近"没解析成具体时间、人物、任务、地点、指标），下游记账/抽取时只能从历史里乱抓凑数。

动手前先定层：感知（理解/锚定）、记忆（存储/权限）、行动（编排/提醒）。根因在理解入口，就不允许去下游字段补规则。判断标准沿用"通用 vs 域定制"：只有域知识（孕周公式、复习阶段、产检节点）才进 `ScenarioDomain`；其余一律做成对所有场景、所有同类输入生效的通用机制。当我想靠"在 prompt 里再加一句兜底"解决问题时，那是补丁信号——要么做成结构性机制（如入口锚定），要么不做。

## 代码风格
- 优先复用现有模式（record DTO、仓储适配器、`@Component` 场景域）。
- 不添加版权/许可头；不加无关注释。
- 结构化记忆属性必须继承 `domain/attribute/Attribute`，用 `type` 做多态路由；未定义字段靠基类 extras 透传，不要新造扁平字段。

## 交付前必须自测（硬性要求）
> 交付给用户的程序不允许出现低级逻辑 bug（编译不过、契约未同步、序列化丢字段、功能未生效）。
> 所有改动在告知用户“完成”之前，必须走完下面的闭环，并自己修到通过，不能把验证甩给用户人工测试。

1. **编译闭环**：`mvn -o -DskipTests package` 必须成功（含 test-compile）。
2. **启动闭环**：用新 jar 启动服务，确认 `http://localhost:8080/` 返回 200 且日志无异常。
   - 本机用固定标题、可复用的 Terminal 窗口前台运行，**禁止新建散落窗口、禁止 `nohup ... &` 后台启动**。
   - 一键重启两个服务（Milvus Lite + butler）：`bash scripts/start_all.sh`（按窗口标题 `milvus-lite`/`butler` 复用已有窗口，先停旧进程再前台 `exec` 启动，并等待就绪）。
   - 只重启 butler：`bash scripts/start_butler_window.sh`。**不要使用 `/tmp/start_butler.sh`（已删除）。**
   - 两个 run 脚本都是前台 `exec`，用户可在对应窗口 Ctrl+C 或关窗口停止；确保 8080 跑的是最新构建。
3. **端到端验证**：运行 `scripts/verify.sh`（属性化记忆主路径）必须全绿；改动时间轴/场景意图还要跑 `scripts/verify_scenario_intent.sh`；改动任务面板/折叠/过期状态/提醒还要跑 `scripts/verify_task_panel.sh`、`scripts/verify_dates_reminders.sh`；改动孕期时间轴还要跑 `scripts/verify_pregnancy.sh`、`scripts/verify_resync.sh`。新增/改动功能要同步补充对应脚本的断言，不能只靠 `mvn test`。
   - 关注项的强制/依赖关系由后端 `resolveEffectiveFocusAreas` 解析，前端只渲染、不实现联动逻辑。
4. **契约同步检查**：改了 `LlmPort`、领域模型、DTO、仓储接口的方法签名/字段后，全局搜索所有调用方与测试一并更新，再回到第 1 步。
5. **数据卫生**：验证脚本产生的测试数据用 `POST /api/admin/purge-all` 清理，不把脏数据留给用户。
6. 单测 `mvn test` 在本机沙箱可能因 byte-buddy 加载失败，属环境问题；以集成脚本验证为准，但不要因此让测试代码编译失败。

## 环境备忘
- JDK：`/Library/Java/JavaVirtualMachines/jdk-21.0.11+10/Contents/Home`；Maven：`/usr/local/bin/mvn`。
- 模型 key 用环境里的 `ARK_API_KEY`，不要清除或写死。
- 沙箱内无法直接 `curl localhost`，启动/验证需要提权在独立 Terminal/进程执行。
- `python3`/`git` 可能打印 xcrun_db cache 警告，忽略即可（看实际输出/退出码）。
   - **只跑与本次改动直接相关的脚本，不要每次全量回归**（脚本会越来越多、越跑越慢）。只有改动了多场景共用的核心抽象（记忆/上下文组装/时间轴同步/会话路由）时，才扩大到 `verify.sh` 做一次冒烟。

## 版本发布与仓库规则
- 两个远程仓库：`origin` = 私有 `yqh411572099/AI-Butler`，`orbit` = 公开 `yqh411572099/orbit-agent`。推送用 SSH 密钥 `~/.ssh/id_ed25519_github`（`GIT_SSH_COMMAND="ssh -i ~/.ssh/id_ed25519_github -o IdentitiesOnly=yes -o StrictHostKeyChecking=accept-new"`），沙箱内 git/ssh-agent 不可用，需提权。
- **正式版本（release，打 tag）**：同时推到 `origin`（AI-Butler）和 `orbit`（公开 orbit-agent），tag 两边都推。推公开仓库前必须确认无密钥泄露（所有 key 走环境变量，`.env`/`data/`/日志/`target/` 已在 .gitignore）。
- **快照/日常提交（snapshot）**：只推 `origin`（AI-Butler），不推 `orbit`。
- **打完正式包立即回快照**：发布正式版本（pom 为 `X.Y.Z`）并打好 tag 后，立刻把 `pom.xml` 版本改成下一个 `X.Y.(Z+1)-SNAPSHOT` 并提交，避免主干长期停留在 release 版本。
- jar 名随版本变化，启动脚本用 `target/butler-*.jar` 动态解析，不要在脚本里写死版本号。
