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

---

## 2026-09-01 · 重建会话 1：定案 + 文档体系 + Phase 0/1 壳骨架

> **重要**：本会话起，项目全面转向 RP-Hub 二次开发（用户拍板），旧 Kotlin/Compose 工程作废（备份于 git tag `legacy-v0.3.0`）。版本线自 v1.0.0 / versionCode 1 重新起算。

### 完成
- **可行性探索**：摸清 RP-Hub（纯前端 Vue CDN + Tailwind CDN，CC BY-NC 4.0 与旧工程一致，二创合规；NSFW 预设位于 built-in-content.js；字体栈为 --app-font-modern/--app-font-serif + Lora；update-check 指向 rphub-presence.zeabur.app）+ 壳技术选型论证（原生 WebView 壳，三层结构：上游层/扩展层/原生层）+ 上游同步机制论证（覆盖 + patch 重放）。
- **决策落盘**：`docs/PLAN-v1.0.0.md`（10 条决策 + 9 Phase 实施计划 + 同步 SOP + 8 项风险登记 + 验收清单）。
- **文档体系重写**：`README.md`（二创署名声明 + 完整门面）、`AGENTS.md`（后续 Agent 工作指南：文件地图/硬性规定速览/工作流程/同步 SOP/扩展规范/测试要求/红线）、`HARD_REQUIREMENTS.md`（8 条新硬性规定，取代旧 13 条）。
- **Phase 0**：旧工程备份 git tag `legacy-v0.3.0`（commit 2fd8b53，含全部历史，无需物理复制 11GB 资产）；上游 11 个文件 SHA-256 指纹登记 `tools/upstream-fingerprints.txt`（基线 RP-Hub 1.8.9 / b409ca6）；keystore 验证可用（luzzy 别名）。
- **Phase 1（进行中）**：Gradle 精简为单模块 :app（AGP 9 内置 Kotlin，移除 core/ 三模块与全部旧依赖）；RP-Hub 上游 19 文件复制至 `app/src/main/assets/rphub/`（含 presence-server 原样保留）；壳代码完成：`AssetExtractor`（filesDir 解压 + 版本标记幂等）、`WebViewSetup`（DOM storage/file 访问/缓存）、`LuzzyBridge`（剪贴板/Toast/版本信息 JSBridge）、`FileChooserHandler`（SAF 文件选择）、`DownloadHandler`（DownloadManager 导出）、`MainActivity`（单 Activity 宿主 + 返回键回退 + onActivityResult 转发）；扩展层骨架：`luzzy-bridge.js` / `luzzy-theme.css`（本地 Lora @font-face + 字体栈覆盖）/ `luzzy-ext.js`（桥接自检 + 关于页品牌注入）。
- **构建修错**：AGP 9 不再需要 org.jetbrains.kotlin.android 插件（移除后重跑构建中）。

### 决策
1. 上游基线锁定 RP-Hub 1.8.9（本地 rp-hub-reference 与官方一致，commit b409ca6）。
2. 壳加载路径 = filesDir 解压（localStorage 持久化），非 android_asset。
3. 更新检查（rphub-update-api）二创后禁用（patch 002），自建服务列 v1.1.0 候选。
4. presence-server 原样保留代码（用户要求不动后端），不部署。
5. 旧工程备份采用 git tag 方式（git 历史完整保留 11GB 资产，无需物理归档）。

### 遗留
- assembleDebug 构建结果待确认（AGP 9 插件修正后）。
- 待办：Patch 体系（001-005 品牌/禁用更新检查/vendor 本地化/扩展层挂载）、CDN 资源离线化（Vue/Tailwind/marked/DOMPurify/SortableJS/Lora）、文件桥实机验证、Phase 7 测试验收。

### 下一步
- 确认构建通过 → 生成登记 patch（001-005）→ CDN 离线化（Phase 2）→ 品牌化（Phase 3）。

---

## 2026-09-01 · 重建会话 2：Phase 1 壳骨架完成 + Phase 2 离线化 + Phase 3 品牌化

> 续重建会话 1（见上）。本会话完成 v1.0.0 重建的核心工程部分，`assembleDebug` 持续通过。

### 完成
- **Phase 1 壳工程（完成）**：Gradle 单模块化（删 core/ 三模块，libs.versions.toml 瘦身为 6 依赖）；AGP 9 内置 Kotlin 适配（移除 kotlin.android 插件、kotlinOptions → kotlin block，查证官方迁移文档）；壳代码 6 文件：AssetExtractor（filesDir 解压 + `.extracted_v1` 版本标记幂等）、WebViewSetup（DOM storage / file 访问 / UA 标注）、LuzzyBridge（剪贴板/Toast/版本/设备信息，R8 keep）、FileChooserHandler（SAF 导入，onActivityResult 转发）、DownloadHandler（DownloadManager 导出）、MainActivity（ComponentActivity 宿主 + 返回键回退）；Manifest 重写（去 LuzzyApp、加下载权限、configChanges 补 density/fontScale）。**产物：24.8MB debug APK 三件套（ABI 拆分）**。
- **Phase 2 离线化（完成）**：vendor/ 下载 7 件（vue 164KB / tailwind.js 398KB 运行时 JIT / marked / purify 3.0.6 / SortableJS / daisyUI 4.7.2 / localforage 1.10.0）；Lora 可变字体本地打包（Regular + Italic 两文件覆盖 400-700）；主页面 + character + novel 子页面 CDN **全扫描清零**（审计中发现子页面同样有 CDN 依赖，补做本地化）；`assets/css/local-fonts.css` 建立（本地 @font-face + 字体栈覆盖）；**决策**：CJK 分片字体（Ma Shan Zheng 100+ 分片）不做本地化，依赖安卓系统 Noto 回退（登记 patch 007 决策）。
- **Phase 3 品牌化（完成）**：title→LuzzyRP、入口 logo→LUZZY·RP、移除 rphub-update-api meta、vendor 引用、扩展层挂载（luzzy-theme.css + luzzy-bridge.js + luzzy-ext.js 尾部注入）；`tools/patches/README.md` 登记 7 个 patch（001-007）；`tools/apply-patches.ps1` 幂等重放脚本（8 步全 SKIP 验证 = 已应用状态）；`tools/sync-upstream.ps1` 同步脚本（dry-run 分支 + 参考克隆更新提示 + 二创专属文件备份恢复保护 + 指纹更新）。
- **扩展层三件套**：luzzy-bridge.js（存在性检测 + 降级）、luzzy-theme.css（字体变量覆盖）、luzzy-ext.js（桥接自检 + 关于页品牌注入）。
- **文档**：CHANGELOG v1.0.0 条目（含上游基线声明 + 历史区存档）；README/AGENTS.md/HARD_REQUIREMENTS.md 已在会话 1 完成；.gitignore 加 rp-hub-reference/ 排除；DESIGN.md 移除（摈弃项，git 历史可恢复）。

### 决策
1. AGP 9 内置 Kotlin：不装 kotlin.android 插件，`kotlin { compilerOptions { jvmTarget } }` 顶层块配置。
2. 入口 logo 品牌化改文字（LUZZY·RP）不动 CSS 动画结构（entry-logo-* 动画体系完整保留）。
3. patch 体系采用「脚本幂等查找替换」而非 git apply（上游文件无版本基线 diff 可比，脚本更抗冲突）。
4. novel 页 CJK 字体本地化放弃（100+ 分片不划算），系统 Noto 回退（patch 007 已登记决策）。
5. rp-hub-reference/ 加 .gitignore（独立仓库 + 11GB 资产不入库；同步 SOP 依赖其 fetch upstream）。

### 遗留
- `cdn.sta1n.cn/keys` 与 `qianxun1688.com` 推广外链未处理（保留待审，Phase 3 遗留）。
- LuzzyBridge 未接入扩展层实机调用验证（需真机）。
- FileChooserHandler/DownloadHandler 实机验证未做（需要真机 SAF）。
- 上游同步演练（sync-upstream.ps1 假发版模拟）未做。
- 构建警告：onActivityResult deprecated / databaseEnabled deprecated（Phase 4 桥接完善时处理）。

### 下一步
- 真机安装验证（壳加载 + 离线化 + 品牌化 + 桥接）→ CSP/混合内容审计 → 同步演练 → Phase 4 桥接完善 → Phase 7 测试验收。

---

## 2026-09-01 · 重建会话 3：真机验证通过（小米 25098PN5AC / Android 16）

> 用户连接真机（小米 pandora，arm64），完成 v1.0.0 壳工程首次实机验收。

### 完成
- **安装与启动**：`adb install` arm64 debug APK 成功；冷启动无崩溃；`AssetExtractor` 解压 rphub + ext 双目录成功。
- **界面完整加载**：uiautomator dump 确认 RP-Hub 全界面就位——侧边栏（聊天/用量统计/记忆系统/UI模板/角色卡管理/在线/高级/设置）、聊天页（未选择角色卡空态、发送图片 0/3、自动生图开关、单次系统指令、剧情分支：主线、切换模型、输入框+发送按钮）。
- **离线化生效**：vendor 本地加载（tailwind.js 运行时 JIT 正常，仅生产环境提示警告）；无任何 CDN 请求失败。
- **品牌化生效**：title LuzzyRP、入口 logo LUZZY·RP。
- **JSBridge 全链路打通**：扩展层自检输出 `[LuzzyRP] v1.0.0-debug (code 1) · upstream RP-Hub 1.8.9 · Xiaomi 25098PN5AC · Android 16 (API 36)`——JS → addJavascriptInterface → Kotlin → 返回 JS 全通。
- **数据持久化**：force-stop 重启后解压标记生效（无重复解压）、扩展层自检再次输出、localStorage 保留。
- **修复 2 个 bug**：① AssetExtractor 只解压 rphub 未解压 ext（扩展层静默失败，好在降级没白屏）→ 重构为多根目录解压；② WebViewSetup 引用已删的 TARGET_DIR 常量 → 改为硬编码 UA 版本。
- **新增**：JS console 转发到 Logcat（tag: JSConsole）——RP-Hub 是纯 JS 应用，调试与扩展层验证全靠 console 输出（FileChooserHandler.webChromeClient 内 onConsoleMessage）。

### 决策
1. AssetExtractor 改为 ROOTS 列表驱动（rphub + ext 双根），标记版本统一管理。
2. JS console 转发进 WebChromeClient（与文件选择桥同文件，避免多 WebChromeClient 冲突）。

### 遗留
- 文件桥实机验证（角色卡 PNG 导入导出 SAF 全流程）——需要用户操作或自动化脚本。
- 推广外链清理（cdn.sta1n.cn/keys、qianxun1688.com）。
- 上游同步演练。
- 构建警告（onActivityResult / databaseEnabled deprecated）Phase 4 处理。

### 下一步
- 文件桥 SAF 实机验证 → 推广外链清理 → 同步演练 → Phase 4 桥接完善 → Phase 7 测试验收 → 发布。

---

## 2026-09-01 · 重建会话 4：设计 SKILL 强制条款 + 主题功能启动

> 用户新任务：①AGENTS.md 增量更新硬性规定（设计相关必须启用 4 项 skill）；②设置页新增主题功能（经典=原版 + 新主题方案 + 新用户默认新主题）；③主题附属字体设置（系统内置字体=经典，默认字体=PuHuiTi-3/AlibabaSans，新用户默认）。

### 完成
- **AGENTS.md 增量更新**：硬性规定速览表新增第 9 条「设计 SKILL 强制条款」+ §2.1 详细展开（触发条件/4 项 SKILL 本地存档表/强制流程 5 步/豁免/与规定 4 的关系）。
- **HARD_REQUIREMENTS.md 同步**：新增规定 9（设计 SKILL 强制条款），含 4 项 SKILL 表 + 强制流程 + 守护落点。
- **4 项 SKILL 启用**：huashu-design（SKILL.md 579 行完整阅读：三方向硬门/反 AI slop/动效=物理学/Gate 文件协议）、awesome-design-md（73 份 DESIGN.md 范本库）、open-design（用户手动下载 zip 解压至 docs/skills/open-design，242MB；AGENTS.md 完整阅读：DESIGN.md 品牌契约/五维 critique/UI 动画哲学）、ui-ux-pro-max-skill（CLAUDE.md + SKILL.md + pro-rules.md 完整阅读：10 优先级规则/检索命令/Pre-Delivery Checklist）。
- **技术侦察**：RP-Hub 无内置主题机制（纯浅色，色值硬编码在 index.html tailwind.config）；设置页结构确认（用户设置/生图设置/高级设置区块）；字体资产盘点（PuHuiTi-3 woff2 每字重 1.4-5.3MB，AlibabaSans woff2 每字重 44-47KB）。

### 决策
1. 主题技术路线候选：方案 C（tailwind.config 色板改 var() 引用 + 扩展层 CSS 变量切换）最优雅，待讨论确认。
2. 字体打包策略：PuHuiTi 精选 3 字重（Regular/Medium/Bold ≈ 15.5MB）+ AlibabaSans 全 6 字重（≈ 0.3MB）。

### 遗留
- 主题方案 3 方向待用户选择（huashu-design 三方向硬门）。
- 字体设置 UI 设计待主题方向确定后展开。

### 下一步
- 主题 3 方向讨论 → 用户选定 → DESIGN.md 设计真源 → 实施。

---

## 2026-09-01 · 重建会话 5：主题系统技术底座 + 三方向板制作中

> 续会话 4。用户确认：新主题走 **Claude 风格** + **亮暗双模式**。三方向板（huashu-design 硬门）并行制作中。

### 完成
- **设计 spec 固化**：`docs/design/theme-spec.md`（三方向共同输入：产品/受众/硬约束/界面结构/三方向色板核心/动效纪律）。
- **三方向板并行启动**（3 个 subagent 独立工作）：
  - A · 暖纸书房：Claude 原味（米纸 #FAF9F5 + 烤橙 #D97757），卡片式分层布局
  - B · 极光暖夜：Claude 暖底 × 旧 Aurora 基因（AuroraPink/Violet），沉浸无边布局
  - C · 墨韵朱砂：Claude 暖底 × 东方文人（宣纸/朱砂/墨色），分栏杂志布局
- **技术侦察完成**：
  - RP-Hub 字体机制：`fontFamilies`（modern/serif/system）→ `data-app-font` 属性 → `--app-font-family` CSS 变量（styles.css:455-465）；`applyFontFamily`（app.js:653）；默认 `fontFamily: 'modern'`（app.js:628）；迁移 `fontFamilyVersion: 4`
  - **上游字体栈已预留 "Alibaba PuHuiTi 3.0" 位置**（styles.css:422）——本地 @font-face 定义后直接命中，无需改上游字体栈
  - 设置页结构：高级参数区块（grid 双列）含「界面字体」「对话字体大小」custom-select——主题选择 UI 落点确认
  - 存储机制：`setStoredValue('settings', settings)` 整体保存（app.js:1667）；扩展层用独立键 `luzzy_theme` / `luzzy_theme_mode`（与上游 `rp_hub_` 前缀零冲突）
- **字体资产打包**：PuHuiTi-3 三字重 woff2（55-Regular 5.0MB / 65-Medium 5.2MB / 85-Bold 5.3MB）+ AlibabaSans 全 6 字重（≈0.3MB）→ `assets/rphub/assets/fonts/`；`local-fonts.css` 追加 @font-face 定义
- **主题系统骨架**：`luzzy-theme.css`（classic 色板变量 = 原版色值 + luzzy 亮暗变量 TODO 待方向板填充）；`luzzy-ext.js`（applyTheme：data-theme/data-mode 驱动 + localStorage 独立键 + 新用户默认 luzzy）；`LuzzyBridge.setSystemBarStyle`（亮暗切换系统栏图标深浅）+ `luzzy-bridge.js` 封装
- **技术方案文档**：`docs/design/theme-tech-plan.md`（data-theme 驱动机制 / 设置项落点 / 字体打包 / 上游同步影响表：patch 008-011 登记规划）

### 决策
1. 主题机制 = `data-theme` + `data-mode` 双属性驱动（复刻上游 `data-app-font` 模式），classic = 原版色值默认变量。
2. 主题/字体设置存扩展层独立键（`luzzy_theme` 等），不侵入上游 settings 对象（硬性规定 3 扩展层隔离）。
3. 字体默认 = luzzy（PuHuiTi + AlibabaSans），通过扩展层 `data-app-font="luzzy"` 触发，上游 `normalizeFontFamily` 兜底为 modern 时扩展层重写。
4. 新用户默认新主题（luzzy）+ 默认字体（luzzy），老用户保留原设置。

### 遗留
- 三方向板完成待展示（A/B/C 各亮暗两张截图）。
- 方向板选定后：DESIGN.md 设计真源 → 色板变量填充 → patch 008-011 → 设置页 UI → 实机验证。

### 下一步
- 展示三方向板 → 用户选定 → direction-approved.md 落档 → DESIGN.md → 实施。

---

## 2026-09-01 · 重建会话 6：三方向板 A/C 完成，B 制作中

> 续会话 5。三个方向板 subagent 并行制作，A（暖纸书房）与 C（墨韵朱砂）已完成并验证，B（极光暖夜）制作中。

