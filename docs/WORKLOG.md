# LuzzyRP 工作日志（WORKLOG）

> 规则：每次工作会话必须追加一节，格式固定为「日期 / 完成 / 决策 / 遗留 / 下一步」。本文件是跨会话的连续记忆，后续开发 Agent 接手前必读。

---

## 2026-08-30 · 会话 1：项目启动 + P0 阶段

### 完成
- **探索与规划**：通读 docs 内全部资料（17 份历史任务文档、design-audit-v0.10.1.md、rikkahub-master 源码架构、图标/字体/世界卡资产盘点），产出 `docs/PLAN-v0.1.0.md` 详细计划并获用户批准。
- **环境核验（P0-1）**：
  - JDK 21.0.11（Microsoft OpenJDK，JAVA_HOME 已配置）✅
  - Android SDK：`C:\Android\sdk`，platforms 含 android-34/35/36/37.0，build-tools 34/35/36（37 可由 AGP 自动安装，licenses 已接受）✅
  - Gradle：不在 PATH → 采用 wrapper 9.4.1（分发包随 wrapper 下载）✅
  - Git 2.54.0 / Python 3.11.14 ✅
  - gh CLI 已登录 **LuzzyMeow** 账号；token scopes：gist / read:org / **repo**（**无 delete_repo**）⚠️
  - SSH：`ssh -T git@github.com` → "Hi LuzzyMeow!" 认证成功 ✅
- **文档体系落盘（P0-2）**：`.gitignore`、`docs/PLAN-v0.1.0.md`、`docs/WORKLOG.md`、`HARD_REQUIREMENTS.md`、`docs/INVARIANTS-CHECKLIST.md`、`CHANGELOG.md`、`README.md`、`LICENSE`（CC BY-NC 4.0）。

### 决策
1. **全新重建**：LuzzyRP 为全新代码库，版本线 v0.1.0 / versionCode 1 起；**不沿用旧仓库发展路线与版本历史**（用户明确指示）。旧 docs/task 17 份文档仅作细节规格参考。
2. 实现蓝本锁定 rikkahub-master（流式 SSE / GenerationHandler / Room / Navigation3 直接移植模式）。
3. 设计基线沿用 Aurora Dual 令牌（AuroraPink #FF6EC7 / AuroraViolet #B57BFF；亮 #FAF7F2 / 暗 #0E1116；动效 300/150/195ms）。
4. 字体默认核心 6 字重（PuHuiTi 55/65/85 + AlibabaSans Regular/Medium/Bold），APK ≈ 30MB。
5. v0.1.0 = RP 核心全功能；TRPG 模式 v0.2.0 专项。
6. applicationId = `com.luzzymeow.luzzyrp`。
7. 旧仓库 Luzzy-RpTRPG：先备份（docs/archive/，gitignore）→ 新仓库首推成功后删除（gh token 无 delete_repo scope，届时需 `gh auth refresh -s delete_repo` 设备码授权或用户手动删除）。

### 遗留
- 旧仓库删除依赖 delete_repo 授权（交互式设备码流程），见上。
- Android SDK build-tools 37 未装（AGP 首次构建会自动安装，licenses 已接受）。

### 下一步
- P0-3 备份旧仓库 → P0-4 创建 LuzzyMeow/LuzzyRP 并首推 → P0-5 删除旧仓库 → P0-6 Gradle 脚手架 → P0-7 资产管线 → P1 模型层。

---

## 2026-08-30 · 会话 2：P0-P4 主体完成

