# 更新日志（CHANGELOG）

> LuzzyRP 遵循语义化版本（`MAJOR.MINOR.PATCH`）；`x.y.0` 视为稳定版并附 APK。
> 格式：`### vX.Y.Z — 标题` + 「新增 / 优化 / 修复 / 注意事项」分类要点 + 构建结果与 versionCode。---

### v0.2.0 — 设计重构 · 角色卡生态 · 预设与档案

按用户检阅反馈全面迭代：4 项设计 SKILL 方法论落地（硬性规定 13）、角色卡生态补全、菜单重构与启动直达。

**新增**

- **设计体系落地**：4 项设计 SKILL（huashu-design / open-design / awesome-design-md / ui-ux-pro-max-skill）全部存档至 docs/skills/ 并写入硬性规定 13；新增仓库根 `DESIGN.md` 设计契约与 `docs/AGENT-GUIDE.md` 开发指南。
- **图标黑边修复**：815 枚图标管线升级——索引色 PNG → RGBA 清洗（透明区杂色归零）+ 字形 bbox 归一化居中（全库视觉大小统一）+ 透明边缘平滑；启动图标黑晕清除、底色改 AuroraPink。
- **启动直达**：应用启动自动打开上次会话；首次启动默认创建并进入「鹿溪」对话。
- **角色卡生态**：手动新建角色卡；头像选择（自动 1:1 裁剪）；聊天背景图（默认回落头像）+ 透明度；`<CUT>` 分割多条开场白（多气泡输出）；世界书整体并入角色卡二级页。
- **世界书编辑器**：条目全字段管理——条目名称/内容/激活策略（常驻+关键词，可叠加）/激活概率 0-100/注入位置（↑Char、↓Char、↑EM、↓EM、@Depth×system/user/assistant + 深度）/独立启停；SillyTavern 世界书 JSON 导入（外部条目默认启用）。
- **提示词预设**：预设 = 名称 + 条目列表；条目独立启停/命名/角色（SYSTEM/USER/ASSISTANT）/注入位置（相对 + @Depth×3）/内容编辑；单选激活注入 system。
- **用户档案**：头像/名字/身份，注入 system 稳定前缀。
- **思考深度自适配**：按模型 id 检测家族自动给出正确档位与请求字段——DeepSeek（none/high/max，官方三档）、GLM（thinking.type / 5.3+ 仅 reasoning_effort）、GPT（reasoning_effort）、Opus（adaptive thinking + output_config.effort，最高 xhigh）；模型级温度/深度覆盖全局。
- **供应商管理增强**：新增/编辑/删除供应商（含内置）；新增模型表单全字段（id/显示名/上下文长度/最大输出/温度/思考深度）；**单位换算**（1024000、1024K、1M、1024k、1m 均可填写）；字段自检（即时校验 + 错误提示）；生成参数并入供应商页。
- **应用日志**：记录用户步骤/模型步骤（请求组装、生成完成、失败堆栈）/工具轮次，内存实时查看 + JSONL 落盘（保留 3 天）+ JSON 导出（SAF 自定义路径）+ 系统分享；关于页内嵌 CHANGELOG 渲染。

**重构**

- 菜单精简为：聊天 / 角色卡 / 预设 / 用户档案 / 设置；历史会话与搜索并入聊天页顶栏；世界书并入角色卡；长期记忆并入设置（记忆设置页）；移除收藏入口。

**数据**

- Room v1 → v2 手写迁移（worldbook_entries.depthRole 列 + prompt_presets 表），老用户数据无损升级。

构建：`assembleRelease` BUILD SUCCESSFUL（R8 minify）· versionCode 3


### v0.1.1 — 首航热修：模拟器实测三缺陷修复

v0.1.0 在 Android 15 模拟器全流程实测（安装 → 启动 → 新建会话 → 开场白渲染 → 发送/错误路径）后发现并修复 3 个缺陷。

**修复**