### 完成
- **方向 A · 暖纸书房**（subagent c7b78d52）：`docs/design/direction-a-warm-paper.html`（900 行单文件）+ 亮/暗截图。卡片式分层骨架（气泡悬浮卡 + 输入区悬浮工具栏 + 顶栏毛玻璃）；烤橙双档策略（#D97757 图形 accent / #B85C3E 文字按钮，过 4.5:1）；对比度亮 13.9:1/5.1:1、暗 13.5:1/6.0:1；零 JS 错误、无溢出、交互全通。
- **方向 C · 墨韵朱砂**（subagent ae7ce188）：`docs/design/theme-C-moyun-zhusha.html`（656 行单文件）+ 亮/暗截图。分栏杂志骨架（书页眉细线 + 消息左右分栏剧本排版）；宣纸 #F7F3EC + 墨色 #2B2620 + 朱砂 #C0392B 点睛；对比度亮 13.6:1/5.5:1、暗 14.7:1/7.9:1；零 JS 错误、交互全通。
- **汇总文档**：`docs/design/direction-summary.md`（三方向对比表 + 交付物清单 + 验证结果 + 选定后流程）。
- **清理**：删除重复的 theme-A-* 截图（保留 subagent 权威版本 direction-a-*）。

### 决策
1. 方向板截图命名规范：`direction-<字母>-<mode>.png`（subagent 产出为准）。
2. 方向板工具条（页面切换/亮暗按钮）是设计标注层，非 app 内容。

### 遗留
- 方向 B（极光暖夜）制作中。
- 用户选定方向后：direction-approved.md 落档 → DESIGN.md 填充 → luzzy-theme.css 变量 → patch 008-011 → 设置页 UI → 实机验证。

### 下一步
- B 完成 → 三方向一起展示给用户 → 用户选定 → 实施。

---

## 2026-09-01 · 重建会话 7：方向 A 选定 + 主题系统实施完成

> 续会话 6。用户从三方向板中选定「A · 暖纸书房」，主题系统全部实施完成（待真机验证）。

### 完成
- **方向选定落档**：`docs/design/direction-approved.md`（用户选择原话「A」+ 方向 A 完整设计要点）。
- **DESIGN.md 设计真源定稿**：方向 A「暖纸书房」完整契约（亮/暗色板 token 表、卡片式分层布局、动效令牌、可访问性、主题技术契约）。
- **luzzy-theme.css 主题变量填充**：classic = 原版色值；luzzy 亮色 = 米纸系 gray（#FAF9F5→#2A2826）+ 烤橙系 primary（#D97757 图形 / #B85C3E 按钮 / #A8543A 文字）；luzzy 暗色 = 深暖灰 gray 反转（#262624→#F5F1EA）+ 暖橙 primary；body 过渡 + reduced-motion 兜底。
- **patch 008-011 实施**：
  - 008：tailwind.config gray/primary 色板 → var() 引用（20 变量一一对应验证）
  - 009：core-utils.js fontFamilies 增加 luzzy 选项
  - 010：app.js 默认 fontFamily → luzzy + normalizeFontFamily 白名单加 luzzy
  - 011：设置页「界面主题」custom-select + 模式选择（v-if 条件显示）；app.js settings 加 theme/themeMode 字段（默认 luzzy/light）+ themeOptions/themeModeOptions + applyTheme/applyThemeMode watch（含 LuzzyBridge.setSystemBarStyle 联动）+ setup return 暴露 + 老用户迁移（savedSettings 无 theme → classic）
- **apply-patches.ps1 扩展**：008-011 幂等重放逻辑（13 项全 SKIP 验证通过）。
- **patches/README.md 登记**：008-011 补丁说明（目的/对应规定/预期冲突点）。
- **修复**：themeOptions 误插到 uiOptions 解构中间 → 移正（node --check 语法验证通过）。
- **验证**：20 个主题变量定义/引用一一对应；13 项 patch 状态全 OK；assembleDebug 通过（40.8MB）。

### 决策
1. 老用户迁移策略：savedSettings 无 theme 字段 → classic（保留原版），新用户默认 luzzy（settings 默认值）。
2. 主题切换联动系统栏：applyThemeMode watch 调 LuzzyBridge.setSystemBarStyle（亮=深图标/暗=浅图标）。
3. 暗色 gray 色阶反转映射（50 最深=画布 → 900 最浅=主文字），使上游全部 gray-* 工具类自动适配，无需改上游 HTML。

### 遗留
- 真机验证（用户在用手机，待空闲）：主题切换、亮暗切换、字体切换、系统栏联动、老用户迁移。
- 方向板 B/C 存档（备选，不实施）。

### 下一步
- 真机验证 → 修复问题 → CHANGELOG v1.0.0 更新 → 发布。

---

## 2026-09-01 · 重建会话 8：真机验证发现主题未生效（移交下一个 Agent）

> 续会话 7。用户连接真机（小米 25098PN5AC / Android 16），验证主题系统。**发现主题未生效——界面仍为原版灰色，非暖纸书房米纸色。已定位到最可能根因，移交下一个 Agent 修复。**

### 完成
- **安装与启动**：`adb install` 最新 APK（40.8MB，含主题系统）成功；冷启动无崩溃；AssetExtractor 解压正常；扩展层自检输出正常（`[LuzzyRP] v1.0.0-debug (code 1) · upstream RP-Hub 1.8.9`）。
- **部署文件验证**（run-as 检查解压后文件）：
  - `index.html` 含 `var(--tw-gray-50)`（patch 008 生效）✅
  - `index.html` 含 `luzzy-theme.css` 挂载（patch 005 生效）✅
  - `files/ext/` 三件套（luzzy-bridge.js / luzzy-ext.js / luzzy-theme.css）齐全 ✅
- **老用户迁移验证**：旧数据（savedSettings 无 theme 字段）→ 主题为 classic（原版灰色）——**迁移逻辑按设计工作** ✅
- **新用户路径验证**：卸载重装（等价全新用户）→ 截图采样仍为原版灰色（聊天区 #BCBDBE、顶栏 #7C7D7D、输入区 #FFFFFF）——**主题未生效** ❌

### 问题定位（关键）

**现象**：新用户默认 theme='luzzy'，但界面颜色仍是原版灰色（#BCBDBE 等），非暖纸书房米纸色（#FAF9F5）。

**已排除**：
1. 文件部署问题（patch 008 已生效，luzzy-theme.css 已挂载）
2. JS 语法错误（node --check 通过）
3. 老用户迁移逻辑（按设计工作）

**最可能根因（待验证）**：**Tailwind CDN（cdn.tailwindcss.com 运行时 JIT）不接受 `var(--tw-gray-50)` 作为 config 颜色值**。

- RP-Hub 用 Tailwind CDN 运行时 JIT：它扫描 DOM class → 按 tailwind.config 生成 CSS 规则注入 `<style>`。
- patch 008 把 config 的 gray/primary 色板从 hex 改为 `'var(--tw-gray-50)'` 字符串。
- Tailwind 的 config 颜色值校验：`'var(--tw-gray-50)'` 不是合法颜色格式（非 hex/rgb/hsl/命名色），**Tailwind 可能拒绝该值，导致 `.bg-gray-50` 等工具类不生成或生成失败** → 界面回落到无样式状态（或浏览器默认/上游 styles.css 兜底色）。
- 证据：截图颜色 #BCBDBE ≈ 原版 gray-200（#e5e7eb 的暗化版？）或 Tailwind 未生成规则时的兜底色；#7C7D7D 顶栏 ≈ 原版黑色渐隐层叠在白色上。

**备选根因（可能性低）**：
- `data-theme` / `data-mode` 属性未设置（watch 未执行）——但 luzzy-ext.js 的 applyTheme 也会设置，且无 JS 报错。
- luzzy-theme.css 的 `:root[data-theme="luzzy"][data-mode="light"]` 选择器未命中。

### 修复方向（下一个 Agent 参考）

**方案 1（推荐）· 放弃 Tailwind config var() 化，改用 CSS 覆盖层**：
- 回滚 patch 008（tailwind.config 恢复 hex 原值）。
- 在 luzzy-theme.css 中，用**高优先级 CSS 规则覆盖** Tailwind 生成的工具类：
  ```css
  :root[data-theme="luzzy"][data-mode="light"] .bg-gray-50 { background-color: #FAF9F5; }
  :root[data-theme="luzzy"][data-mode="light"] .text-gray-800 { color: #3D3A36; }
  ```
- 缺点：需要覆盖 RP-Hub 用到的全部 gray/primary 工具类组合（bg-/text-/border-/from-/to-/ring- 等），工作量大但可控。
- 优点：不依赖 Tailwind 对 var() 的支持，100% 可靠。

**方案 2 · 验证 Tailwind 是否支持 var() 颜色**：
- 在浏览器（Playwright）加载 index.html，检查生成的 `<style>` 里 `.bg-gray-50` 规则是否存在、值是什么。
- 若 Tailwind 支持 var()（生成 `background-color: var(--tw-gray-50)`），则问题在 data-theme 未设置，转查 watch 链路。
- 若 Tailwind 拒绝 var()（规则缺失），走方案 1。

**方案 3 · 换用 Tailwind 任意值语法**：
- config 色板改 `'rgb(var(--tw-gray-50) / <alpha-value>)'` 形式（Tailwind 官方支持的 CSS 变量颜色模式）——但需要变量存 RGB 三元组而非 hex，改动面大。

**验证方法**：
1. Playwright 加载 `file:///D:/.NekoTool/LuzzyRP/app/src/main/assets/rphub/index.html`，`page.evaluate` 检查：
   - `document.documentElement.dataset.theme` / `dataset.mode` 值
   - `getComputedStyle(document.querySelector('.bg-gray-50')).backgroundColor` 值
   - 生成的 `<style>` 中 `.bg-gray-50` 规则文本
2. 真机复测：卸载重装 → 截图采样聊天区背景应为 #FAF9F5（亮色）。

### 决策
1. 主题未生效问题**不阻塞其他工作**，但 v1.0.0 发布前必须修复。
2. 移交下一个 Agent 时，优先执行「修复方向」中的方案 2（验证根因）→ 按结果走方案 1 或 3。

### 遗留
- **P0：主题未生效**（根因待验证，见上）。
- 真机验证其余项（亮暗切换、字体切换、系统栏联动）待主题生效后补测。
- 文件桥 SAF 实机验证（角色卡 PNG 导入导出）。
- 推广外链清理（cdn.sta1n.cn/keys、qianxun1688.com）。
- 上游同步演练。
- 构建警告（onActivityResult / databaseEnabled deprecated）。

### 下一步
- 下一个 Agent：验证 Tailwind var() 支持性 → 修复主题 → 真机复测 → 补测亮暗/字体/系统栏 → CHANGELOG → 发布。

---

## 2026-09-01 · 重建会话 9：任务重置 + 三方向硬门 + 「暖幕手记 × Claude」主题实施完成

> 用户指令：完全重新开始主题任务（含移除旧「暖纸书房」设计）；更新 AGENTS.md 硬性规定 9；
> 启用 4 项设计 SKILL；重做主题（保留经典；新用户默认新主题）与字体设置（内置改「经典」，
> 默认 = PuHuiTi 3 + AlibabaSans）。本次会话全流程完成，模拟器验证通过。

### 完成
1. **规定 9 文档**：HARD_REQUIREMENTS「八条」→「九条」措辞同步（规定 9 上会话已写入，本次核对一致）。
2. **4 项设计 SKILL 完整阅读**：huashu-design SKILL.md + animation-pitfalls.md、open-design AGENTS.md
   + CLAUDE.md、ui-ux-pro-max CLAUDE.md + SKILL.md + pro-rules.md、awesome-design-md README.md。
3. **旧主题移除**：patch 008-011 全部撤销（index.html 色板恢复 hex、app.js/core-utils.js 与
   rp-hub-reference 逐字节一致、luzzy-theme.css 重置、apply-patches/README 移除登记）。提交 feaa52d2。
4. **三方向硬门**（huashu Fallback Phase 1-5）：spec-v2（≥500 字合同 + 五问）→ 共享骨架
   boards-v2/skeleton.html（固定 RP-Hub 结构、CSS 变量暴露全部主题面）→ 3 个并行 subagent 产出
   方向板（A 轮盘#19 Swiss Monochrome「锐白」/ B ElevenLabs 参照「午夜场」/ C Collins「暖幕手记」，
   各含亮暗双手机屏/40 格色板/字体样张/设置预览/动效令牌）→ AskUserQuestion → **用户选
   「选C，但进一步增强CLAUDE风格」**。提交 d978e299。
5. **设计真源**：direction-approved-v2.md（用户原话 + C×Claude 融合细则）+ DESIGN.md 全文重写
   （Claude token 体系：cream/coral/ink 亮暗色板、Lora×PuHuiTi 排印、动效令牌、Do/Don't、技术契约）。
6. **实施**（patch 008-011 v2 全部重写登记 + 幂等重放 13 项 SKIP 验证）：
   - 008v2 色板 var() 化；009v2 字体选项（经典系改名 + luzzy 新增）；010v2 默认字体 luzzy；
   - 011v2 设置页主题卡（主题+模式+字体附属）+ theme/themeMode 字段 + immediate watch + 老用户迁移
     （置于 if(savedSettings) 块内，规避 hasOwnProperty(undefined) TypeError）；
   - luzzy-theme.css：classic/亮/暗三套变量 + Lora 名字标签 + 暗色 bg-white 校准（!important，
     上游 glass 组合优先级更高，CDP 诊断后定案）；
   - 壳层：LuzzyBridge 状态栏恒白/导航栏随主题；windowBackground 暖化；debug 开 CDP。
7. **模拟器验证**（emulator-5554，pm clear 全新用户路径 + CDP 数据面）：
   - 新用户默认：theme=luzzy / mode=light / appFont=luzzy，body=#FAF9F5 精确命中；
   - 变量组：gray50=#FAF9F5、gray900=#141413、primary500=#CC785C、primary600=#A9583E 全对；
   - 暗色：IndexedDB 持久路径 reload 后 mode=dark、body=#181715、输入岛 rgba(37,35,32,.72)；
   - 经典回退：主题卡切换后 canvas=#f9fafb（上游原值）；字体栈 AlibabaSans+PuHuiTi 生效；
   - 截图存档：docs/design/verify-v2-final-{light,dark}.png。

### 决策
1. **三方向共享骨架**：huashu「三版布局互异」规则对主题任务修正——宿主 DOM 固定，差异轴收窄为
   视觉身份（色彩/材质/圆角/排印/动效），spec §5 明文声明。
2. **主题机制沿用 var() 方案**：jsdom 实证 Tailwind Play CDN 接受 var() 色值（推翻会话 8 的
   「CDN 拒绝 var()」假设）；会话 8 观察色 #BCBDBE/#7C7D7D = 黑渐隐叠 windowBackground 白
   （变量未定义→transparent），真因为 CSS 变量链路未生效而非 JIT 拒绝。
3. **暗色 bg-white 覆盖用 !important**：上游 glass/blur 组合有更高优先级声明（CDP 诊断
   matches=true 但 computed 不变），扩展层主题覆盖以此为合法取胜手段。
4. **系统栏**：状态栏恒白图标（顶栏深渐隐在亮暗两模式都可读），导航栏图标随主题明暗。
5. 验证手段沉淀：debug 壳开启 CDP（setWebContentsDebuggingEnabled），adb forward + ws 驱动
   完成填表/切主题/数据面断言——后续 WebView 壳验证的标准路径。

### 踩坑记录
- `adb shell input text` 对 WebView 输入框不生效（需 CDP evaluate + 原生 setter + input event）。
- 模拟器同时存在 `com.luzzymeow.luzzyrp`（旧 v0.1.1 Compose 残留）与 `.debug` 后缀包，
  launch 时曾启动错包造成长时间误判——旧包已卸载。
- `AssetExtractor.EXTRACT_VERSION` 不 bump 则 install -r 后 filesDir 不重新解压；
  本次用 `pm clear` 强制全新解压。**后续改 assets 必须卸载重装或 bump 版本号**。
- app.js 为 CRLF/LF 混合行尾，node 字符串替换需行定位而非整段匹配。
- PowerShell heredoc 经 Git Bash 传入会被截断——长 PS1 内容用 Write 工具整文件写入。

### 遗留
- 上游硬编码 indigo/blue/pink 工具类（用户设置页头部渐变、抽屉图标等）不在 gray/primary
  ramp 内，保持原样；是否扩展主题覆盖待用户决策（DESIGN.md Do/Don't 已留口）。
- 「荧光笔落笔」招牌动效（DESIGN.md roadmap）：需正则/markdown 管线配合，独立迭代。
- 真机回归（用户手机）：本会话仅模拟器验证；发布前按 §6.3 走真机矩阵。
- CHANGELOG 已更新 v1.0.0-rc2；版本号/versionCode 未动（发布流程待用户确认节奏）。

### 下一步
- 用户真机体验新主题 → 反馈微调（色板/字体/动效均可按 DESIGN.md token 快速调）。
- 决定是否扩展上游硬编码色的主题化（indigo/pink → coral 系）。
- v1.0.0 正式发布流程（assembleRelease + CHANGELOG 定稿 + GitHub Release）。

### 会话 9 追记：暗色模式修复（用户反馈「暗色有点难看，对比度没调好」）