### 完成
- **P0 收口**：Gradle 9.4.1 wrapper（复用 rikkahub 启动器）+ 首次构建走通（修掉 3 个 AGP9/Kotlin2.4 兼容问题：buildconfig 属性移除、java 包名遮蔽、JVM target 不一致）；`gh repo create LuzzyMeow/LuzzyRP` 创建并首推成功（SSH）。
- **P0-7 资产管线**：`tools/icon_pipeline.py` 生成 815 枚图标资源 + GameIcons.kt（12 类）+ LobeIcons.kt（lucideExtra 37 枚机械转 VectorDrawable）+ LuzzyIcons.kt 语义别名（92 项全解析）+ 启动图标（legacy+adaptive）+ 通知 small_icon（猫）；6 字重字体落位 res/font。
- **P1 模型层**：UIMessage 多部件模型、ToolApprovalState 五状态、MessageChunk 合并代数（按角色合并——OpenAI delta 无稳定 id，rikkahub 同款语义）、PNG tEXt 读写器；ChunkMergeTest/PngTextChunkRoundTripTest 全绿。
- **P2 AI 层**：OpenAI 兼容/Anthropic/Google 三协议 SSE 真流式（callbackFlow+trySend 逐 event+awaitClose cancel，[INVARIANT-STREAMING] 注释块）；主机思考参数适配（方舟/智谱/月暗 thinking.type、DeepSeek reasoning_effort、OpenRouter reasoning）；TagToolCallParser（跨 delta 切分安全、截断尽力解析、标签不上屏）；8 测试全绿。
- **P3 数据层**：Room v1（9 实体、8 DAO、WAL、schemas 导出、vec0 虚表 onOpen 创建）；SettingsStore（DataStore JSON blob）；ConversationRepository（分支树/重roll）；CharacterCardRepository（ST v2/v3 PNG/JSON 导入导出、内置卡鹿溪）；WorldbookRepository（三策略召回）；MemoryRepository（ACE Execute/Update+余弦去重+评分淘汰）；VectorIndex（sqlite-vec 维度自适应重建）；DataSourceModule 全接线。
- **P4 生成管线**：NsfwBlock 占位（用户手填+不可触碰警示）；AgenticProtocol 内置提示词强化（>2 轮思考、>1 次主动调用）；TaskPrompts（A/B/C 摘要+ACE 反思/提取+标题）；ToolRegistry+BuiltinTools（world_keyword_search/memory_search/current_time）；GenerationHandler（256 步、原地回填、三 break、审批续跑、标签兜底、两阶段 maxLoops=3）；PromptAssembler（KV 三层布局：稳定前缀系统消息→append-only 历史→尾部动态块）；ChatService（单一真源、流式直写、节流落库、审批续跑、后处理链：标题/摘要/ACE）；GenerationLoopTest 4 用例全绿（含原地回填断言、审批暂停续跑断言）。
- **KV Golden**：KvPrefixStabilityGoldenTest——同历史两次组装逐字节相等 + 易变字段不进前缀断言，全绿。

### 决策
1. 合并代数按**角色**判定（delta 无稳定 id；rikkahub 同款）。
2. RP 两阶段落地为「工具轮次上限 3」：轮次内=阶段一（思考+工具，流思考卡片/工具卡片），轮次耗尽不再提供工具=阶段二（基于结果写正文）。普通对话 UNLIMITED（仅 256 步约束）。
3. KV 分层落位：稳定前缀=system 首消息；动态内容（A/B 摘要+记忆 Top-K+被动召回）置于历史**之后**的 system 块（保护历史前缀缓存）。
4. sqlite-vec 虚表维度自适应：首次写入按实际维度重建；嵌入冗余 JSON 存实体列供真实余弦去重。
5. ProviderGateway 接口抽出（Handler/ChatService 依赖抽象，测试可注入假网关）。

### 遗留
- **旧仓库删除阻塞**：gh token 无 delete_repo scope，需用户执行 `gh auth refresh -h github.com -s delete_repo`（设备码授权）后由 Agent 重试 `gh repo delete LuzzyMeow/Luzzy-RpTRPG --yes`，或用户在 Settings→Danger Zone 手动删除。备份已在 docs/archive/（gitignore）。
- 流式真机实测待用户提供 API Key（DeepSeek/方舟）。

### 下一步
- P6/P7 UI 全量（Navigation3 路由壳、聊天页思考卡片时间线、角色卡库、设置族、记忆页）→ P8 发版。