- **启动崩溃**：`painterResource(R.mipmap.ic_launcher)` 无法加载自适应图标（AdaptiveIconDrawable 既非 Vector 亦非位图，首帧即抛 IllegalArgumentException）→ 品牌 logo 以 PNG 资源（`luzzy_logo`）入 drawable，全部 Compose 引用替换。
- **顶栏遮挡**：edge-to-edge 下未处理 WindowInsets，首页/聊天页顶栏切入状态栏 → 全局 Shell 增加 `systemBarsPadding()`。
- **种子数据缺失**：内置角色卡「鹿溪」/ 其世界书 / 正则内置预设的 `ensure*()` 从未被调用 → 应用启动时幂等初始化（新建会话现在正确显示「与鹿溪 的故事」并携带完整开场白）。

**优化**

- 发送后无可用供应商时以错误条提示（errorContainer），不再静默。

构建：`assembleRelease` BUILD SUCCESSFUL（R8 minify）· versionCode 2


### v0.1.0 — 首航：全新引擎 · 全新起点

LuzzyRP 的第一个正式版本。全新代码库，以 rikkahub 已验证的流式架构为实现蓝本，将「真流式输出」与「Agentic 闭环」一次锚定到位。

**新增**

- **真流式输出引擎**：callbackFlow + OkHttp SSE EventSources + `trySend` 逐 event 零节流 + `readTimeout = 10 MINUTES`；思考卡片与正文气泡均以 1 字 = 1 次更新直连单一真源 `MutableStateFlow<Conversation>`。
- **三协议供应商层**：OpenAI 兼容（一等公民，覆盖 DeepSeek / 火山方舟 CodingPlan / GLM / OpenRouter 及任意兼容端点）、Anthropic messages API、Google Gemini SSE；主机思考参数自适应（thinking.type / reasoning_effort / reasoning.effort）。
- **Agentic 闭环**：`maxSteps = 256` 硬上限、工具结果**原地回填**（绝不新建 TOOL 消息）、三 break 条件、`ToolApprovalState` 五状态机审批与续跑、禁用 `tool_choice = required`；RP 模式两阶段闭环 `maxLoops = 3`（工具轮次上限 → 阶段二基于结果生成正文）。
- **文本标签兜底**：`<tool_calls>` 标签流式解析器（GLM-5.2 等无原生 function calling 模型），支持跨增量切分安全、行式/JSON 数组双格式、截断尽力解析，协议文本永不上屏。
- **内置提示词强化**：Agentic 行为协议（>2 轮思考、>1 次主动工具调用）注入稳定前缀；NSFW 块占位文件（由用户手动填写，任何逻辑不可触碰）。
- **角色卡生态**：SillyTavern v2/v3 PNG 导入导出（自研 PNG tEXt chunk 读写器）+ JSON 导入导出、头像 1:1 裁剪、内置默认角色卡「鹿溪」（只读保护）。
- **世界书三策略召回**：常驻 / 关键词（含递归扫描与概率 roll）/ 向量（sqlite-vec Top-K + 相似度阈值）；注入位置分层与 KV 缓存结构对齐。
- **长期记忆（ACE）**：Execute（Top-K 注入）→ Reflect（LLM 反思 helpful/harmful/neutral 计数）→ Update（提取新事实 + 余弦 ≥0.92 嵌入去重）+ 评分淘汰；管理页可启停/删除。
- **三级摘要**：A 级每轮 ≤50 字（盲续五要素）/ B 级每 10 轮合并 ≤10 条 / C 级每 50 轮固化永久；异步执行不阻塞回复。
- **数据层**：Room v1（9 实体、8 DAO、WAL、schemas 导出、AutoMigration 就绪）+ DataStore 设置单一真源 + sqlite-vec 向量索引（维度自适应）。
- **UI（Aurora Dual）**：Material 3 Expressive + MaterialExpressiveTheme；AuroraPink/AuroraViolet 双生色板（零硬编码色值）；三态动效令牌（进入 300ms / 交互 150ms / 退出 195ms）；815 枚 game-icon-pack 图标 + lobe lucideExtra 37 枚向量图标的生成管线；阿里巴巴普慧体 3 + AlibabaSans 中西文逐字符混排（其余文字回退系统字体）。
- **页面**：首页（会话列表/置顶/搜索/抽屉）、聊天页（思考卡片时间线 + 工具卡片内嵌 CoT 列 + 双色气泡 + 流式跟随 + 回到底部 + 发送/停止切换）、角色卡库与详情、世界书、长期记忆、收藏、历史会话、设置族（供应商/生成参数/外观/关于）。