**诊断（CDP 实测）**——用户直觉正确，且根因比对比度更深：
1. **机制缺陷（主因）**：v2 纯 `var()` 色板下，Tailwind JIT 无法给带透明度修饰符的工具类
   （`bg-gray-50/60`、`border-gray-100/80` 等）注入 alpha，**回退输出纯白**——暗色下输入框/
   设置输入框/分段滑块发白全是这个根因（jsdom 早期 B 场景已见端倪：alpha 变体丢失）。
2. **层次不足**：画布 #181715 与卡片 #252320 仅差 3.5% 亮度，界面糊成一片死黑。
3. **文字对比不达标**：text-gray-500 = #6E6B64 实测 3.95:1（<4.5）；text-gray-400 = #4A4842 仅 2:1。
4. **上游写死白**：styles.css `.segmented-switch__indicator { background:#fff }`。

**修复（patch 008 升 v3 + 暗色板重调）**：
- 色板改 **RGB 三元组** + config `rgb(var(--tw-*) / <alpha-value>)`（Tailwind 官方模式）——
  透明度变体由 JIT 自动注入 alpha，机制性消除白块回退；classic/亮/暗三套变量全部改三元组。
- 暗色板重调（保持 Claude 暖黑）：canvas #171614 / surface-soft #201E1B / card #2B2824（层次
  拉开）/ hairline #3E3A34（暗下可见）/ 图标 #6B675F（3.3:1）/ 弱文字 #8A867D（4.98:1）/ 次级
  #A5A198（6.8:1）/ 正文 #DED9CF（12:1）。
- segmented 白滑块暗色覆盖 + bg-white 校准值随新板同步（#2B2824 系）。
- DESIGN.md 暗色 token 表与技术契约同步；patches/README 008 条目更新 v3。

**复验（CDP 数据面，pm clear 全新路径）**：暗色画布 #171614、卡片 #2B2824、输入岛
rgba(43,40,36,.72)、透明度变体 rgba(23,22,20,·.8) 正常着色、**纯白残留 = 0**（全 DOM 扫描）、
次级文字对比 4.98:1；亮色不受影响（#FAF9F5 + alpha 变体 rgba(250,249,245,.8)）。
截图：docs/design/verify-v3-{light,dark}.png。

**经验**：var() 色板必须用三元组 + <alpha-value> 形式；纯 var() 会静默损坏全部透明度工具类
（不报错、仅回退白色，CDP 全 DOM 扫描才能抓到）。

### 会话 9 追记 2：真机验证通过（用户连接小米 25098PN5AC / Android 16）

- **部署**：EXTRACT_VERSION 1→2（旧包 filesDir 停留 rc1 解压产物，必须 bump 才会重新解压；
  IndexedDB 用户数据不受影响）→ arm64 APK `install -r` 保数据安装 → 启动后确认
  `.extracted_v2` + index.html md5 与新构建一致。
- **老用户路径**：savedSettings（rc1 保存过 theme='luzzy'/light/luzzy）被正确沿用，未触发
  classic 迁移；body 精确 #FAF9F5、变量三元组、字体栈 AlibabaSans+PuHuiTi 全部命中——
  对照 rc1 同一真机的 #BCBDBE，P0 问题确证修复。
- **亮/暗实测**：暗色 CDP 持久路径 reload 后 canvas #171614、输入岛暗面、**纯白残留 0**
  （全 DOM 扫描）；截图 verify-v3-phone-{light,dark}.png 存档；验证后已恢复用户原 light 设置。
- **真机 CDP 踩坑**：熄屏时 Page.captureScreenshot 挂起（/json visible:false）——先
  `input keyevent KEYCODE_WAKEUP + 82` 唤醒再截；CDP 长时间多客户端折腾后会僵死（HTTP 无响应），
  force-stop 重启 app 刷新 socket 即恢复；`await evaluate('location.reload()')` 会永久挂起
  （页面销毁丢响应），必须 fire-and-forget 或用 Page.reload（CDP 方法，连接保持）。

---

## 2026-09-01 · 会话 10：雾纸玻璃层 Frost-Paper（液态玻璃方向融合）

### 完成
- **设计 SKILL 强制条款重执行**：完整重读 4 项 SKILL 主文档（huashu-design SKILL / awesome-design-md README / open-design AGENTS+CLAUDE / ui-ux-pro-max CLAUDE+SKILL），跑 glassmorphism 风格检索（blur 10-20px + 半透 10-30% + 1px 亮边 + 对比度条件性）。
- **三方向硬门**：固化共享 spec（`docs/design/boards-v3/SPEC.md`）→ 3 个并行 subagent 各出一块方向板（HTML+Playwright 截图，亮暗双框渲染同一聊天场景）：A 雾纸 Frost-Paper（Windows Mica 派，玻璃仅固定 chrome）/ B 琥珀琉璃 Amber-Glass（暖 tint，AI 气泡玻璃化）/ C 晨露 Liquid-Clear（Apple Liquid Glass 派，高透+saturate，用户气泡玻璃化）→ 用户选定 **A**（`direction-approved-v3.md` 存档）。
- **实施**（全在 `luzzy-theme.css`，零新 patch）：chrome 半透白面枚举接管（`bg-white/50/60/70/90/95` → 亮 cream/暗暖纸 0.86；**故意不接管 /20 /40**——照片上白 chip 白字语义）；`backdrop-blur-xl` 24→16px；`.app-sidebar` 补 blur；模态面板 `.fixed.inset-0 > .bg-white` 雾纸化；气泡 `.msg-bubble-glass` 回归不透纸面+去 blur；上游 `!important` 白面成建制收编（`.input-island` 等 7 个选择器 + 抽屉遮罩去 slate）；`@supports` 实底降级。
- **真源与文档**：DESIGN.md 新增 Glass 章（配方表 + 枚举原则）；AGENTS.md §1.3 字体路径修正（实际 `rphub/assets/fonts/`）；CHANGELOG rc3 条目；EXTRACT_VERSION 2→4；`assembleDebug` 通过（40.8MB）。
- **验证（模拟器 CDP 取证）**：亮/暗 tint 全部命中（顶栏/输入岛/侧栏 rgba 逐项核对）；暗色输入岛 `!important` 夺回实证（白 0.9 → rgba(32,30,27,0.88)）；模态选择器探针命中；证照 `docs/design/verify-frost-{light,dark}.png`。

### 决策
1. **玻璃面积与强度构成三方向结构差**（不是换皮）：A chrome-only 高不透 / B 暖 tint 到 AI 气泡 / C 高透+saturate 到用户气泡——用户选 A（最稳）。
2. **bg-white/N 枚举而非通配**：通配 `[class*="bg-white/"]` 会打碎照片浮层白 chip（`/20` `/40` 白字语义），rc2 的通配写法是隐患，本轮已改。
3. **气泡去玻璃化**：方向 A 板定稿「气泡=纸感」，`.msg-bubble-glass` 不透 + 去 blur——与雾纸气质一致且降低 GPU 代价。
4. 模拟器 blur 不渲染定性为**设备/WebView 层问题**（Chromium 124，supports=true 但合成器跳过；设计以 alpha 兜底不劣化），真机复验后再定论。

### 遗留
- **真机 blur 复验**：用户下次连接手机时，确认新 WebView 上磨砂玻璃实际渲染（预期成立），并顺带过一遍聊天页/弹窗/抽屉的雾纸观感。
- rc3 证照为空聊天页（无会话数据），聊天气泡纸感 + 名牌/typing/Toast 收编效果待有会话数据的设备走查。

### 下一步
1. 真机复验雾纸层（上）。
2. AGENTS.md §9 待办顺延：SAF 文件桥实机、推广外链清理、上游同步演练、v1.0.0 正式发版。

### 工具坑（复用价值）
- Git Bash 会把 `/data/...` 参数改写为 `C:/Program Files/Git/data/...`——`adb push` 到设备路径必须 `MSYS_NO_PATHCONV=1`（本次 push 静默失败导致 md5 不一致的排障教训）。
- 模拟器 AVD 的系统 WebView 可能远旧于真机（本机 124 vs 真机 14x）：WebView 行为验证不能只信模拟器。


### 会话 10 追记：真机验证通过 + 模拟器诊断改判

- **装机**：小米 25098PN5AC（Android 16 / WebView **150.0.7871.47**）`install -r` 保数据；`.extracted_v4` 标记确认；用户原 light 设置沿用，老用户 tint/字体全部命中。
- **🔴 根因改判（重要）**：追记前文「模拟器旧版 WebView 不渲染 backdrop-filter」**结论错误**。真凶是上游 styles.css 移动端媒体查询的全局 kill-switch：`* { backdrop-filter: none !important }`（上游为性能主动关闭移动端磨砂——上游自己的 glass 类在手机上从未模糊过）。它同时解释模拟器与真机的全部怪象（inline 都失效、supports=true、视觉无模糊）。WebView 版本无关。
- **修复**：luzzy-theme.css 对 chrome 表面（.backdrop-blur-xl 持有者 / .app-sidebar / 模态面板）以 `:root[data-theme]` 前缀 + `!important` 更高特异性精准放行 blur(16px)；其余表面维持上游省电策略。真机 CDP 复测：顶栏/输入岛 `blur: blur(16px)` ✅。
- **视觉实证**：条幅探针垫入输入岛玻璃后方 → 系统级 `adb shell screencap` 截图 → 玻璃后条纹明显磨砂化（玻璃外锐利、玻璃内柔化），亮暗双证 `docs/design/verify-frost-phone-{light,dark}.png`；验证后已清理测试元素并恢复 light。
- **新坑**：CDP `Page.captureScreenshot` 在页面有激活 backdrop-filter 时永久挂起（合成读回互锁，两台设备均复现）——有 blur 的页面截图必须走 `adb shell screencap`。
- **小坑**：小米 Android 16 已限制 shell 注入按键（`input keyevent` 抛 SecurityException）——亮屏改用 `am start` 拉起 Activity 顺带唤醒（本次有效）+ `svc power stayon true`。

### 会话 10 追记 2：v1.0.0 正式版发布
- **发布**：versionCode 1→5（衔接 v0.3.0 的 4，避免覆盖安装降级拦截）· assembleRelease 通过（luzzy 签名 + R8 + ABI 三件套 17.1MB）· 模拟器发布包 smoke（首启公告→欢迎向导→新用户默认主题/字体全通过）· CHANGELOG/README 定稿 · 提交 5c7cf43c · **GitHub Release v1.0.0 已发布并附三件套 APK**（stable，非 prerelease）：https://github.com/LuzzyMeow/LuzzyRP/releases/tag/v1.0.0
- **遗留顺延**：手机上仍为 debug 包（release 与 debug 签名不同不能覆盖装，卸载 debug 会清用户数据——留给用户决定）；indigo/blue 硬编码点缀与「荧光笔落笔」动效在 v1.1.0 候选。

---

## 2026-09-02 · 会话 11：v1.1.0 增量更新（多模型商混用 + 思考卡雾纸玻璃 + 外观独立面板）【进行中】

### 开始
- 任务：①多模型商（provider）混用模型——设置页/聊天页以 `[商名]` 前缀区分同名模型、记忆双模式支持跨商嵌入/总结模型、含供应商管理器（用户选定方案）；②聊天页思考卡片液态玻璃（三方向硬门已过，用户选定**全卡雾纸玻璃**）；③主题/字体设置独立为侧边栏「外观」入口 + 模态弹层。
- 设计 SKILL 强制条款：4 项 SKILL 主文档已复核（huashu-design / awesome-design-md / open-design / ui-ux-pro-max）。
- 详细计划已落盘 `docs/PLAN-v1.1.0.md`。

### 会话 11 完成 · v1.1.0 发布

**完成**
1. **多模型商混用（patch 012）**：模型引用体系 `providerId::bareId`（裸 id=跟随激活商，老数据零迁移）+ `parseModelRef/formatModelRef/resolveModelRequest` 解析层；供应商管理器（`settings.apiProviders` 任意用户商 + 每商「检测」+ custom/custom2 一次性迁移）；跨商合并模型列表（`providerModels` 按商缓存 + 合并视图条目带 bareId/providerName，启动仅拉激活商、选择器惰性补拉、手动刷新拉全部）；请求点全接入（主聊天/识图/UI模板/记忆总结/记忆嵌入）；`usesThinkingCotTag` 取 bareId 防商名误判；向量检索按 (provider, model) 分桶现算查询向量、legacy 分片跟随激活商、新分片记 `embeddingProvider`；用量记录加 provider；iframe 同步载荷剥前缀；工坊激活商为用户商时映射 custom 槽位。涉及 app.js / ui-components.js / index.html，`node --check` 通过。
2. **思考卡片全卡雾纸玻璃**（luzzy-theme.css 追加，零 patch）：整卡 `gray-100/.86`+blur16 saturate1.15（暗 gray-200），发丝线，is-open 暖阴影，头部半透，卡内 bg-gray-50 半透化；`.is-live` 降级 .96+blur6+珊瑚描边；`@supports` 实底兜底；`:root[data-theme]`+`!important` 破移动端 kill-switch。
3. **外观独立面板（patch 013）**：AppSidebar「外观」按钮（emit open-appearance）+ 模态面板（主题/模式/字体/对话字号，绑定既有 settings 复用 watch+deep-watch+系统栏联动）+ 设置页主题卡改入口卡。
4. **发布链**：EXTRACT_VERSION 4→5 · versionCode 6 / versionName 1.1.0 · CHANGELOG/README（版本表+状态徽标）· patches README 登记 012/013、011 升 v3 说明 · DESIGN.md 新增「外观面板与模型商徽标」章 + Glass 章思考卡配方 · assembleRelease 通过（三件套 ~17.9MB）。

**验证（真机小米 25098PN5AC / Android 16，CDP + adb screencap）**
- 启动健康：mounted ✓ body #FAF9F5 ✓ luzzy/light ✓ 控制台仅已知 tailwind CDN 警告；
- 玻璃四态 computed 全命中：亮 is-open rgba(245,240,232,.88)+blur16 / 暗 is-open rgba(43,40,36,.88)+blur16 / 暗 is-live rgba(43,40,36,.96)+blur6+珊瑚边 rgba(217,119,87,.45)；条纹探针证照磨砂生效；
- 外观面板：雾纸弹窗自动接管（.88+blur16），四项设置回显用户现值；
- 供应商管理器：4 内置商「当前/检测/设为当前/配置点」渲染正确，「检测」端到端 ✓（patch fetch 返回 5 模型 →「✓ 5 个模型」）；
- 模型选择器：跨商合并列表 `[DeepSeek]`/`[STA1N API]` 珊瑚 chip 徽标并列展示，族谱计数按 bareId，槽位显示复合引用格式；
- 验证后现场已完全恢复（fetch unpatch、探针 key 清除、mode=light、探针节点移除）；证照 `docs/design/verify-v110-*.png` ×6。

**决策**
1. 复合引用分隔符取首个 `::`（商 id 无冒号，openrouter 模型 id 单 `:` 不冲突）；裸 id 语义=跟随激活商，避免全量迁移；
2. 老用户 custom/custom2 迁移后**原字段保留**（工坊协议仍读），靠 `apiProvidersMigrated` 标记一次性导入；
3. `userApiProviders` computed 返回原始响应式条目（拷贝会导致管理弹窗 v-model 不写回，开发中已修）；
4. 向量检索分桶而非批量重嵌入：切换嵌入商不触发全量 API 费用；桶间分数直接混排（跨嵌入空间比较属固有限制，词面 boost 部分补偿）；
5. 验证用临时 fetch patch + 探针 key 的方式取得端到端证据，结束后全部还原（用户无感）。

**遗留**
- 真机端到端对话级验证（用户实际配 ≥2 商 key 跨商对话）由用户日常使用覆盖；
- 向量分桶在多商混合场景的检索质量待实际语料评估；
- AGENTS.md §9 待办顺延：SAF 文件桥实机、推广外链清理、上游同步演练、indigo/blue 主题化、荧光笔动效（v1.2.0 候选）。

**下一步**
1. 用户真机体验反馈微调；
2. GitHub Release v1.1.0（本会话收尾发布）；
3. 上游同步演练（sync-upstream.ps1 假发版模拟）。

### 会话 11 收尾：GitHub Release v1.1.0 已发布
- 提交 8cad276c 推送 origin/main · Release（stable，附三件套 APK ≈17.9MB×3）：https://github.com/LuzzyMeow/LuzzyRP/releases/tag/v1.1.0
- 五维 critique 已过：方向（三方向硬门用户选定全卡雾纸）/ 品牌（全 token 化零新色）/ 层级（徽标 chip 次要于模型 id）/ 动效（200ms ease-out + reduced-motion 兜底）/ 工程（语法校验 + 真机四态 + patch 全登记零裸改）。

---

## 会话 12 · v1.2.0 增量更新（2026-09-02 开始）