---

## 2026-08-30 · 会话 3：P6/P7 UI 全量 + v0.1.0 发版

### 完成
- **P6/P7 UI**：RouteActivity（Navigation3 NavDisplay，16 路由，三态转场 300/195ms）；聊天页（思考卡片三态节点 + 工具卡片审批按钮 + 双色气泡 + 流式跟随 + 回到底部箭头 + 发送/Stop 同位切换）；首页（列表/置顶/搜索/抽屉/新建会话带鹿溪开场白）；角色卡库（PNG/JSON 导入 + 详情编辑/导出/只读保护）；世界书/记忆/收藏/历史页；设置族（供应商列表+详情/生成参数含温度滑条与历史轮数 0-200/外观含四种主题模式/关于）。AuroraSurface 交互引擎、LuzzyTextField、MarkdownText 轻量渲染。ViewModelModule Koin 工厂（含 parametersOf 路由参数）。
- **P8 发版**：生成 4096 位发布 keystore（30 年有效期，口令记录 docs/RELEASE-KEY.md，均 gitignore）；assembleRelease 签名成功（arm64 20.7MB / x86_64 20.7MB / universal 21MB，R8 minify）；CHANGELOG v0.1.0 完整条目；INVARIANTS-CHECKLIST 自检记录；tag v0.1.0 + GitHub Release（附 universal APK）。

### 决策
1. Markdown 渲染 v0.1.0 采用零依赖 AnnotatedString 子集解析（粗/斜/代码/删除线），第三方 Markdown 引擎列为 v0.2 候选。
2. 会话标题自动生成（首轮后一次性任务）。

### 遗留
- 旧仓库删除仍阻塞（delete_repo scope，见会话 2 遗留）。
- 流式真机逐字验收待用户提供 API Key；Android 模拟器实测（安装/启动/会话流）列为下一会话首选任务。
- 世界书条目全功能编辑页（增删改条目）与正则编辑器 UI 在 v0.2 完整交付（数据层与召回引擎已就绪）。

### 下一步
- 真机/模拟器实测流式逐字 → 世界书/正则编辑器 UI → v0.2.0（TRPG 模式专项）。

---

## 2026-08-30 · 会话 4：模拟器工具链搭建 + 全流程实测 + v0.1.1 热修

### 完成
- **模拟器工具链**（用户禁用 USB 真机 A9210，真机上的应用已卸载清理）：sdkmanager 安装 emulator + system-images;android-35;default;x86_64 → 创建 AVD `LuzzyRP_Test`（pixel_6）→ 启动 Android 15 模拟器（emulator-5554）。
- **全流程实测**：安装 → 启动 → 首页（Aurora 主题/图标/搜索框渲染 ✓）→ 新建对话 → 聊天页（鹿溪开场白完整渲染 ✓）→ 注入文本发送 → 错误条优雅提示（无 Key 场景 ✓）。UI 自动化走无障碍树（contentDescription 可寻址）。
- **实测发现并修复 3 缺陷**：① AdaptiveIcon 不可 painterResource（启动崩溃，改 luzzy_logo.png 资源）；② WindowInsets 未处理（顶栏入状态栏，加 systemBarsPadding）；③ 种子数据未接线（ensureBuiltinCard/Worldbook/Presets 在 LuzzyApp 启动时幂等初始化）。
- **v0.1.1 发版**：versionCode 2，CHANGELOG 条目，tag + Release 替换修复版 APK。

### 决策
1. `input text` 不支持中文注入（系统限制），ASCII 文本完成流程验证；中文流式验收待用户提供 API Key 后真机/模拟器手测。
2. 模拟器保持运行（LuzzyRP_Test @ emulator-5554），供后续迭代连续测试。

### 遗留
- 旧仓库删除仍阻塞（delete_repo scope）。
- 流式逐字验收待 API Key。

### 下一步
- 世界书/正则编辑器 UI → v0.2.0（TRPG 专项）。
