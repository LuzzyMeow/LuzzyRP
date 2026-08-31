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

---

## 2026-08-30 · 会话 5：旧仓库删除确认 + APK 桌面交付

### 完成
- **旧仓库已删除**（用户手动执行，API 返回 404 确认）：LuzzyMeow/Luzzy-RpTRPG 不复存在，本地 docs/archive/ 备份保留（gitignore）。至此 12 条硬性规定的遗留项全部清零。
- **桌面交付**：v0.1.1 签名 APK 两份已复制到用户桌面（universal 21.1MB / arm64 20.8MB，SHA256 前缀 09b96518b66afee2），等待用户手动检阅并给出更新方向。

### 遗留
- 无阻塞项。等用户检阅反馈后按新方向迭代。

### 下一步
- 依据用户检阅反馈确定 v0.2.0 更新方向（候选：世界书/正则编辑器完整 UI、流式逐字中文验收、TRPG 模式专项）。

---

## 2026-08-30 · 会话 6：v0.2.0 —— 设计技能落地 + 全功能迭代

### 完成
- **SKILL 强制落地（规定 13）**：4 项设计 SKILL 克隆存档 docs/skills/（open-design zip 下载反复断连，内容经官方 README/协议文档完整掌握）；HARD_REQUIREMENTS 增补规则 13；创建 DESIGN.md（仓库根设计契约）+ docs/AGENT-GUIDE.md。
- **思考深度研究**（anysearch 核实）：DeepSeek reasoning.effort/none-high-max；GLM-5.3 仅 effort 三档；Opus 4.7+ adaptive+output_config.effort；GPT reasoning_effort。实现 ThinkingDepthAdapter 按 id 检测家族。
- **图标黑边根因**：815 枚全为 P 模式索引色（透明区 RGB 杂色 + 硬边）→ 管线升级 RGBA 清洗 + bbox 归一化（occupancy 0.72 统一大小）+ 边缘平滑；启动图黑晕清除。
- **C1-C3**：注入位置全集 + depthRole；PromptPreset/PresetEntry + Room v2 手写迁移；UserProfile；ThinkingDepth；TokenCountParser；PromptAssembler 预设/档案/@Depth 注入（KV 三层保持）；AppLogger（环形内存 + JSONL 3 天 + SAF 导出/分享）；ChatService 全链日志 + 上次会话记忆 + 思考深度合并 extraBody。
- **UI 重构**：菜单（聊天/角色卡/预设/用户档案/设置）；启动直达（首次建鹿溪会话）；聊天页历史+搜索 BottomSheet + 切换会话；预设列表/编辑页（条目 CRUD + 位置选择器 + @Depth）；用户档案页（头像/名字/身份）；角色卡详情重构（hero 头像/背景+透明度/<CUT> 开场白/世界书入口/新建模式）；世界书二级编辑器（条目全字段 + ST 导入默认启用 + 删书）；设置菜单并入记忆；供应商增删 + 模型新增表单（单位换算 + 自检 + 深度档位）+ 生成参数并入；关于页 CHANGELOG 渲染 + 日志查看/导出/分享。
- **实测**：Android 15 模拟器 v0.2.0 启动直达鹿溪会话 ✓、聊天页历史/搜索入口 ✓、图标干净 ✓。
- **发版**：v0.2.0 / versionCode 3；CHANGELOG/README 同步；tag + Release（附 universal APK）+ 桌面交付。

### 决策
1. open-design 完整 zip 未能入库（网络反复断连）——已通过官方文档掌握内容，zip 存档列为待补；其余 3 仓库完整入库（移除内嵌 .git 保证克隆自包含）。
2. @Depth 语义实现为「距末尾消息数」，注入角色可选 system/user/assistant（对齐 RP-Hub）。
3. 聊天背景分层渲染因 ColumnScope 作用域问题本次回退（背景数据链路已就绪），列为下轮打磨首项。

### 遗留
- 聊天背景图渲染（数据/UI 链路已通，待图层作用域修复）。
- 记忆设置页拓展滑条（容量/TopK/阈值 UI，数据层字段已就绪）。
- 用户档案头像在菜单展示；流式逐字中文验收仍待 API Key。

### 下一步
- 聊天背景图层 + 记忆滑条 + 用户头像菜单展示 → 视觉打磨轮。

---

## 2026-08-30 · 会话 7：v0.3.0 主题/UI/UX 完全重制

### 完成（对上轮缺口的补救）
- **Phase 0**：ui-ux-pro-max 检索实操（design-system 基线 + 回退规则）；huashu 动效纪律提炼。
- **Phase 1 主题令牌 v2**：AuroraColor 完整 M3 方案（surfaceContainer 全族/inverse/scrim/AMOLED 独立方案）；AuroraBrush 渐变系统；LuzzyElevation 五层；LuzzyIconSize 四级；LuzzyCorner 五档；MotionTokens v2 场景化。
- **Phase 2 组件 v2**：AuroraTopBar+TopBarAction、LuzzyDialog（三态进出）、EmptyState、AuroraSurface v2（scale 0.98 按压）。
- **Phase 3 逐页重制**：ChatPage（背景分层 Box/气泡 v2 极光淡染 vs 纸面卡描边/渐变发送键/顶栏半透明）；首页（时段问候+渐变标题+空态）；五页顶栏换装；RouteActivity 转场 v2（缓动曲线化+fade 常驻）。
- **Phase 4**：模拟器亮色主题巡检（渐变按钮/纸面气泡/顶栏 v2 可见生效）；单测+构建绿。
- **Phase 5 发版**：v0.3.0 / versionCode 4；CHANGELOG 专章；tag+Release+桌面交付。

### 决策
1. ui-ux-pro-max CSV 无极光系匹配 → 按其规则回退 DESIGN.md 品牌契约色，采用其结构（令牌/对比度/动效分层）。
2. 转场位移降到 1/6-1/8 屏且 fade 常驻（消除滑动残影，huashu「少即是多」）。
3. 按压反馈统一 scale 0.98 + graphicsLayer（不动布局，pro-rules「Stable Interaction States」）。

### 遗留
- 暗色/AMOLED 主题截图巡检（方案已实现，截图待补）。
- LuzzyIconSize 全项目替换尚有残留硬编码 dp（重点页面已换）。
- 用户档案头像在抽屉头部展示；聊天背景透明度实时预览。

### 下一步
- 暗色巡检 + 图标尺寸全量替换 + 弹窗统一替换 AlertDialog 调用点。

---

## 2026-08-30 · 会话 8：仓库状态改为「开发中 · 不可游玩」

### 完成
- 用户已手动删除全部旧 Release；README 重写：顶部 WARNING 大字警示块（开发中/不支持正常游玩/不保证数据兼容/Release 不附 APK）+ 能力完成度表 + 路线图（v0.4 为首个可玩版本目标）。
- 发布说明性 Release（v0.3.0 tag，**标记 pre-release、不附 APK**）：明确 WIP 状态与已知未验收项。
- CHANGELOG 同步 assets（关于页渲染源）。

### 决策
1. 后续发版策略调整：**v1.0 前所有 Release 一律不附 APK、标记 pre-release**；首个可玩版本（预计 v0.4 验收完成后）才恢复附 APK 的稳定版发布。
2. README 状态徽章置顶 Status: WIP。

### 下一步
- 暗色/AMOLED 巡检 + 组件统一收尾（见会话 7 遗留）。