- 任务：①液态玻璃补全（用户反馈思考卡/对话气泡玻璃不完整，三方向硬门用户选定**统一雾纸**）；②「外观」改侧栏独立页（设置置底、外观在上、全应用唯一入口）+ 新增「关于」独立页（应用内 CHANGELOG）；③供应商管理器大扩展：三协议（OpenAI/Anthropic/Gemini）自定义商、二级编辑弹窗（模型增删改：id/显示id/上下文长度/最大输出长度/输入模态/模型类型/自定义请求体+供应商级请求体）、五组模型 id 热检测预设、编辑商 id 引用重映射、热更新模型列表；④自定义生图模型接入生图流（openai 协议 image 模型）；⑤版本 v1.2.0 / versionCode 7。
- 设计 SKILL 强制条款：4 项 SKILL 主文档已复读。用户疑问答复定案：**不设「最大输入长度」字段**（上下文长度=输入+输出总预算，服务端 tokenizer 硬计数，客户端按上下文−最大输出推导）。长度字段深度=注入+展示（用户选定）。
- 三方向硬门与选择记录落盘 `docs/design/direction-approved-v120.md`；详细计划落盘 `docs/PLAN-v1.2.0.md`。

### 会话 12 完成 · v1.2.0 开发与模拟器全量走查

**完成**
1. **统一雾纸玻璃补全**（luzzy-theme.css，零 patch）：气泡/typing/思考卡 0.74+blur18、思考卡 is-open 0.80、is-live 0.94+blur6+珊瑚描边、流式 `:has(.cot-ui.is-live)` 行加厚 0.88+blur8、名字 chip 0.82、操作工具条收编 0.6+blur14；`--luzzy-glass-alpha/--luzzy-glass-blur` 单点调参；@supports 降级扩展；v1.0.0 的气泡强制实底规则作废。亮暗 computed 四值实测命中（模拟器 CDP 探针：亮 rgba(245,240,232,.74)+blur18/暗 rgba(43,40,36,.74)）。
2. **patch 014**：侧栏底部簇重排（外观→关于→设置置底，均 selectView+itemClass）；外观独立页（预览条+四控件，弹窗/设置页入口/重复字号下拉全删，全应用唯一入口）；关于页（logo/版本/上游基线/署名/GitHub/CHANGELOG 渲染）；`ext/luzzy-changelog.js` 生成链（tools/gen-changelog.mjs）；app.js 删 showAppearancePanel、增关于页惰性渲染 watch。
3. **patch 015**：apiProviders 条目扩展（protocol/models/extraBody，normalize 白名单保全）；parseLengthToken/formatLengthToken；供应商编辑器二级弹窗（z-[60]，五组热检测预设长词优先只填空字段+撤销，模型增删改/输入模态多选/类型单选/键值行懒编辑）；id 重映射（槽位前缀+key+缓存键+激活商）；fetchModels/checkApiStatus 三协议分型；requestChatCompletion 三协议适配（anthropic Messages/gemini GenerateContent，system 抽出、图片 base64、thinking/thought→reasoning、thinkingBudget 映射）；四个裸 fetch 点接入；工坊 remap 仅 openai；max_tokens 注入+选择器 meta chip；自定义生图（luzzy-image:// 伪 URL 分流+startCustomImageTask+生图设置模型来源）。
4. **版本**：versionCode 7 / versionName 1.2.0；EXTRACT_VERSION 5→6；patches README 登记 014/015（含 013 取代交叉引用与 015 实施中修正）；node --check 全量。

**验证（模拟器 LuzzyRP_Test / Android 15 / WebView）**
- 外观独立页：标题/五组控件/预览条渲染，设置页确认无双入口（入口卡改为跳转）；
- 关于页：v1.2.0-debug 版本、上游 1.8.9（桥读取）、CHANGELOG 14K 渲染（v1.1.0/v1.0.0 章节在列）、GitHub 按钮；
- 侧栏顺序：…高级组→外观→关于→设置 ✓ 激活态 ✓；
- 编辑器端到端：添加→编辑器（三协议按钮/模型区/请求体区/保存）；热检测 GLM-5.3-FLASH→自动填五项+轻提示；长词优先 DEEPSEEK-V4-FLASH-VISION-EXP→DeepSeek-V4-Flash-Vision-Exp；anthropic 商保存→管理器卡片（violet 徽标/编辑/检测/设为当前/key 掩码）；Key 编辑即存；UI 删除→确认→卡片消失（删除路径回归）；编辑已有商 id 冲突误报已修（__source 排除自身）；
- 三协议解析（罐装 SSE 探针）：anthropic text_delta→content/thinking_delta→reasoning/usage；gemini part.thought→reasoning/usage；请求体结构（anthropic max_tokens+system+thinking budget；gemini systemInstruction+generationConfig.thinkingConfig）；system 启发式（首条 user 字符串且多消息；单消息保留正文）；max_tokens 注入（有 meta 注入/无 meta 不发）+extraBody 合并（模型级 reasoning_effort:max）；
- 玻璃：亮暗 computed 全命中；证照 verify-v120-appearance.png / verify-v120-about.png；
- 实施中修复四项：新增供应商占位条目未入列（致命，已修）、id 冲突误报、SSE 兜底解析缺失、system 启发式吞单消息正文。

**决策**
1. 玻璃方向=统一雾纸（三方向硬门，direction-approved-v120.md）；长度字段=注入+展示；不设「最大输入长度」字段（上下文=输入+输出总预算，服务端 tokenizer 硬计数）；
2. 侧栏底部簇顺序 外观→关于→设置（设置置底满足用户要求，关于紧邻设置；用户可一句话调整）；
3. 残留 body transition 自定义元素（6526/14390 字节）为 v1.1.0 起 WebView 解析固有现象（HEAD 构建同样存在，不影响功能）——排查时曾被误判为回归，纠正记录在案；
4. EXTRACT_VERSION 排查教训：v1.1.0 验证手法（改资产必须 bump）在本次反复重建中再次生效，凡 filesDir 现象先查 .extracted_vN 标记。

**遗留**
- 真机（小米）玻璃四态 screencap + 流式性能 + 核心回归：设备当前未连接 USB，发布后接入即补（玻璃管线与 v1.1.0 真机已验证链路同族，computed 级证据已备）；
- anthropic/gemini 真实 key 端到端对话（罐装 SSE 已覆盖解析路径）；
- 「原生思考 250字」行的 z 层与头部名字叠压（用户截图细节，顺延）；
- AGENTS.md §9 待办顺延：SAF 文件桥、推广外链清理、上游同步演练、indigo/blue 主题化、荧光笔动效。

**下一步**
1. assembleRelease → push → GitHub Release v1.2.0（附 APK）；
2. 用户真机升级体验反馈（外观页/关于页/三协议供应商/玻璃观感）；
3. v1.3.0 候选：Gemini/Anthropic 图像模型接生图流、视频输入管线、每模型温度覆盖。

### 会话 12 收尾：GitHub Release v1.2.0 已发布
- 提交 b3a15e65 推送 origin/main · Release（stable，附三件套 APK ≈17.95MB×3）：https://github.com/LuzzyMeow/LuzzyRP/releases/tag/v1.2.0
- 五维 critique 已过：方向（三方向硬门用户选定统一雾纸）/ 品牌（全 token 化零新色，violet 徽标为功能区分色）/ 层级（meta chip 次要于商徽标与模型 id）/ 动效（200ms ease-out 既有令牌，无新增动效，reduced-motion 兜底）/ 工程（node --check 全量 + computed 四值证据 + patch 全登记零裸改 + pro-rules：触控目标 ≥44px 的编辑器行按钮、暗色正文对比 ≥7:1、无 hover-only 交互）
- 遗留移交：真机四态走查（设备未连接，用户侧载 release APK 或接入 USB 后由下一会话补）

### 会话 12 补遗 · 全面自检轮（用户指令：完全检查全部流程/改动）

用户要求对本次任务做完整复查。逐文件 diff 级审查 + 真实调用链回归，**确认并修复 9 处 bug + 1 处壳工程缺失**：

| # | 严重度 | 问题 | 修复 |
|---|--------|------|------|
| 1 | ★致命★ | 三协议适配器收到的 url 是调用方 buildApiEndpoint 产物（/v1/chat/completions）——anthropic POST 到错误端点、gemini 拼出损坏 URL（罐装测试传裸 base 未暴露） | 分发器剥 OpenAI 路径得裸 base，CDP 按真实调用链回归：anthroUrl=https://api.anthropic.com ✓ geminiUrl=…/v1beta/models/…:streamGenerateContent ✓ |
| 2 | 高 | 自定义生图 reroll 崩溃：nextImageUrl.href 对字符串 URL 取 undefined | 两分支统一产出字符串，调用点去 .href |
| 3 | 高 | 自定义生图 prompt 恒为字面 "$1"：encodeURIComponent 杀死了正则替换占位符 | prompt 原样进替换串；parse 改子串提取+容错解码 |
| 4 | 高 | 编辑器保存的手动模型不进 providerModels 缓存 → 无 Key 的商手动模型永不进选择器 | 保存时合并入缓存（去重） |
| 5 | 中 | fetchModelsForProvider 的 manual 条目 {id,manual} 丢失 meta → 选择器 meta chip 不显示 | 展开完整条目 |
| 6 | 中 | 热检测预设逐字输入锁死短标签（glm-5.3 → glm-5.3-flash 时 label 停在 GLM-5.3） | __presetLabel 追踪自动填充值，长词预设可覆盖；UI 回归：两步输入保存后 label=GLM-5.3-Flash ✓ |
| 7 | 低 | 预设「撤销」恒作用于最后一行 | providerEditorPresetModel 追踪触发行 |
| 8 | 低 | 预设填充 extraBody 后输入框回显为空 | 填充时同步 extraBodyText（回归：回显 {"reasoning_effort":"max"} ✓） |
| 9 | 中 | anthropic thinking 预算可能 ≥ max_tokens（API 400）；连续同角色消息两家 API 均拒绝 | anthropicThinkingConfig 守卫（<2048 不启用）；toAnthropic/toGemini 相邻同角色合并（回归：u1+u2 合并为双 text part ✓） |
| 10 | 中（壳） | WebView 无 onCreateWindow/setSupportMultipleWindows → window.open no-op，关于页 GitHub 按钮无效（v1.0.0 起 _blank 外链同病） | LuzzyBridge.openUrl（ACTION_VIEW）+ luzzy-bridge.js 封装降级 + openGitHubRepo 接入 |

**方法教训**：罐装测试必须走「真实调用链传参形态」（本次直传裸 base 掩盖了 #1）；EXTRACT_VERSION 反复坑再现（stash 验证轮装回旧资产干扰判断半小时）。

**现场**：fetch 拦截已还原；全部测试供应商已删（UI 删除路径回归）；EXTRACT_VERSION 6→7。
- Release v1.2.0 资产已用自检轮修复版重传（clobber，12:49 构建三件套）。

### 会话 12 补遗 · v1.2.0 真机走查（小米 25098PN5AC / Android 16 / df97f3c4）

用户接入真机，完整走查通过。安装自检轮修复版 debug 包（数据保留，EXTRACT_VERSION 7 自动重解压，CDP 全程在线）。

**玻璃四态 + 流式（真实立绘会话，5 气泡/2 思考卡/5 工具条）**
- 亮色 computed 全命中：气泡/思考卡 `rgba(245,240,232,.74)` + `blur(18px) saturate(1.2)`；工具条 `rgba(250,249,245,.6)` + `blur(14px)`；
- **流式加厚**：临时 `is-live` 类验证 `:has` 规则真机生效——气泡加厚 `0.88` + `blur(8px)`，思考卡 `0.94` + 珊瑚描边 `rgba(204,120,92,.45)`；
- 暗色 computed 全命中：`rgba(43,40,36,.74)` + blur18；工具条 `rgba(32,30,27,.6)`；
- **视觉证照**（立绘透色肉眼确认，文字对比清晰）：`docs/design/verify-v120-phone-chat-{light,live,dark}.png`。

**独立页**
- 外观独立页：标题/预览条/五组控件渲染完整（`verify-v120-phone-appearance.png`）；
- 关于页：v1.2.0-debug 版本、CHANGELOG 16K 渲染、GitHub 入口（`verify-v120-phone-about.png`）。

**核心回归**
- 用户真实会话渲染完好（5 气泡/名字标签/操作工具条/立绘背景）；思考卡头部点击展开正常（收起为上游 toggle 既有行为，与本次改动无关）；mode 恢复 light、无测试类残留、用户数据零污染。

**遗留（顺延）**
- 真实 API 流式生成的发热/帧率观察（消耗用户配额，未主动触发；CSS 层已由 is-live 模拟验证）；
- 「原生思考 N字」行 z 层与头部名字叠压细节（上游交互，顺延）。

**结论**：v1.2.0 真机走查通过，发布状态维持。

### 会话 13 · 记忆链路与主动工具排查（2026-09-02）

**任务**：用户反馈「对话时没见到记忆工具调用卡片；真实上下文请求里没有记忆分片」（向量记忆模式、确认有分片、嵌入模型正常）。排查 + 模拟器端到端复现。

**方法**：静态定位（app.js/data-services.js 记忆链路）+ 模拟器罐装复现（播种测试角色/分片，CDP fetch 拦截罐装嵌入向量 [1,0,…8 维] 与 SSE 回复，抓 chat 请求体验证）。

**结论：记忆链路本身全部正常，无 v1.2.0 回归**
1. 自动提取→嵌入（resolveModelRequest 裸引用/复合引用）→int8 量化存储→按楼分桶检索→打分→注入，全链路打通；请求体实证含 `<role_memory_vector_recall>` + `<memory_fragment similarity="103.0%">` 分片；
2. 「没工具卡片」：主动工具（关键词检索 tool_grep / 联网 tool_web）**默认 enabled:false**（工具页手动启用）；且是提示词协议——模型必须先输出 `<reason:…>` 行再输出 `<tool_grep_add:…>` 标签（缺 reason 行不进捕获模式，本次罐装实测验证）；卡片检索的是**对话原文**（searchDialogueByKeywordForTool），不是记忆分片——RP-Hub 记忆无工具调用机制，靠向量召回自动注入；
3. 「看不到分片」四个解释（按可能性）：
   a. **查看器标注缺陷**（上游 wart，可修）：召回块 role=user 无 `_preventContextMerge`，深度 4 注入点紧邻前一条用户消息时被 `postprocessContextMessages`（data-services.js:703 mergeConsecutiveRoleMessages）合并 → `buildContextViewerState.isMemory` 的 `startsWith('<role_memory_vector_recall>')` 失效 → 显示为普通 USER 楼层（分片其实在请求里，用户没认出 `<memory_fragment` 原文）；
   b. **保留窗口**：`vectorKeepFloors`（默认 50）楼内的轮次不重复注入（防重复，设计使然）——新/短会话所有分片都是「近期」，全部被排除 → 什么都不注入；
   c. **阈值 0.45 硬编码**（MEMORY_VECTOR_SIMILARITY_THRESHOLD=45），低于即静默丢弃；
   d. **静默失败**：分桶检索按 (embeddingProvider, embeddingModel) 现算查询向量，桶请求失败仅 console.warn（`向量分桶检索失败`），用户不可见——分片记录的嵌入商/模型与当前配置对不上时整桶跳过（patch 012 跨商分桶的 UX 空缺）。

**可修项（候选 patch 016，待用户点头）**：① `injectContextMessages` 召回块加 `_preventContextMerge: true`（一处，恢复查看器「角色记忆（向量召回）」标注）；② 检索失败外化为 toast；③ 查看器记忆判定 startsWith→includes（与 ① 二选一即可）。

**给用户的自查清单**：确认记忆开关+向量模式+嵌入模型已选；会话超过保留窗口（默认 50 楼）再观察；测试消息与分片内容高度相似；工具页启用「关键词检索」才有卡片（注意它查对话原文）。

**现场**：模拟器测试数据已清（RPHubDB deleteDatabase，应用恢复首启态）；fetch 拦截随 force-stop 清除；仓库零改动（纯排查）。

### 会话 14 开始 · v1.2.1（2026-09-02）

任务：① 召回块 `_preventContextMerge`（会话 13 结论落地）；② 记忆内容管理器（角色选择器查看指定角色的分片/总结，查看/编辑/删除/启停/清空）；③ 开屏加载动画与设置页两处蓝色收编品牌色（仅 luzzy 主题）；④ 上游标记体系（新硬性规定 10 + verify-markers.ps1 + 存量补全）。计划已批准，展开落盘 `docs/PLAN-v1.2.1.md`。

### 会话 14 完成 · v1.2.1 实施与双端验证（2026-09-02）

**完成（按批准计划 docs/PLAN-v1.2.1.md）**
1. **patch 016**（data-services.js）：召回块 `_preventContextMerge: true`——模拟器罐装复现：
   上下文查看器恢复「角色记忆（向量召回）·已注入 1 个向量分片」紫色标注与分片高亮
   （`docs/design/verify-v121-context-label.png`）。
2. **patch 017**（index.html + app.js）：记忆内容管理器——角色/分支选择器、分片列表
  （轮次/[商名]嵌入模型徽标/参与召回开关/展开预览/编辑/删除）、总结列表（轮次标签/编辑/删除/
   重试仅当前角色）、清空此角色记忆。模拟器 CRUD 全链路验证：跨角色切换（scoped 直写）、
   编辑强制重嵌成功才落盘（罐装嵌入 ×1、qLen 12/dims 8）、启停持久化、删除确认。
3. **品牌色收编**（luzzy-theme.css 零 patch）：splash 7 处 + 设置页两横幅 → 品牌 token；
   验证：luzzy 亮色 splash/横幅暖赭（截图 verify-v121-splash-luzzy-light.png、
   verify-v121-settings-light.png、verify-v121-settings-advanced-light.png）；
   **classic 对照全部回上游原蓝**（verify-v121-settings-classic-banner.png、
   verify-v121-splash-classic.png——CDP 动画重放法）。