**优化**

- KV 缓存命中：消息序列化稳定化（固定字段序、紧凑 JSON、时间戳不进前缀）+ 三层提示组装（稳定前缀系统消息 → append-only 历史 → 尾部动态块）。
- 分支会话模型：重新生成产生兄弟节点，selectIndex 当前分支指针，历史完整保留。
- ABI 拆分（arm64-v8a / x86_64 / universal）+ R8 minify + shrinkResources。

**守护**

- 单元测试全绿：流式合并代数、标签兜底解析、PNG tEXt 往返、KV 前缀稳定性 Golden（同历史两次组装逐字节相等）、Agentic 循环（原地回填/审批续跑/两阶段收口）。
- 不变性落点文件均带 `[INVARIANT-STREAMING]` / `[INVARIANT-AGENTIC]` / `[INVARIANT-KV]` / `[INVARIANT-NSFW]` 守护注释块。

**注意事项**

- 使用前请在「设置 → 供应商」配置 API Key（内置 DeepSeek 与火山方舟 CodingPlan 档案，均可编辑）。
- 向量召回（记忆/世界书）需在设置中另行配置嵌入模型；未配置时自动退化为关键词召回。
- NSFW 提示词块为占位状态，由用户手动填写（`NsfwBlock.kt`），项目本身不做任何内容过滤。

构建：`assembleRelease` BUILD SUCCESSFUL（R8 minify）· versionCode 1
---

### v0.3.0 — Aurora v2 主题重制 · 三态动效体系 · 图层设计

以 4 项设计 SKILL 为方法论本体完成的**可见视觉全面重制**（ui-ux-pro-max 检索基线 + huashu 动效纪律 + open-design DESIGN.md 契约 + awesome-design-md 结构范式）。

**主题重制（主题令牌系统 v2）**

- **AuroraColor v2**：Material3 方案完整落位——surfaceContainer 全族五档色阶、outline/outlineVariant、inverse 系、scrim、surfaceDim/Bright；亮/暗/**AMOLED** 三套独立方案（AMOLED 纯黑非暗色微调）。
- **极光渐变系统（AuroraBrush）**：Pink→Violet 主渐变 / 反向渐变 / 画布顶部氛围微渐变——发送按钮、品牌标题、主行动统一取用（渐变为品牌资产，禁止业务自拼）。
- **LuzzyElevation 图层令牌**：五层深度体系（画布→卡片 2dp→悬浮 6dp→弹窗 12dp+scrim45%→Toast 16dp）。
- **LuzzyIconSize / LuzzyCorner 令牌**：图标 16/20/24/32 四级语义尺寸、圆角五档——全项目统一，禁止随机值。
- **MotionTokens v2**：场景化动效规格（页面 slide¼+fade 300/195 快出慢入、弹窗 scale 0.92 spring、Sheet、列表 stagger 20ms、按压 scale 0.98 布局不跳动、展开 195）+ reduced-motion 替代规格。
- **Typography/Shapes v2**：字阶微调 + LuzzyCorner 接入 Material Shapes。

**组件库 v2**

- **AuroraTopBar**：统一顶栏（56dp 高/图标 24dp/单行省略/操作位规范），聊天/世界书/预设/预设编辑/用户档案/记忆 全部换装；新增 TopBarAction 规范按钮。
- **LuzzyDialog**：统一弹窗（scale+fade 三态进出、Layer3、正文左对齐、danger 变体）。
- **EmptyState**：统一空态（Hero 图标+标题+引导，spring 入场）。
- **AuroraSurface v2**：按压缩放 0.98（布局不跳动）+ 色彩过渡 + 阴影抬升三态。

**逐页重制**

- **聊天页**：背景图层正确分层（卡背景>头像，透明度直调）；气泡 v2（用户=极光淡染渐变 / AI=纸面卡 Layer1 阴影+描边）；输入栏渐变发送键；顶栏半透明融合。
- **首页**：时段问候 + 极光渐变品牌标题；会话空态引导。
- **页面转场 v2**：fade+slide 六分之一屏、缓动曲线化（快出慢入不对称节奏）。

构建：`assembleRelease` BUILD SUCCESSFUL（R8 minify）· versionCode 4

