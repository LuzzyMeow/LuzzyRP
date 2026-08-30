# 更新日志（CHANGELOG）

> LuzzyRP 遵循语义化版本（`MAJOR.MINOR.PATCH`）；`x.y.0` 视为稳定版并附 APK。
> 格式：`### vX.Y.Z — 标题` + 「新增 / 优化 / 修复 / 注意事项」分类要点 + 构建结果与 versionCode。
---

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