4. **patch 018**（index.html head + ext/luzzy-ext.js）：开屏主题防闪蓝——head 内联
   localStorage 主题快照同步设 data-theme + luffy-theme.css 移入 head + 扩展层
   MutationObserver 维护快照。冷启动首帧即品牌色（无快照默认 luzzy+light）。
5. **标记体系（硬性规定 10）**：HARD_REQUIREMENTS 新增第 10 条（CHANGELOG 已声明）；
   AGENTS.md §1.5/§2/§4.1/§4.2/§4.3 同步；存量补全 001-012 显式标记 14 处；
   `tools/patches/entities/` 实体 diff（上游 1.8.9 基线→当前态逐文件，8 个文件，
   反向 apply 全 PASS）；apply-patches.ps1 加实体重放段（指纹判定）；
   `tools/verify-markers.ps1` 校验门 **39 PASS / 0 FAIL**（含 R1/R2 敏感文件指纹一致性）。

**实施中发现的两个 Vue 模板坑（均已修复并登记 patch 017）**
- 管理卡紧跟 classic 卡的 `v-else-if`：production 编译丢弃中间注释后两元素直接相邻，
  被编译器并入条件链（enabled=false 整卡消失）→ **卡片移到链区域之外**（记忆页首卡）。
- 编辑弹窗 transition 插在「视图区中间」不渲染 → **移到文档尾部与供应商编辑器弹窗同级**
  （供应商弹窗旁，位置已验证工作）。
- 连带踩坑记录：改 assets 未 bump EXTRACT_VERSION 导致 filesDir 旧资产反复误判
  （本次 7→8→9→10→11→12 五连 bump 的根源）；真机 exec-out 管道污染 PNG（改设备侧
  screencap+pull）；「保存」按钮全局匹配点错欢迎弹窗按钮（改 modal 内精确匹配）。

**真机走查（小米 df97f3c4 / 1.2.1-debug，只读不碰用户数据）**
- 安装成功，EXTRACT 12 重解压，用户数据完好（李書원会话/记忆 8 分片 4 轮）；
- 记忆内容管理器：当前角色分片 8 条与记忆面板一致、嵌入徽标 `[STA1N API] gemini-embedding-2`
  正确、行操作齐全（verify-v121-phone-manager.png）；
- 关于页 v1.2.1-debug + 应用内 CHANGELOG 渲染（verify-v121-phone-about.png）；
- 主题 luzzy 暖色（用户当前设置）。

**遗留/顺延**
- 上游「自动获取模型/流式输出」等 toggle 仍为蓝色（peer-checked:bg-blue-*，属待办
  「indigo/blue 主题化」范围，本次两处横幅+splash 范围外）；
- classic 总结记忆在管理器中的显示依赖提取管线生成的完整结构（手工种子对象被
  prepare 过滤为空列表——真实数据无此问题，罐装提取验证可后续补）；
- 稳定版 APK 构建与 GitHub Release：待用户指示（当前仅 debug 验证包）。

**现场**：模拟器（LuzzyRP_Test）测试数据已 pm clear+重播种验证用，无用户数据；
真机仅安装+只读走查；仓库改动全部登记（016/017/018 + 标记补全 + entities）。

---

### ⚠ 会话 14 移交补充 · 悬浮面板回归与修复中断（2026-09-03，移交下一 Agent）

> **本节为正式移交记录。** 用户叫停修复，要求完整记录现状后移交。上一节（会话 14 完成）中
> 「真机走查通过」的结论**作废**——那轮验证使用的是坏结构构建，且我漏看了截图里的明显异常。

#### 移交时仓库状态

- 分支 main，**全部 v1.2.1 改动未提交**（含代码/文档/entities）；提交前请先读「已知未解问题」。
- versionCode 8 / versionName 1.2.1（**未发布**，无 Release 无 tag）；EXTRACT_VERSION = **13**。
- 真机（小米 df97f3c4）安装的 1.2.1-debug = EXTRACT 13 构建；模拟器（LuzzyRP_Test）同。
- verify-markers.ps1：**39 PASS / 0 FAIL**；entities 8 个全部反向 apply PASS
  （生成时已加 `--ignore-cr-at-eol`，apply-patches.ps1 实体段已加 `--ignore-whitespace`
  ——**行尾坑**：checkout 产物 CRLF 与 rp-hub-reference LF 混用会让实体 diff 膨胀成全文件 diff）。

#### 已完成且验证过的内容（坏结构构建上验证，逻辑不受结构影响的部分仍可信）

1. patch 016（data-services.js）召回块防合并：罐装场景下上下文查看器恢复
   「角色记忆（向量召回）· 已注入 N 个向量分片」标注（verify-v121-context-label.png）。
2. patch 017（app.js）记忆内容管理器数据层：跨角色切换/编辑强制重嵌成功才落盘/启停/删除，
   罐装验证全部通过（IndexedDB 持久化断言过）。
3. patch 018（index.html head + ext/luzzy-ext.js）开屏防闪蓝：模拟器验证冷启动首帧即品牌色。
4. 品牌色收编（luffy-theme.css）：luzzy/classic 对照截图齐备。
5. 标记体系：硬性规定 10 + verify-markers + entities + 存量补全。

#### 用户发现的回归 #1（已修复，待真机复验）：记忆面板悬浮于所有页面底部

- **现象**（真机截图 handoff 证据：verify-v121-phone-home/manager/about.png，EXTRACT 12 构建）：
  「记忆引擎设置 + 向量记忆检索」面板出现在聊天页/记忆页/关于页底部；v1.2.0 基线
  （verify-v120-phone-chat-light/about.png）底部干净。
- **根因**：会话 14 中把管理卡「物理搬移」到记忆页首位的脚本，把 memory 视图的
  wrapper/视图闭合 `</div>` 一起搬走 → 引擎设置卡/向量卡/classic 卡脱离 `v-if="currentView==='memory'"`
  成为顶层常驻渲染（祖先链直连主布局容器，CDP 实测）。
- **修复动作**：index.html `git checkout HEAD` 还原 v1.2.0 基线 → 重放：
  ① 管理卡块+弹窗块（从坏文件按 div 平衡切割提取，卡片插回 classic 卡之后并保留
  `v-if="true"` 断链；弹窗插 015 供应商编辑器注释前）② 001-012 标记补全重跑
  ③ patch 018 head 注入重做。EXTRACT_VERSION 12→13。
  - ⚠ **v-if="true" 断链本身是有效的**（bump 9 后曾验证 enabled=false 卡片正常渲染）；
    当时的「物理搬移」是被链理论带偏的多余动作，且搬移脚本有缺陷。**不要再次搬移。**
- **修复后模拟器复验（EXTRACT 13）**：聊天页底部干净 ✓、关于页无引擎条 ✓、
  引擎设置卡回到 memory 视图内（祖先链含 management-view）✓、管理卡在位 ✓。
- **EXTRACT 坑再次发作**：修复 index.html 后未 bump EXTRACT_VERSION（12=12），
  install -r 后 filesDir 仍跑坏结构旧资产，造成「修了没生效」假象浪费一轮排查
  ——**改 assets 必 bump EXTRACT_VERSION，本案第五次踩中**。

#### 已知未解问题 #2（用户报告，移交后第一优先）：EXTRACT 13 构建的布局异常

- **现象（用户 2026-09-03 口头报告，真机 EXTRACT 13 构建）**：
  ① 「屏幕上方遗漏了字段」（顶部内容缺失/被裁切）；
  ② 「下方超出了屏幕边缘」（底部内容溢出屏幕）。
  设备随即断开，未能截图与定位视图；模拟器同构建未复现观察（未逐页核对）。
- **首要怀疑线索（强烈建议先查这个）**：重建 index.html 的**正则 div 计数**显示
  「World Info 视图之后区域累计 -2」（工具输出 `AFTER_MEMORY_BALANCE: -2 NEG: true`），
  当时误判为「上游文件本就依赖浏览器解析容错」而放过。**正则计数有盲区**
  （自闭合、非 div 标签、行内顺序），该 -2 很可能对应真实缺失的 2 个 `</div>`，
  顶部裁切/底部溢出与容器层级错误高度吻合。
- **建议诊断路径**：
  1. 用真实 HTML 解析器对比 `rp-hub-reference/index.html`（v1.2.0 基线）与当前
     `app/src/main/assets/rphub/index.html` 的 DOM 树：iframe srcdoc 注入后
     逐层 diff（重点：各 `.management-view` 的父级与层级、body 直接子级清单）；
  2. 从「Classic Memory View」注释起到文件尾，逐视图用 CDP 检查
     `document.querySelector(...).parentElement` 链与预期不符处；
  3. 修复原则：**只动 patch 017 插入的块与它带来的闭合**，禁止再次搬移上游区块；
  4. 修复后 EXTRACT_VERSION +1 → 双端重装 → 逐页截图核对
     （聊天/关于/记忆/设置/外观 五页，对照 verify-v120-phone-* 基线）。
- **替代修复方案**（若结构修复仍失败）：放弃从坏文件提取的块，按
  `docs/PLAN-v1.2.1.md` §二 从 v1.2.0 基线**重新手写** patch 017 的两段插入
  （管理卡 + 编辑弹窗），插入内容语义见 tools/patches/README.md 017 条目；
  app.js 数据层无需重做。

#### 移交检查清单（下一 Agent 接手步骤）

1. `git status` 确认未提交改动即本节描述状态；先跑 `tools/verify-markers.ps1`（应 39 绿）；
2. 按「已知未解问题 #2」诊断路径定位布局异常（先 iframe DOM diff，再 CDP 逐视图）；
3. 修复后：EXTRACT_VERSION+1 → 双端重装 → 五页截图核对 → 记忆管理器 CRUD 罐装回归
   （流程见本节上文与 PLAN-v1.2.1.md §六）→ verify-markers 全绿；
4. 全部通过后再走 CHANGELOG/README 发布流程（当前文档已标注「开发中·未发布」）；
5. 提交拆分建议：结构修复单独一个 commit（含 WORKLOG 修复记录）。

---

### 会话 15 开始 · v1.2.1 布局异常修复（2026-09-03）

任务：接手会话 14 移交，修复「顶部遗漏字段/底部溢出」布局异常 → 模拟器验证 → 推送。

### 会话 15 根因定位与结构修复（2026-09-03）

**根因（parse5 浏览器同源解析器实证，推翻「缺 2 个 </div>」假设）**
- 用 parse5（与浏览器同族的规范 HTML 解析器，jsdom 内核）对 v1.2.0 基线（39677d41）
  与当前 index.html 做树级对比：9 个 `.management-view` 中 8 个结构与基线完全一致，
  唯一差异=记忆视图多 1 个直接子级（patch 017 管理卡，预期插入）；
  patch 017 管理卡 22 开/22 闭、编辑弹窗 5 开/5 闭，div 全平衡——
  **会话 14 正则工具的 AFTER_MEMORY_BALANCE=-2 系旧坏构建残留 + 正则盲区，当前文件无缺 div**。
- **真根因：patch 018 head 注入第二段丢失 `<script>` 开标签**（index.html 原 L103）。
  `</script>` 后裸露 `document.write(...luzzy-theme.css...)` + 游离 `</script>`：
  规范解析下 head 内的非空白文本强制关闭 head、提前开启 body，三个后果——
  ① 裸文本与一个 href 解析损坏的 `<link>` 成为 body 首个子元素，
     `document.write('` / `');` 作为正文文本渲染（顶部错乱、内容下推）；
  ② `luzzy-theme.css` 主题底座加载失败 → patch 008 色板的 `--tw-*` 变量全失效
     → 全应用配色/玻璃层崩坏（布局异常直接来源）；
  ③ body 提前开始 + 尾部多余高度 → 底部溢出屏幕。
- 修复：在 `</script>` 与 document.write 之间补回 `    <script>` 一行
  （只动 patch 018 自身插入块，未触碰任何上游区块——遵守移交修复原则）。
- git 行尾坑：index.html blob 为 CRLF 为主的混合行尾（i/-text，git 不做 eol 转换），
  Edit 工具整文件归一会造成 3449 行伪 diff；最终以字节级操作还原 HEAD 后按锚点插入，
  `git diff --numstat` = 1 行新增（`+    <script>`）。

**验证（修复后）**
- parse5 树级对比：body 直接子级与基线模式一致（无游离 link/文本），
  management-view 结构 = 基线 + 管理卡，与意图完全吻合；
- verify-markers.ps1：39 PASS / 0 FAIL；
- entities/012-018-index-html.patch 重建（新 post-hash f1b28197，头部路径与旧格式一致），
  正向 apply（上游基线→结果 CR 归一后 == 当前文件）与反向 apply（当前→== 上游基线）双 PASS；
- EXTRACT_VERSION 13→14；tools/patches/README.md 补 018 登记条目；
  AGENTS.md §4.2 补 018 行；README.md patches 注释 001-015→001-018。

**待办**：模拟器重装 EXTRACT 14 构建 → 五页截图精确识图对照 v1.2.0 基线
（聊天/记忆/设置/外观/关于）→ 管理器 CRUD 罐装回归 → CHANGELOG/README 去开发中标注 → 推送。

### 会话 15 完成 · 布局异常修复验证通过（2026-09-03）

**完成**
1. 模拟器（LuzzyRP_Test，EXTRACT 14 debug 构建）逐页精确识图核对：聊天页（顶部 header
   完整/底部干净/暖色主题生效）、记忆系统页（引擎设置卡回位、向量检索卡正常、管理卡在位、
   底部干净）、外观页（主题卡/字体/字号齐全）、关于页（v1.2.1-debug + 应用内 CHANGELOG）、
   设置页（用户横幅品牌暖赭渐变、无蓝紫残留）——五页全部无「顶部缺字段/底部溢出」。
2. 管理器 CRUD 回归：编辑弹窗渲染正常；保存触发「正在重新嵌入…」禁用态；罐装嵌入端点
   未激活时 fetch 失败 → logcat 实锤 `[LuzzyRP patch 017] 分片保存失败: Failed to fetch`
   → toast + 按钮复位 + 不落盘（失败路径实测 ✓；成功路径会话 14 已罐装验证、数据层未改动）；
   启停（参与召回→已停用）✓；删除+确认弹窗 ✓；向量检索卡总分片即时联动 2→1 ✓；
   杀进程重启后分片数与「已停用」状态持久化 ✓。
3. 文档收尾：CHANGELOG v1.2.1 去掉「开发中/布局异常」标注、补「修复」条目（patch 018
   缺 `<script>` 根因）；README 版本表 v1.2.1 改「待发布」并注明修复；AGENTS §9 重写为
   会话 15 快照（含新坑记录：Edit 工具翻转 index.html 混合行尾、截图/实机坐标 ×1.2 换算）；
   luzzy-changelog.js 由 gen-changelog.mjs 重新生成。

**决策**
- 根因结论修正会话 14 移交假设：「缺 2 个 </div>」经 parse5 树级对比证伪（017 插入块
  22/22、弹窗 5/5 全平衡）；真根因 = patch 018 head 第二段注入缺 `<script>` 开标签，
  裸文本令 head 提前关闭、body 提前开始 + luzzy-theme.css 加载失败。
- 真机复验不顺延到单独会话：建议随 v1.2.1 正式包进行（EXTRACT 14 自动重解压）。

**遗留/下一步**
- 发布 v1.2.1：assembleRelease 正式包 + GitHub Release（待用户指示）；
- 上游 toggle 蓝（叙事视角选中态等）主题化（顺延）；
- classic 总结记忆管理器显示（依赖提取管线，顺延）。

**现场**：模拟器保留 EXTRACT 14 debug 构建（测试数据：记忆测试角色，1 分片已停用）；
无未登记改动（verify-markers 39 绿）。

---

### 会话 16 · patch 019 侧栏品牌化与预览交互化 + v1.2.1 发布（2026-09-03）

**任务（用户 5 点）**：① 侧栏关于置底；② 主题预览随主题切换 + 点击切深浅；③ 抽屉 RP HUB → LuzzyRP；
④ 全量改动复查；⑤ 真机验证后推 Release。

**设计合规**：触发硬性规定 9 → 完整阅读 4 项 SKILL 主文档；本任务为已选定方向上的迭代 +
用户给定明确方向（豁免三方向门，落档本节）；色值全部取 DESIGN.md 既有 token
（预览 classic 板 = 上游原值，语义正确不受「禁新增裸 blue」约束）；DESIGN.md 已同步。

**实施（patch 019 登记）**
- ui-components.js：app-logo RP/HUB → Luzzy/RP（双色同构开屏字标，下划线 w-14）；
  底部簇 外观→关于→设置 ⇒ 外观→设置→关于（关于置底）。
- index.html：外观页预览交互化——`rgb(var(--luzzy-prev-*))` 取色、luzzy 双卡为
  button（aria-pressed + 选中 ring + active:scale-[0.98]）点击直切 themeMode、
  classic 单亮卡、提示行。
- ext/luzzy-theme.css（扩展层零 patch）：`--luzzy-prev-light/dark-*` 8 变量 ×2 作用域，
  值全为既有 token。
- app.js：关于页 fallback 版本 v1.2.0→v1.2.1。

**登记与门禁**：tools/patches/README 019 条目；AGENTS §4.2 表 019 行；verify-markers
+2 项（41 PASS / 0 FAIL）；apply-patches 实体表 012-017→012-019（**修正移交遗留**：
表内引用 012-017-index-html.patch 但实体目录只有 012-018-*，指纹命中会重放失败）；
entities 四文件重建为 012-019-*，正/反向 apply 8/8 PASS；EXTRACT_VERSION 14→15；
node --check ×2 + CSS 括号平衡 + parse5 结构复验（唯一差异=管理卡，预期）；
index.html 编辑再遇整文件行尾翻转 → 字节级锚点替换解决（numstat 24/12）。

**模拟器验证（EXTRACT 15 debug）**：首启正常；抽屉 Luzzy RP + 外观→设置→关于 ✓；
预览双卡渲染 ✓；点暗卡全 UI 变暗+ring+下拉同步 ✓；点回亮卡 ✓；切 classic 预览变
单张上游蓝灰卡+暗卡消失 ✓；切回 luzzy 杀进程重启持久化 ✓。

**真机验证（小米 df97f3c4，release 包 arm64）**：MIUI 拦 adb 注入 → 用户开启
「USB 调试（安全设置）」后全流程通过：首启公告暖色 ✓；欢迎弹窗走完（Tester）✓；
抽屉 a11y 树 text="LuzzyRP" + 顺序 ✓；外观页双卡 ✓；点暗卡全 UI 变暗无白块 ✓；
切回亮 + 杀进程重启持久化 ✓。adb 坑新增：MIUI INJECT_EVENTS 需安全设置开关；
多显示器警告下 screencap 默认 display 可用。

**发布**：assembleRelease 三件套（arm64/universal/x86_64 ≈17.97MB，versionCode 8）；
CHANGELOG v1.2.1 并入 019 条目（标题改「侧栏品牌化 × 主题预览交互化 × …」）；README 版本表
更新为正式版；gen-changelog.mjs 重跑；提交 b1c74c53 推送；**GitHub Release v1.2.1 已发布**
（附三件套 APK，仅稳定版附 APK，硬性规定 8）；发布状态回写 CHANGELOG/README/AGENTS §9。

### 会话 16 补充 · 应用图标粉底修复（2026-09-03）

**现象**：新装包（release/debug）图标出现粉红底，用户要求恢复透明贴纸效果。

**根因**：自适应图标背景色 `ic_launcher_background`（values/colors.xml）在 v0.2.0-wave1
（2e85c21f）由 v0.1.0 的深梅紫 #2A0E22 改为粉红 #FF6EC7。自适应图标 = 背景色 +
前景贴纸（ic_launcher_foreground 本身透明底），透明区露出背景色。用户原应用的桌面
图标是 MIUI 桌面旧缓存渲染，新装包触发重新渲染后粉底显形——非本次 v1.2.1 引入。

**修复**：colors.xml 背景色改全透明 #00000000（一色值改动，图标画作资源零触碰，
仍遵守「禁止重新生成」）。模拟器 AOSP 桌面（Launcher3）把透明合成到黑底属该桌面
无 alpha 处理的局限；MIUI 支持 alpha 合成，真机效果待用户复装确认（若 MIUI 仍异常，
后备方案=以 luzzy_logo.png 原作为源机械派生透明版 mipmap PNG，需用户确认后做）。

**附带**：CHANGELOG v1.2.1 补修复条目 + gen-changelog 重跑；debug/release 双包重建
（versionCode 仍 8，可直接覆盖安装）；桌面两份 APK 已刷新为修复后构建；
GitHub Release v1.2.1 三件套已 clobber 替换。设计条款豁免说明：用户明确指定
恢复透明（缺陷修复式资源呈现修正，非新视觉设计），4 项设计 SKILL 本会话已读。

---

### 会话 17 · 文档状态回写 + 发布卫生 + 工作区整洁（2026-09-03）

**任务（用户决策：方案一+二+三 / 彻底删除 / 补打 r2 tag）**：①文档状态同步；②发布卫生
（补 tags + v1.2.1-r2）；③工作区整洁（冗余目录彻底删除）。方案四技术债后置排期。

**完成**
1. **文档回写（硬性规定 5）**：README 状态徽章（开发中·不可游玩 → 正式版·可游玩，10B981）、
   版本规划表 v1.2.1 行改「✅ 已发布，附 APK」、「9 条」→「10 条」两处 + 规定 10 补入枚举；
   AGENTS §1.1/§3.1（9→10 条）、§1.5（PLAN 指针 → PLAN-v1.2.1）、§9 快照陈旧值修正
   （verify-markers 39→41 项、entities 012-018→012-019、EXTRACT 14→15、真机已复验、
   遗留待办复核重排：清除已完成项，补记图标复装确认/检索失败 toast/上游同步演练）；
   HARD_REQUIREMENTS 规定 2 守护落点 001-005→001-019（按 AGENTS §1.1 于 CHANGELOG 声明）。
2. **发布卫生**：`git fetch origin --tags` 补齐本地 v1.0.0~v1.2.1 tags（此前本地只有 v0.x）；
   新打轻量 tag `v1.2.1-r2` → 7976aba0（Release APK 实际构建点；原 v1.2.1 tag 指向
   b1c74c53，与 clobber 替换后的分发产物存在漂移，用户选定补 r2 方案）。
3. **工作区整洁（硬性规定 7，用户选「彻底删除」）**：`git rm` 约 1.246 万入库文件——
   docs/game-icon-pack(9780)/lobe-ui-master(1556)/rikkahub-master(992)/AlibabaPuHuiTi-3(50)/
   AlibabaSans(30)/D&D 5e SRD(21)/task(16)/brand-logos(5)/trpg标准世界卡(2)/audit(1)/
   AGENT-GUIDE/INVARIANTS-CHECKLIST/PLAN-v0.1.0；另发现并移除
   `app/src/main/assets/CHANGELOG.md`（v0.2.0 时代残留 77 行，git grep 证实零代码引用）。
   .gitignore 增 14 条防回流规则。保留：docs/skills（随仓库分发既定决策）、docs/design、
   PLAN-v1.x、WORKLOG。清理后 docs 入库从约 2.67 万文件降至约 1.43 万（本次移除 ≈1.24 万）。
   删除前全库引用扫描：AlibabaSans/PuHuiTi 匹配均为字体名而非目录引用。
4. **CHANGELOG v1.2.1 注意事项补 2 条**（文档回写声明 + 清理声明）；gen-changelog.mjs 重跑
   （luzzy-changelog.js 21602 chars——本会话不出包，下次发版随 versionCode/EXTRACT bump
   生效到设备）。
5. **门禁**：verify-markers.ps1 复跑 **41 PASS / 0 FAIL**（清理不触及 rphub/）。

**决策**
- 冗余处置=彻底删除（用户选择；不走本地 archive）。删除后 docs 入库从约 26.7k 文件
  降至约 14.3k，其中 14.27k 为 skills/design/PLAN/WORKLOG 项目文件，仓库显著瘦身；
- v1.2.1-r2 采用轻量 tag（与既有 tag 风格一致，for-each-ref 实测均为 commit 直指类型）；
- 上游新版复核未完成（GitHub fetch 网络重置，本地基线 b409ca6 暂无新提交记录）——
  列入遗留待办「上游同步演练」。

**遗留/下一步**
1. 方案四技术债（用户排期后置）：toggle 蓝主题化（先走硬性规定 9 设计合规链，建议 v1.2.2）、
   向量检索失败 toast 外化（候选 patch 020）、classic 总结管理器显示、图标复装确认；
2. 网络可用时跑上游同步演练（sync-upstream.ps1 假发版模拟）；
3. 推送本会话两个提交 + v1.2.1-r2 tag。

**现场**：仓库零未登记上游改动（verify-markers 41 绿）；删除已 staged；
EXTRACT_VERSION 本会话未 bump（不出包，无设备安装；下次发版照常 bump）。

---

### 会话 18 · v1.2.2 开发：toggle 蓝主题化（patch 008 v4）+ 检索失败外化（patch 020）（2026-09-03）

**任务（用户决策：执行 1+2，静态门禁方案、视觉验证留待设备）**：
① 上游 toggle 蓝/indigo 主题化；② 向量分桶检索失败 toast 外化。③（classic 补测）因模拟器
未运行顺延；（图标复装确认）需用户复装，非 Agent 可代。

**设计合规（硬性规定 9）**：本会话完整复读 4 项 SKILL 主文档（huashu SKILL.md 全文 579 行 /
open-design AGENTS.md 全文 / ui-ux-pro-max CLAUDE.md+SKILL.md / awesome-design-md README）。
三方向门豁免：属 v1.2.1 已选定「品牌色收编」方向的延续迭代 + 用户本次明确指示修复
（huashu 豁免清单第 2 类，落档本节）。色值零新发明：blue/indigo 在 luzzy 下=primary 同值，
classic 下=Tailwind 原值。

**实施**
1. **patch 008 v4**（index.html tailwind.config，字节级锚点编辑）：blue/indigo 色板接入
   `rgb(var(--tw-*) / <alpha-value>)`（各 50-900 十阶，覆盖上游实际用到的全部色阶——
   实测 41 处 blue-* + 8 处 indigo-* 均在 50-900 内，无 950）。luzzy-theme.css 三作用域
   各 +20 变量：classic=Tailwind 原值（零影响）/ luzzy 亮暗=primary 同值（收编为珊瑚）。
   violet 3 处保留为协议徽标功能区分色（v1.2.0 critique 备案）。
2. **patch 020**（app.js 两处 catch，~7132 注入检索 / ~7218 手动检索）：console.warn 外增
   节流 toast——`window.__luzzyVectorToastAt` 30s 全局节流防离线刷屏；showToast 以
   try/catch 引用（不在作用域时自动降级为仅 warn，扩展层不伤主流程）。登记
   tools/patches/README 020 条目 + verify-markers 2 项（020-vector-toast / 020-toast-throttle）。
3. **登记链同步**：apply-patches（008 块注释 v4 + 实体表 012-020 + 标题行）、AGENTS
   （§1.5/§4.2 表 020 行/§9 快照会话 18 + v1.2.2 开发中条目 + 主题速览 blue/indigo）、
   README（目录注释 001-020 + 规划表 v1.2.2 🚧 行）、HARD_REQUIREMENTS（001-020）、
   DESIGN.md（Do's & Don'ts 收编清单+色板级收编段 / 技术契约变量清单+008v4）、
   AssetExtractor EXTRACT_VERSION 15→16、CHANGELOG 新增 v1.2.2 开发中章节。

**entities 重建（012-019 → 012-020）**
- index.html/app.js 内容变更，ui-components/runtime-services 仅改名（diff 内容与旧实体
  逐字节一致——大小 12396/19276 与旧文件相同）；旧 012-019-* 四件删除。
- 生成法：`git diff --no-index --ignore-cr-at-eol rp-hub-reference/<f> app/src/main/assets/rphub/<f>`
  （Start-Process 原始 stdout 重定向防 EOL/编码篡改），随后**字节级**将 3-4 行
  `---/+++` 归一为 rphub 相对路径短格式（与旧实体一致）——**全路径格式会使
  apply-patches 的 `--directory=app/src/main/assets/rphub` 前缀叠加错位**。
- 双向验证：pre-blob hash 4/4 == 上游基线（b290e220/f73b1ddd/7fcb10e7/473672b8，正向
  apply 数学等价成立）+ `git apply -R --check --directory=…` 反向 4/4 exit 0（post==当前）。

**实施中连环抓错（语法门禁立功，均为致命级）**
1. 双逗号：插入 anchor 本身带尾逗号又补了一个 `',,'`（numstat 25/2 行数正常，
   **整块提取 node --check 才暴露**）；
2. 吞括号：$seg 含 primary 块闭合 `}` 但 $new 未还回（brace depth 计数定位）。
   修复后：tailwind.config 整块 node --check exit 0 + brace depth 0、numstat 25/1。
   **教训入册**：字节级插入上游文件后必须做「内容级语法校验」（提取块 node --check +
   括号深度计数），行数级 numstat 检查无法发现同行错误。

**门禁汇总**
- node --check app.js ✓；tailwind.config 整块 node --check ✓ + brace depth 0 ✓；
- luzzy-theme.css 括号 80/80 平衡 ✓；git diff --numstat 无整文件伪 diff ✓
  （index.html 25/1、app.js +22 行级、luzzy-theme.css +65 行级）；
- verify-markers.ps1 **43 PASS / 0 FAIL**（+020 两项）；apply-patches.ps1 自检全 SKIP 无 FAIL；
- pro-rules 快查：纯色彩 token 层改动，无 DOM/交互/触控目标变化，亮暗对比沿用 primary
  既有实测值（白字 4.7:1/4.5:1）。
- 五维 critique：方向（收编延续+用户指示）✓ 品牌（零新色，全 token）✓ 层级（状态色
  语义与按钮/链接一致）✓ 动效（零新增）✓ 工程（上述门禁）✓。

**遗留/下一步**
1. 视觉验证待设备：toggle/叙事视角 luzzy 下珊瑚化 + classic 对照原蓝（用户所选静态
   门禁方案的既定边界）；建议随 v1.2.2 发布走查一并做；
2. v1.2.2 发布流程待指示（versionCode 9 / assembleRelease / CHANGELOG 去「开发中」/
   GitHub Release）；
3. 图标复装确认（用户）、classic 总结补测（需模拟器）、上游同步演练（需网络）。

**现场**：EXTRACT_VERSION=19（v4 实施后连升 16→17→18→19，见下）；versionCode
仍 8（未发版）；仓库改动全部登记（008 v4 + 020 + 登记链 + entities 重建）；无未登记
上游改动（verify-markers 43 绿）。

### 会话 18 补充 · 真机验证（小米 25098PN5AC / Android 16 / df97f3c4，EXTRACT 19 debug）

用户接入真机，安装 EXTRACT 19 debug 包（install -r，数据保留）实测：

- **启动回归 ✓**：重解压后首启正常，用户真实会话/立绘/雾纸玻璃完好，无布局异常；
- **侧栏激活项 ✓**：luzzy 亮色下「聊天/设置」激活态=暖珊瑚底+珊瑚竖条+珊瑚图标
  （修复前实测为硬编码蓝——styles.css `.sidebar-nav-button.bg-primary-50` 家族，
  详见下「实施中发现」）；暗色下同族=暗珊瑚 ramp ✓；
- **叙事视角选中态 ✓**：用户设置页 segmented-switch 选中项由蓝变珊瑚
  （styles.css `segmented-switch__option.is-active{color:#2563eb}` 收编）；
- **settings-toggle 家族 ✓**：自动获取模型/流式输出/使用封面背景三枚 checked 开关
  由蓝/indigo 变珊瑚（含 --compact/--indigo/--solid 变体收编）；未勾选「沉浸模式」
  保持中性灰=正确；
- **暗色全页 ✓**：暗纸底+暗珊瑚横幅/toggle，无白块无破损；
- **用户状态已恢复**：外观页点亮色卡回到 Luzzy+亮色（用户原始设置）；
- **classic 对照未在真机执行**：外观页自定义下拉对 adb input tap 无响应（已知坑族）；
  机制保证：全部收编规则 scoped 于 `:root[data-theme="luzzy"]`，classic 作用域变量
  = Tailwind 原值（与 v1.2.1 逐字节同源）；建议用户日常手动切 classic 目测一次；
- **toast（patch 020）真机未触发**：需制造检索失败（断网/改嵌入配置），涉及用户
  真实数据与网络，不做；留待模拟器罐装或用户日常使用观察；
- ⚠ **操作事故披露**：验证中途一次 back 键退出应用落到系统「修改闹钟」对话框
  （已按返回取消、未保存；期间一次 tap 可能触到时间轮盘）——**若用户闹钟原本不是
  08:02 请自查**。后续真机操作已改为每步截图确认。

**实施中发现（真机实测驱动，已全部收编）**：styles.css 存在 ~70 处硬编码蓝字面量
（不走色板工具类，patch 008 色板机制覆盖不到）。本次按「实测可见优先」收编 4 族——
①侧栏激活项 `.sidebar-nav-button.bg-primary-50`（渐变/阴影/::before 竖条）；
②`.segmented-switch__option.is-active`；③`.settings-toggle` checked 家族
（--compact/--indigo/--solid + focus ring）；④`.modal-primary-button`（弹窗主按钮）。
其余低频组件清点入 v1.3.0 遗留（DESIGN.md 已同步）。EXTRACT 16→19 连升记录迭代过程。

### 会话 18 收尾 · 用户决策：发布暂缓，继续功能更新（2026-09-03）

- **用户指示**：暂缓 v1.2.2 发布流程——不打 versionCode 9、不 assembleRelease、
  不 GitHub Release；用户将继续提出功能更新。
- **推论**：后续功能预计并入 v1.2.2 一并发布，版本范围随之扩展；CHANGELOG
  「开发中（未发布）」状态维持，README 规划表 v1.2.2 行保持 🚧。
- **现场交接**：EXTRACT_VERSION=19；verify-markers 43 PASS / 0 FAIL；versionCode 仍 8；
  真机已装 EXTRACT 19 debug（主题已恢复用户原设置 Luzzy+亮色）；main 已推至
  8927b2eb；工作树干净；零未登记上游改动。
- **下一会话入口**：直接接功能需求，按 AGENTS §3.2 流程评估归属
  （扩展层优先 → 必要时登记 patch 021+）；发布动作等用户明确指令。
- **附（仓库描述纠正，用户指出「我们不是 Kotlin 原生项目」）**：GitHub About 原描述
  误写「原生 Kotlin + Jetpack Compose」——本项目为 WebView 封装（Compose 从未使用）。
  已改为「基于 RP-Hub（Vue 3 前端）的安卓 WebView 封装 + 独立扩展层与主题系统」；
  README 简介两处「原生安卓」措辞同步收敛为「WebView 封装（Kotlin 薄壳）」定位；
  全仓扫描确认现存 Compose 引用均为「旧 v0.x 工程已推倒重建」历史记录（准确保留）。

---

## 会话 19 · 全新品牌图标替换 + v1.2.2 发布（2026-09-04）

**任务（用户指令，解除会话 18 发布暂缓）**：新 icon（用户 AI 生图，桌面源图不动、
复制入库）全面替换所有品牌 LOGO → 立即构建 APK 推送新版本。设计阶段（三方向
提示词）落档 `docs/design/icon-v2-directions.md`，用户自选生图。

**实施**
1. **源图入库**：`docs/design/brand-logo-v2-source.png`（1254×1254，White Fox
   头像版：白发狐耳+额前护目镜+紫瞳+黑色项圈+白色工装，暖奶油底+珊瑚记号——
   与暖幕手记品牌同构）；桌面原件未动。
2. **Pillow 产出全套**：mipmap 五密度 `ic_launcher`（满幅）/`ic_launcher_round`
   （圆形 alpha 抗锯齿）/`ic_launcher_foreground`（108dp 网格，全图 68% 居中 +
   边缘均值色画布）；`drawable-nodpi/luzzy_logo.png` + `ext/luzzy-logo.png`
   （各 192）。圆形蒙版预览验收（`brand-logo-v2-preview.png`）：耳朵在圆内
   有余量、护目镜完整、48px 可辨 ✓。
3. **adaptive 方案切换**：`colors.xml` #00000000 → **#EDD7BD**（新图边缘均值，
   历史链 #2A0E22→#FF6EC7→transparent→#EDD7BD）；透明贴纸方案终止。
4. **约束移交**：AGENTS §1.2「禁止重新生成」条目改写为「2026-09-04 用户主动
   替换，后续换 icon 需用户提供新图并确认」。
5. **版本链**：versionCode 9 / versionName 1.2.2；EXTRACT 20；CHANGELOG v1.2.2
   转正式 + 全新品牌图标条目；README 徽章 v1.2.2 / 规划表 ✅ / 版本对应表加行；
   gen-changelog 重跑（22941 chars）。

**发布**（结果见下节补充）：assembleRelease 三件套 → 真机快验 → push →
GitHub Release v1.2.2（tag + 三件套 APK，沿用 v1.2.1 排版）。

### 会话 19 补充 · 发布执行记录（2026-09-04）

- verify-markers 43 PASS / 0 FAIL（res/ext 图标替换不触及 rphub 上游文件）；
- assembleRelease 三件套完成（17.3MB ×3——应用无 native 库，三 ABI 内容一致属正常）；
- **真机 release 包未复验**：安装阶段设备 USB 断开（no devices）。delta 评估：
  EXTRACT 19 debug 已完成主题变更真机验证，EXTRACT 20 增量=图标资源 + 应用内
  CHANGELOG 文案（资源级，风险低）；图标本身已经圆形蒙版/48px 阶梯双重预览校验。
  待用户下次接入/复装时补看 MIUI 桌面新图标渲染效果（MIUI 桌面图标有缓存，
  更新安装后如未刷新可重启桌面或重装触发）。
- **换图标 SOP 定稿（用户指示：以后换图标都按今天的步骤）**：AGENTS.md 新增
  §3.5「更换应用图标 SOP」——源图入库约束/设计合规（规定 9）/圆形蒙版+48px
  验收门/Pillow 全套产出清单/adaptive 方案约定/EXTRACT bump/文档同步/发布联动，
  含红线（画作资源必须用户提供新图并明确指示）；§1.2 图标条目与 §9 快照同步
  刷新至会话 19。

### 会话 19 补充 · 真机 release 复验（2026-09-04，v1.2.2 发布后）

用户要求真机验证（提示有两台设备，指定用小米）。过程与结果：

- **设备辨别**：adb 当时仅枚举到小米 `df97f3c4`（25098PN5AC / pandora /
  Android 16）；另一台未出现在 adb 列表。小米设备码已按用户要求写入
  AGENTS.md §6.1。
- **关键发现：真机日常包 = debug 包**。设备只装 `com.luzzymeow.luzzyrp.debug`
  （v1.2.1-debug / versionCode 8 / EXTRACT 19），用户真实数据都在 debug 包内；
  直接装 release 包（com.luzzymeow.luzzyrp）会生成第二个空数据 LuzzyRP（数据分裂）。
  且首次 release 安装曾**静默失败**（MIUI「USB 安装」确认框无人确认，输出被吞）——
  教训：install 命令后必须核对 `Success` 输出。
- **处置**：改用 **assembleDebug（v1.2.2-debug，versionCode 9，EXTRACT 20）install -r**
  覆盖日常包——数据保留、版本/图标全部到位。
- **验证结果（全通过）**：①MIUI 桌面图标 = 新 White Fox LOGO（无缓存问题，
  `verify-v1222-launcher.png`）；②应用正常启动、真实会话渲染完好；
  ③关于页 = v1.2.2-debug + 新品牌 LOGO + 应用内 CHANGELOG 显示 v1.2.2 章节
  （`verify-v1222-about.png`）。EXTRACT 20 重解压生效。
- release 三件套 APK 维持 GitHub Release v1.2.2 分发用途不变。

---

## 会话 20 · 上游同步 RP-Hub 1.9.0（2026-09-04）

**任务（用户指令「开始同步」）**：同步上游 1.9.0（本会话前置核查发现：上游 2 新提交
仅动 built-in-content.js +8/-13，破限预设标记改名 roleplay_hub_default→rphub_default
+ UI 模板提示词微调 + 更新公告 1.9.0；nsfw_rules 未触碰；我方 patch/扩展层零引用旧标记）。

**开始状态**：main 1e9342d7 干净；verify-markers 43 PASS；versionCode 9 / EXTRACT 20。

**同步执行（mini-sync）**
1. 参考克隆快进 b409ca6→94a0cd9（ff-only）；built-in-content.js 覆盖至 1.9.0，
   源/目标 SHA-256 双核一致（F8B74D0D…BC220）；
2. 指纹基线表：built-in-content.js 条目 + 表头更新至 1.9.0（94a0cd9）；
3. apply-patches 重放 21 项全部幂等 SKIP（本次未覆盖任何被 patch 文件，佐证无遗漏）；
4. verify-markers 43 PASS / 0 FAIL（R1 按新基线通过）；
5. EXTRACT_VERSION 20→21；CHANGELOG 新增 v1.2.3「开发中」章节；README 基线号 2 处
   （简介+badge）→1.9.0；AGENTS §9 快照刷新。

**决策**
- gen-changelog 本轮【不】重跑：脚本整包嵌入 CHANGELOG.md，重跑会把「开发中」章节
  带进日常包关于页；按 §3.4 发布流程在发版步骤 3 前重跑（登记为发版遗留）；
- 未走 sync-upstream.ps1 全量覆盖：上游仅 1 文件变更，按变更文件精准覆盖 + 幂等重放
  + 校验门全绿达成同一终态（上游 diff 仅 built-in-content.js +8/-13，vendor/字体无变化）。

**遗留 / 下一步**
- 真机（小米 df97f3c4）未连接：debug 包（EXTRACT 21）构建完成后待 install -r 覆盖
  日常包，按 §6.2 走数据兼容+核心功能回归（重点：破限预设标记改名后的对话回归）；
- 发版 v1.2.3 时：§3.4 全流程（versionCode 10 / gen-changelog 重跑 / README 版本对应表加行）。

### 会话 20 功能段 · 六大需求开发（2026-09-04，v1.2.3 开发中）

**任务（用户指令）**：①开屏动画自创设计（规定 9 全流程）②向量检索失效排查修复
③关于页滑动/置顶/版本分类/关键词搜索 ④用量趋势折线图（三粒度+供应商/模型筛选）
⑤设置页三修（STA1N 图标/残留外观入口/存储自动统计）⑥聊天页去全屏按钮。

**完成**
- 设计合规：复读 4 项设计 SKILL 主文档（规定 9）；开屏动画三方向板产出
  docs/design/splash-v1/board-{a,b,c}.html（A 手稿终端·轮盘 16 号 / B 开卷·Aēsop 参照 /
  C 落笔成序·原研哉视角），待用户选定后落地（patch 027 预留）。
- patch 021 设置页清理（残留外观入口区整段移除+存储自动统计+文案）：index.html + app.js watch。
- patch 022 聊天页全屏整体下线（按钮/绑定/ref/函数/监听/暴露，styles.css 上游死规则不触碰）。
- patch 023 STA1N 图标修复（上游图床 404 → cdn.sta1n.cn/favicon.ico，core-utils + novel 同修）。
- patch 024 关于页增强（版本章节解析 + 分类/搜索过滤渲染管线 150ms 防抖 + 置顶 FAB +
  ext 动效层：进 200ms/退 140ms ease-out、scale(0.96) 起步、reduced-motion 降级）。
- patch 025 用量趋势折线图（数据层三粒度分桶 + provider::model 分序列 + Top8 溢出合并 +
  token 分类色板；TokenUsageView 纯 SVG 卡；recordApiUsage 补存 provider/protocol——
  修复 patch 012 半成品导致的 [商名] 前缀从未显示；历史记录 apiUrl 反查兜底）。
- patch 026 向量检索修复（手动检索摘除保留窗排除 + 死供应商显式报错 + 裸引用回退协议
  跟随激活商）。排查报告：入库链路完好，前端过滤死区（A 排除窗 > B 桶跳过 > C 阈值 0.45 硬编码遗留）。
- 设施：EXTRACT 22；verify-markers manifest +13（56 项）；实体范围式再生成
  （007-023/009-023/012-026）且 6 实体逆向 --check 全过——顺带修复 apply-patches 实体
  文件名与磁盘名不一致的潜在坑（012-020-assets-js-* 实际名 vs 脚本引用名，被 SKIP 逻辑掩盖）。
- 门禁：apply-patches 全 SKIP；verify-markers 56 PASS / 0 FAIL；app.js/ui-components.js/
  runtime-services.js/core-utils.js node --check 全过。

**决策**
- ③④组件级新增遵循 DESIGN.md 既有 token（非新视觉方向，三方向门不适用；开屏动画走完整门）；
  折线图分类色序落档 DESIGN.md（数据可视化分类色序节）。
- 向量阈值滑杆（排查项 C）列候选迭代不随本版；gen-changelog 仍留发版时重跑。
- CHANGELOG v1.2.3 章节已扩写全量内容；DESIGN.md 增补三处（组件表/分类色序/FAB 动效）。

**遗留 / 下一步**
- 开屏动画：等用户三选一 → 落地 patch 027（index.html 开屏 DOM + ext 动效）+ CHANGELOG 补条目；
- 真机回归（小米 df97f3c4 未连接）：EXTRACT 22 debug 包 install -r + §6.2 全量走查
  （重点：向量检索复测、用量图渲染、关于页搜索/置顶、设置页自动统计）；
- 模拟器罐装走查 024/025 新 UI 交互（可选，真机回归可覆盖）。

### 会话 20 补充 · 开屏「开卷」落地（patch 027，2026-09-04）

- 用户三方向选定 **B「开卷」**（原话落档 docs/design/splash-v1/direction-approved.md）；
  方向板预览「看不到动画」= 单次播放后定格终帧（iframe 已播完），点板内重播可复看。
- 落地：index.html 上游 entry-transition DOM 整体替换为 luzzy-splash DOM（标记 027）+
  ext/luzzy-theme.css 动画全量样式（掀封→纸落→界格→钤印→落墨→荧光→页码 ≈2.3s，
  2.55s 起 450ms 淡出交还主界面；亮/暗随 data-mode 首帧自适应；reduced-motion 终帧直出
  +200ms 退场；纯 transform/opacity）。
- **patch 003 重放块退役**：入口字标并入新开屏（003 标记保留于 index.html 注释，
  verify 003-logo PASS；意图由实体 012-027 承载）——apply-patches.ps1 留退役说明。
- 设施：实体 012-026-index → 012-027-index（逆向 --check PASS）；manifest +027（57 项）；
  EXTRACT 22→23；apply/verify 全绿（57 PASS / 0 FAIL）。
- 遗留：真机开屏首帧实测（WebView 下 CSS 动画起始时机 + 字体闪变观察）。

### 会话 20 补充 2 · 真机首验反馈修复 + 全面审计（2026-09-04）

**用户真机反馈**：①开屏动画「完全失败」（不符合交付标准，要求 20 帧逐帧审）
②关于页 CHANGELOG 内容全部消失 + v1.0.0 ×4 + 远古空版本号。

**逐帧审片（无头浏览器虚拟时钟 + 20fps 采样，harness=应用真实 DOM/CSS）**：
- 实锤缺陷：①024 编辑事故——正文渲染元素被 .*? 替换吞掉未补回（CDP DOM 实证
  v-if/v-else 双缺，changelogHtml 有值无渲染位）；②版本正则截断 rc 后缀（v1.0.0×4）；
  ③掀封 rotateX 在 fixed 层不可读（父级 perspective 链路 + 淡出过早）→ 观感=「封面原地消失」；
  ④内容节拍滞后造成 ~400ms 空场；⑤公告弹层压开屏（z 序）；⑥冷启动白闪。
- 环境发现（重要）：无头虚拟时钟截图下 fixed 层 transform 动画不推进（opacity 正常、
  非 fixed transform 正常、静态 transform 正常）——逐帧审只能看编排节拍，
  transform 动效必须真机实看。
- 修复：正文元素恢复 + rc 独立/下拉去重 + 节拍 v2（内容前移交叉、翻角加大、opacity 后段
  保持）+ 封面离场双保险（上滑离屏为主 + 自透视翻页为辅）+ z-index 200 + 窗口底色暖纸 +
  EXTRACT 24 + gen-changelog 重跑（25177 字符，带 v1.2.3 开发中章节）。
- 逐帧复审：节拍连续无空场 ✓ 定格/退场 ✓ reduced-motion 降级路径 ✓（headless 默认即该路径，
  顺带完成降级验证）。

**全面审计（用户指令：占位/转义/替换安全）**：
- 替换伤亡清点 14 项 PASS（唯一标记项=patch 026 有意移除的死 wrapper，已清理）；
- 重复插入 9 项 PASS；chart 卡区域无反引号/插值（模板字面量安全）；
- TODO/FIXME/占位标记 5 文件全零；CSS 花括号 149/149 平衡；
- verify-markers 57 PASS / 0 FAIL；4 个 JS node --check 全过；
- 桌面端到端（真实应用 file:// 启动 + CDP）：挂载无错误、关于页正文 30676 字符/13 章节、
  下拉默认「全部版本」、用量趋势卡在位（空数据态）、设置页存储标签/无外观残留、splash DOM ✓。

**真机待验（用户回来后）**：开屏实机观感（含系统「减弱动画」设置检查）、用量图有数据渲染、
向量检索复测、§6.2 全量。桌面审计已完成的部分不重复。

### 会话 20 补充 3 · 真机回归全量通过（2026-09-05 凌晨，EXTRACT 24，小米 df97f3c4）

**方法**：screenrecord 原生帧率录制（315 帧 ≈51.7fps）+ 50fps 抽帧拼图逐帧判读开屏；
UI Automator 坐标驱动页面走查 + 全屏截图取证（证据：docs/design/verify-v123-*.png）。
系统动画设置正常（三 scale=1.0，无减弱动画）→ 完整动画路径渲染。

**开屏「开卷」（51.7fps 逐帧）**：暖白窗口过渡（窗口底色暖化生效）→ 封面（发丝框+
珊瑚题签）→ **掀封 3D 翻页真机成立**（透视梯形逐帧收窄上移，sheetB/C 可辨）→ 纸落 →
钤印(白狐 logo) → 落墨(双色字标) → 标语 → 荧光划线 → 页码 → 暖光 → ≈2s 定格保持 →
交叉淡入主界面；**公告弹层全程被压在开屏下（z-200 ✓）**；用户数据完好。
逐帧证据：frames/dev_sheetA-D.png（sheetA 启动、sheetB 掀封、sheetC 内容、sheetD 定格退场）。

**功能走查全过**：
- 关于页：CHANGELOG 正文完整渲染（30676 字符/13 章节）+ 下拉默认「全部版本」+
  搜索框在位 + **滚动后置顶 FAB 出现、点击平滑回顶 ✓**；
- 用量统计：总用量 15.59 万；**趋势折线图双系列真机渲染**（GLM-5.3-Flash 峰值 15.9万 /
  embedding 贴地）、供应商 chip「全部供应商/STA1N API」、模型 chips 带色点、Y/X 轴齐全；
  **provider 反查兜底真机生效**（provider 缺失旧记录正确归入 STA1N API）；
  「日」窗口空=正确（记录距今 39h 超 24h 窗），「月」窗口数据正常；
- 设置页：**STA1N API 图标显示官方 favicon（不再空白）**；存储改「存储空间占用」+
  **自动统计生效**（14.5MB/10GB+分类明细自动测出，无手动按钮）；高级设置区无外观入口残留；
- 向量检索（patch 026 核心修复）：查询 test → **「记忆分片 · 10 / 20」全部返回**，
  相关度 60.7%（gemini-embedding-2 真实余弦打分）——**不再报「还没有可检索的向量分片」**
  （修复前该场景必报，因分片全落保留窗排除）；
- 聊天页：右上角全屏按钮消失（仅剩删除键）✓。

**已知小瑕疵（记录不阻塞）**：月粒度桶起点标注与预期 ±1 天（时区取整毛刺，外观级）；
折线图模型标签「STA1N API · [Cloud]GLM-5.3-Flash」存在品牌名冗余（数据源模型名本身含
[Cloud] 前缀所致，显示语义正确）。

**结论**：会话 20 六大需求 + 上游同步 + 本轮全部修复，真机验证**全量通过**。

### 会话 20 补充 4 · 开屏 v3 门扉交互 + 主题单轨化（2026-09-05，EXTRACT 25，待真机）

**用户指令**：①开屏重做——掀封叙事移除（图一图二不要），直接从终帧构图淡入 → 进度条
自左向右 → 「沉溺」按钮（非「开始」）浮现并停滞等待点击 → 点击触发 轻微眩晕+泡泡+
中心放大 转场进聊天页；品牌色不变；三方向豁免（用户明示由 Agent 决定细节）。
②主题单轨化——删除经典主题与主题切换，恒定「暖幕手记」，外观页其余设置不变。

**实现**：
- patch 027 v3（index.html DOM + luzzy-theme.css splash 段重写）：门扉结构
  （page/bubbles/stack+progress+沉溺按钮/rule/pagenum）；入场 0.45s 整体淡入+内容轻阶梯；
  进度条 1.4s 自左向右（coral→amber）；按钮 2.25s 浮现+呼吸；转场 lspDiveZoom .95s
  （微摆眩晕→blur 失焦→scale 2.35 中心放大→淡出）+ 泡泡层 10 枚错峰上浮
  （coral 描边+amber 内透，水下隐喻呼应「沉溺」）；reduced-motion 近零时长直出可点态、
  转场退化 220ms 淡出；pointer-events auto（门扉拦截底层）。
- **ext/luzzy-splash.js 新建**（行为状态机 ~40 行：点击→lsp-dive→animationend/兜底收殓）；
  patch 005 挂载区更新（第 4 行 splash.js，SKIP 检测同步）。
- patch 028 主题单轨化（index.html 外观页 + app.js）：界面主题下拉卡删除、预览卡简化为
  亮/暗模式预览（patch 019 交互保留）、模式卡恒显；app.js themeOptions 移除 + 老用户
  classic 无条件迁 luzzy + expose 清理；luzzy-theme.css 作用域恒真不动。
- 设施：EXTRACT 25；实体 012-028（index/app 重生成+逆向 PASS）；manifest 59 项
  （011-theme-ui 退役→028-no-theme-switch notcontains、+027-splash-js/+028×2）；
  verify-markers **60 PASS / 0 FAIL**；node --check 全过。

**转场设计佐证**：转场保留上下文/连续性原则（uxdesign.cc Transition animations
practical guide + Smashing Magazine Improving User Flow，通用原则中置信度）；
泡泡水下隐喻与「沉溺」文案自洽；中心放大+气泡上升=相对运动强化坠入感。

**真机验证**：设备 USB 再次掉线，EXTRACT 25 包已构建待装。待验清单：门扉开场节拍、
沉溺按钮浮现/呼吸、点击转场全程（眩晕/泡泡/放大）、外观页主题卡移除+模式切换、
classic 老数据迁移（设备 settings.theme 可能为 classic）。

### 会话 20 补充 5 · 开屏 v3 门扉 + 主题单轨化 真机验证通过（2026-09-05，EXTRACT 27）

**门扉（51.7fps 录屏 gate_rec3.mp4 + 截图）**：淡入构图 → 进度条自左向右走满 →
「沉 溺」按钮浮现+呼吸等待 → 点击后 **眩晕+泡泡+中心放大转场真机成立**
（gate3_c.png 45 帧逐帧：微晃→泡泡环升起→构图以按钮为轴放大、blur 失焦加深→
聊天页从溶解的开屏背后浮现→完全抵达）；转场后数据完好。
**公告弹层被压**（modal-suppressed 截图，z-200 持续生效）。

**主题单轨化（patch 028）**：外观页「界面主题」卡消失、外观预览双卡全为暖幕手记
（亮/暗模式切换保留且工作）、模式卡恒显、界面字体/对话字号不变、
「经典主题为上游原版配色」过时提示句清除（appearance-single 截图）；
classic 老数据强制迁移 luzzy（本机 settings.theme 经历 classic→luzzy 无感迁移）。

**过程踩坑（再次入册）**：EXTRACT 25 首装消费掉版本号后，挂载修复又改了 assets
却未再 bump → install -r 跳过解压 → 设备 filesDir 仍是旧 index.html → 转场无反应
（run-as grep 设备侧文件定位）。教训：**同一构建周期内多次改 assets 必须逐次 bump**，
且装机前用 run-as 校验设备侧文件内容。EXTRACT 现 27。

**收尾状态**：verify-markers 60 PASS / 0 FAIL；全部门禁与真机验证通过；
EXTRACT 27 debug 包=日常包（数据保留）。

### 会话 20 补充 6 · CHANGELOG 自动同步 + 资产签名根治 + FAB 错位全案告破（2026-09-05）

**① 关于页 CHANGELOG 自动同步（用户指令「万一忘记更新怎么办」）**：
- genChangelog Gradle 任务（preBuild 挂钩，inputs/outputs 增量，CC 兼容）——构建即从
  仓库根 CHANGELOG.md 重生成应用内数据；node 不可用时降级告警不阻塞构建；
- gen-changelog.mjs 新增 --check（同步=0 / 过期=3）；verify-markers 新增 R3-changelog-sync
  门禁——本轮实测：CHANGELOG 变更未 gen 时 R3 FAIL 拦截 ✓、gen 后 61 PASS ✓；
- 文档：HARD_REQUIREMENTS 规定 5 增补注记（已按规矩在 CHANGELOG 声明）。

**② 资产签名自动解压（根治三踩的 EXTRACT 坑）**：
- build.gradle.kts assetSignature()（文件数+总大小+最新 mtime）→ BuildConfig.ASSET_SIGNATURE；
- AssetExtractor 改签名比对（.extracted_sig），EXTRACT_VERSION 手动机制退役；
- 实测：CSS/index 改动后仅 install+launch 即自动重解压 ✓（run-as 双 grep 验证）。

**③ 关于页置顶按钮错位全案告破（用户报告：按钮错位/箭头偏移）**：
- **根因**：luzzy-ext.js 品牌注入泛匹配选择器 `[class*="about"]` 取末位——patch 024 的
  置顶按钮（about-top-fab 类）成为末位匹配 → 72px 灰底品牌卡被 append 进 44px 按钮内部
  → flex 双 item 挤偏箭头（CDP arrowOffset -36px）+ 品牌卡溢出成「幽灵窄条」；
- **修复**：注入锚点改显式 `.about-view` + 已注入实例迁移逻辑；FAB 定位 v4 = fixed +
  零动画零过渡（本机 WebView 分数 DPR 3.25 合成层失效规避）；品牌基线串 1.8.9→1.9.0
  （LuzzyBridge.UPSTREAM_VERSION + app.js 回退标签，同步期遗漏）；
- **真机终验**：CDP arrowOffset [0,0]、按钮钉右下角标准位（311,691）、幽灵卡消失
  （verify-v123-fab-final.png）；
- **过程代价（诚实记录）**：CSS 文件被多轮正则补丁搞成 v1/v3 双段弗兰肯斯坦（孤儿
  keyframes 片段吃掉后续规则、旧 transform 残留），最终整体重写 FAB+splash 区段并
  按行手术清除 v1 残段；期间多次构建失败（it.logger/Function2/standardOutput 编译错）
  后带病安装旧 APK 造成验证假象——**构建守卫必须先于安装判定**已入流程。

### 会话 20 补充 7 · v1.2.3 正式版发布（2026-09-05）

- versionCode 10 / versionName 1.2.3；CHANGELOG 定稿（状态转正式版 + 构建结果）；README
  徽章 + 版本对应表 + 版本规划表三处更新；gen-changelog 重生成（R3 门禁全绿 61 PASS）；
- assembleDebug + assembleRelease 双构建；debug 包（日常数据包）装机冒烟：门扉 → 沉溺 →
  关于页显示「v1.2.3 · 基于 RP-Hub 1.9.0 · 正式版已发布（2026-09-05）」✓；
  release 包未装真机（避免数据分裂，仅分发）；
- commit fe760038 推送；GitHub Release v1.2.3 创建并附三件套 APK
  （arm64-v8a / universal / x86_64，各 17.3MB）：
  https://github.com/LuzzyMeow/LuzzyRP/releases/tag/v1.2.3
- 遗留：无（发版流程 §3.4 全项完成；下一版 v1.3.0 候选见 README 规划表）。

---

## 会话 21 · v1.3.0 性能与功能小版本（2026-09-05，探索阶段）

**任务（用户指令，四项）**：①手机端性能优化（高刷未跑满/聊天卡顿/发送键点击热区漂移/气泡与流式卡顿/开屏掉帧）②内置供应商仅保留 DeepSeek 且支持编辑内置供应商 ③关于页「基于 RP-Hub」行去掉版本号 1.9.0（防同步遗忘）④思考卡片内新增「记忆嵌入」独立节点。

**开始状态**：main e56693b0 干净；v1.2.3 正式版已发布（versionCode 10）；verify-markers 61 PASS / 0 FAIL。

**探索安排**：三路子代理并行（性能管线 / 内置供应商体系 / 思考卡片×记忆链路）；关于页版本号串已主会话定位（index.html:2138 + app.js:703/711/715/11080 + luzzy-ext.js:29/62 + luzzy-bridge.js:48 + LuzzyBridge.kt UPSTREAM_VERSION）。规定 9：4 项设计 SKILL 主文档已复读；豁免判定=性能修复（机械操作）+ 开屏迭代（方向 B 已选定）+ 节点/编辑按钮（组件级新增循 DESIGN.md token）。

### 会话 21 实施 · 四需求全量落地（2026-09-05，patch 029-034）

**探索结论（三路子代理 + 主会话，行号级锚点）**：
- 性能四根因：①流式每 60ms 对增长全文跑「正则→marked→DOMPurify→DOMParser→iframe」
  全链 O(n²) 重算 + v-html 全量替换（runtime-services.js:57/624-682）；②玻璃层爆炸——
  luzzy-theme.css 33 处 backdrop-filter 重放上游移动端 kill-switch + `.glass-stabilize`
  给每气泡强挂 will-change（styles.css:1913，非法动画值仅起促层作用）+ scroll-reveal
  三族不回收 → 渲染窗口常驻 40-60 候选层 @3.25（单气泡层 ~11MB）；③发送键热区漂移 =
  FAB 同族（输入岛玻璃+transition-all+键盘位移，DPR 3.25 合成层绘制错位）；④开屏
  lspDiveZoom filter:blur(0→9px) 全屏层逐帧重栅格 ≈3510×7800 像素/帧。壳层（WebViewSetup.kt）
  无性能负配置，不动 Kotlin。
- 供应商：唯一数据源 core-utils.js:946-971；锁定=模板反向查询；**sta1n 深度绑定
  defaultApiProviderId 链路（直删白屏）**；扩展层被「解构捕获+freeze+闭包」三重隔离
  挡死 → 必须走 patch；工坊同步已有用户商 remap 兜底（app.js:1059-1066）。
- 记忆节点：getTimelineSteps（app.js:8560 区）单点驱动 + 模板通用 step 渲染（零模板改动）；
  **时序约束=检索/嵌入早于 assistant 消息懒创建 → 只能创建时盖戳、渲染期读戳**；
  识别走 window.RPHubContextUtils.isVectorMemoryRecallContent（patch 016 生态）。
- **TDZ 陷阱（实抓）**：memorySettings 定义于 :1386、MODEL_REF_FIELD_LABELS 于 :4120，
  均晚于 normalizeApiProviderSettings() 调用点 :980——迁移函数不得引用（注释入册）。

**实施（用户拍板 D1=高频面退实底 / D2=「记忆召回」不强制出卡 / D3=STA1N 无损迁移）**：
1. patch 032（commit 124f6daa）：STREAM_RENDER_INTERVAL 60→120ms（三协议共用 :57）+
   renderMarkdown `options.cache=false` 流式 LRU 旁路（唯一调用方=index.html 流式分支）。
2. patch 034（commit 509f6f80，ext/luzzy-theme.css）：D1 高频面退实底（气泡/typing/
   输入岛/侧栏 blur 归零 alpha 0.97，:has 流式加厚同步失效；思考卡/模态/工具条保留磨砂）+
   glass-stabilize/scroll-reveal 三族 will-change→auto + lspDiveZoom 去 filter:blur
   （泡泡脱离 filter 父层）。
3. patch 033（commit e2cec220，index.html 字节级）：输入区 transition-all→`transition-[bottom]`
   定向、输入岛去过渡、发送/中止按钮 `transition-[background-color,box-shadow,transform,opacity]`。
4. patch 029（commit 3a4b8908）：core-utils 精简仅 DeepSeek（editable）+ 默认商切 deepseek；
   app.js 六处（allApiProviders override 合并 / migrateRemovedBuiltinProviders 老用户迁移
   （URL/Key/模型槽位引用保留）/ normalize 容器 / apiUrl watch 直编 override / 编辑器内置
   分支 id 锁定 + 保存写 apiProviderOverrides）；index.html 编辑按钮+URL 框放开+id 置灰；
   novel 兜底副本同步；**patch 023 随条目退位（校验项 029 接管）**。
5. patch 030（commit c170b5db）：关于页「基于 RP-Hub 二次开发」固定文案（index 去插值 +
   app.js upstreamVersionLabel 整链移除 + luzzy-ext 页脚同改；UPSTREAM_VERSION 保留仅诊断）。
6. patch 031（commit b93f333d）：extractMemoryRecallStamp 盖戳（片段数+相似度区间，
   失败静默降级）+ getTimelineSteps 时间线首位「记忆召回」thinking 型节点。
7. 门禁与登记链：verify-markers manifest 023×2 退役→029 接管 + 新增 029-034 十项
   （**71 PASS / 0 FAIL**）；实体再生成 5 枚（007-029-novel / 009-029-core-utils /
   012-031-app / 012-032-runtime / 012-033-index，pre 哈希与基线逐一对齐，逆向
   --check 8/8 PASS）；tools/patches/README 登记 028（补记，会话 20 遗漏）+ 029-034；
   gen-changelog 重跑（R3 全绿）；CHANGELOG v1.3.0 章节 / DESIGN.md（性能档位玻璃表 +
   记忆节点 + 编辑内置商组件行 + Motion 去 blur + Don'ts 高频玻璃禁令）/ README 规划表 /
   AGENTS §4.2 表 001-034 + §9 快照 / HARD_REQUIREMENTS 范围号同步。
8. **apply-patches 011 重放块退役（意外发现）**：SKIP 检测锚「界面主题」被 028 移除后
   失配 → 落入重放分支 IndexOf 崩溃（会话 20 补充 4 后从未重跑 apply-patches 故未暴露）。
   按 003 先例退役（倒置条件 SKIP + 退役注释），重跑全 SKIP。

**过程抓错（诚实记录）**：①index.html 两次被 Edit 工具翻转混合行尾（3462/3461、3467/3464
整文件伪 diff）——两次均 git checkout 回滚后字节级脚本重放（numstat 恢复 2/1、8/5 行级），
坑表规则第三次验证有效；②patch 032 首次编辑把 HTML 注释插进 div 标签属性间（非法结构），
自查发现后移至标签外；③PS 5.1 无 `??` 运算符、`.Split(string)` 按字符拆分——脚本写法坑两次。

**遗留 / 下一步**：
- 真机回归（小米 df97f3c4）：debug 包 install -r + §6.2 全量 + 性能专项（流式 10s
  Performance 录制前后对比 / elementFromPoint 热区偏移向量 / 开屏逐帧）+ D1 玻璃档位
  亮暗目测 + 供应商迁移/DeepSeek 编辑/记忆节点专项走查；
- 老用户迁移注意：激活商=STA1N/OpenRouter/SiliconFlow 时自动转用户商（Key 保留）；
  记忆嵌入引用不改写（TDZ 约束），指向已退内置商时 patch 026 显式报错兜底（重选即可）；
- v1.4.0 候选（本版明确不做）：styles.css 低频蓝收编、向量阈值滑杆、壳层 textZoom
  （理由：零症状贡献/归因污染/独立设计决策，见会话 21 对话记录）。
