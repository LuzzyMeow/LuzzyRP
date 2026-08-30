# LuzzyRP · v0.1.0 详细开发计划

> 项目宗旨：**"每次对话，都像一本有你的小说。"**
> LuzzyRP 是一款移动端 AI 角色扮演应用，将 LLM 的推理能力与角色扮演（RP）规则/背景提示词深度融合。
> 本文档是 v0.1.0 的完整实施计划，由项目负责 Agent 维护；任何偏离本计划的决策必须记录进 `docs/WORKLOG.md`。

---

## 0. 决策记录

| # | 决策 | 说明 |
|---|------|------|
| 1 | 全新重建 | LuzzyRP 为全新代码库，版本线自 **v0.1.0 / versionCode 1** 起，**不沿用旧仓库（LuzzyMeow/Luzzy-RpTRPG）v0.x 版本历史与发展路线**。`docs/task/` 内 17 份历史文档仅作细节规格参考（正则编辑器字段、token 统计格式、ACE 结构等），不作为路线图；里程碑按本计划全新制定。 |
| 2 | 实现蓝本 | `docs/rikkahub-master/`（流式 SSE、GenerationHandler、Room、Navigation3 均为已验证实现，直接移植模式）；设计基线沿用 Aurora Dual 令牌体系（`docs/audit/design-audit-v0.10.1.md`）。 |
| 3 | 仓库 | `LuzzyMeow/LuzzyRP`（公开），许可证 CC BY-NC 4.0；git 操作一律走 SSH（`git@github.com:LuzzyMeow/LuzzyRP.git`）。 |
| 4 | 旧仓库删除 | `LuzzyMeow/Luzzy-RpTRPG` 在完成本地备份（`docs/archive/`，gitignore）且新仓库首推成功后删除。gh token 缺 `delete_repo` scope 时需 `gh auth refresh -s delete_repo`（设备码授权）或用户手动删除。 |
| 5 | 字体打包 | 核心 6 字重：PuHuiTi-3 55-Regular / 65-Medium / 85-Bold + AlibabaSans Regular / Medium / Bold（APK ≈ 30MB）。非简中/英文文本回退系统字体。 |
| 6 | 版本范围 | v0.1.0 = 核心 RP 对话全功能；TRPG 模式列入 v0.2.0 专项（§14）。 |

## 1. 硬性规定（与仓库根 `HARD_REQUIREMENTS.md` 同步）

1. **禁止破坏流式输出**：所有模式的模型输出必须是模型原生流式，1 字 = 1 次更新，非模拟请求、严格逐字。
2. **禁止破坏 Agentic 模式**：所有模式支持多轮思考与多轮工具调用；除被动工具外，每模式至少 >2 轮思考、>1 次主动工具调用；必须强化/更新内置提示词。
3. **NSFW 提示词不可触碰**：禁止任何审查、过滤、改写；当前为占位（`NsfwBlock.kt`），由用户手动填写。
4. **代码内详细注释**：涉及不变性的代码必须带 `[INVARIANT-xxx]` 注释块（禁止修改范围 + 允许修改范围 + 原因），让后续开发 Agent 明白需求。
5. **Android 原生 Kotlin + Compose**，参考成品：rikkahub / cherry-studio / RP-Hub / lobehub / SillyTavern。
6. **图标仅限** `docs/game-icon-pack` 与 `docs/lobe-ui-master` 提取，禁止自绘；内置字体仅限 `docs/AlibabaPuHuiTi-3` 与 `docs/AlibabaSans`；所有新 UI 具备进入/交互/退出三态丝滑动画。
7. **禁止遗留占位符**：以最完整、功能最全面为质量标准，不得简化。
8. **KV 缓存命中**：新功能不得破坏模型输入缓存命中机制（前缀稳定化 + Golden 测试守护）。
9. **工具/MCP/内置提示词更新** → 同步 CHANGELOG + agentic 请求阶段内部工具提示词（如需）。
10. **新版本同步** `CHANGELOG.md` 与 `README.md`（美观且极其详细）。
11. **工作区整洁**：清理冗余、文件分类。
12. **发布**：`android` 资源同步最新构建产物 → 编译 APK → 推送远程 → 按旧版 release 排版写 Release（仅稳定版附 APK）。

## 2. 文档体系

| 文件 | 作用 |
|------|------|
| `docs/PLAN-v0.1.0.md` | 本计划（完整扩展版：实体字段 / 接口签名 / 路由表 / 令牌表） |
| `docs/WORKLOG.md` | 工作日志：每次会话追加「日期 / 完成 / 决策 / 遗留 / 下一步」 |
| `HARD_REQUIREMENTS.md` | 12 条硬性规定 + 6+6 不变性，逐条标注守护落点 |
| `docs/INVARIANTS-CHECKLIST.md` | 发版前逐项自检表 |
| `CHANGELOG.md` / `README.md` / `LICENSE` | 版本记录 / 项目门面 / CC BY-NC 4.0 |
| `docs/archive/` | 旧仓库源码备份（gitignore，仅本地参考） |

## 3. 工程骨架与版本目录

```
LuzzyRP/
├── app/                   # 主应用：UI、ViewModel、Room、DataStore、DI、ChatService
├── core/model/            # 纯领域模型（无 Android 依赖，纯 Kotlin）
├── core/ai/               # AI SDK：Provider、SSE 流式、三协议、标签兜底解析
├── core/common/           # 工具：Call.await()、JSON 单例、PNG tEXt chunk 读写
├── tools/icon_pipeline.py # 图标资产生成脚本（Python）
├── docs/                  # 工作资料（规划/日志/归档/参考资料）
├── gradle/libs.versions.toml
├── settings.gradle.kts
├── build.gradle.kts
└── HARD_REQUIREMENTS.md
```

- 构建：Gradle 9.4.1 wrapper · AGP 9.2.1 · Kotlin 2.4.0（`jvmToolchain(21)`，字节码 target 17）· KSP 2.3.9（坐标不可解析则回退 2.3.4 并记录 WORKLOG）。
- `libs.versions.toml` 全目录：

| 库 | 版本 |
|----|------|
| agp | 9.2.1 |
| kotlin | 2.4.0 |
| ksp | 2.3.9 |
| compose-bom | 2026.05.01 |
| material3 | 1.5.0-alpha21（Expressive） |
| navigation3 | 1.1.2 |
| lifecycle-viewmodel-navigation3 | 2.10.0 |
| koin-bom | 4.2.1 |
| okhttp | 5.3.2（+ okhttp-sse） |
| room | 2.8.4 |
| datastore | 1.2.1 |
| kotlinx-serialization | 1.11.0 |
| coroutines | 1.11.0 |
| coil | 3.x |
| sqlite-vec（android） | 最新稳定 |
| paging | 最新稳定（配 Room 2.8.4） |
| junit | 4.13.2 / kotlin-test |

- SDK：compileSdk 37 · minSdk 26 · targetSdk 37；abiSplits arm64-v8a + x86_64；applicationId `com.luzzymeow.luzzyrp`；R8 minify 开启（keep kotlinx-serialization / Room）。

## 4. 资产管线（规则 6 落实）

### 4.1 图标
- 源：`docs/game-icon-pack/间距/256像素/白色/`（815 枚实心单色 PNG，12 类）。
- 脚本 `tools/icon_pipeline.py`：
  1. 扫描 → 复制 `app/src/main/res/drawable-nodpi/ic_game_<拼音类>_<序号>.png`（资源名只允许小写字母数字下划线）。
  2. 生成 `core/…/GameIcons.kt`：12 类注册表，每项含资源 ID 引用 + 中文原名元数据（`IconMeta(name, category)`）。
  3. 生成 `LuzzyIcons.kt`：语义别名表（Send/Delete/Edit/Settings/Star/Brain/Book/Map/Sword… → 具体图标），UI 层只允许用 LuzzyIcons。
- `docs/lobe-ui-master/src/icons/lucideExtra/` 40 枚：解析 `createLucideIcon('Name', [path…])` 中的 SVG path 数据，机械生成 VectorDrawable XML（复制路径数据，非手绘）。
- `docs/brand-logos/`：luzzy.png → 自适应启动图标前景/背景 + 通知 small_icon（白色单色版）；deepseek/kimi/trae/zai → 供应商标识。

### 4.2 字体
- `res/font/puhuiti_regular.ttf` / `puhuiti_medium.ttf` / `puhuiti_bold.ttf` / `alibaba_sans_regular.ttf` / `alibaba_sans_medium.ttf` / `alibaba_sans_bold.ttf`。
- `LuzzyMixedFontFamily`：按字符分流——CJK → PuHuiTi；拉丁字母/数字/半角标点 → AlibabaSans；其余（韩文/日文假名等）→ 系统默认回退。

### 4.3 主题令牌（Aurora Dual）
| 令牌 | 值 |
|------|-----|
| AuroraPink | #FF6EC7 |
| AuroraViolet | #B57BFF |
| 亮底 Canvas | #FAF7F2 |
| 暗底 Canvas | #0E1116 |
| Spacing | 4pt 栅格（4/8/12/16/24/32） |
| 动效 | 进入 300ms / 交互 150ms / 退出 195ms |
| 表面 | `AuroraSurface` 引擎，**禁 Modifier.blur** |

全项目零硬编码色值（`AuroraColor.kt` 唯一色源）；MaterialExpressiveTheme + MotionScheme.expressive()。

## 5. 领域模型层（:core:model / :core:common）

### 5.1 消息模型
```kotlin
@Serializable
data class UIMessage(
    val id: Uuid,
    val role: Role,                    // system / user / assistant
    val parts: List<UIMessagePart>,
    val createdAt: Instant,            // 不参与 KV 前缀序列化
)

@Serializable
sealed interface UIMessagePart {
    @Serializable data class Text(val text: String) : UIMessagePart
    @Serializable data class Reasoning(
        val thinking: String, val signature: String? = null, val durationMs: Long? = null,
    ) : UIMessagePart
    @Serializable data class Tool(
        val toolCallId: String, val toolName: String,
        val input: JsonElement, val output: List<UIMessagePart>? = null,
        val approvalState: ToolApprovalState = ToolApprovalState.Auto,
    ) : UIMessagePart
    @Serializable data class Image(val data: String, val mime: String) : UIMessagePart
}

@Serializable
data class MessageChunk(
    val id: String, val model: String,
    val choices: List<UIMessageChoice> = emptyList(),
    val usage: TokenUsage? = null,
)
@Serializable
data class UIMessageChoice(val index: Int, val delta: UIMessage? = null, val message: UIMessage? = null, val finishReason: String? = null)
```

### 5.2 合并代数 `List<UIMessage>.handleMessageChunk(chunk)`
- delta.role 与末消息一致 → 末消息上合并；否则新建消息追加。
- Text 增量 → 追加至末 Text part（无则新建）。
- Tool 增量 → 按 `toolCallId` 定位，toolName/input 字符串拼接（OpenAI delta 累积语义）。
- Reasoning 增量 → 合并至末 Reasoning part。
- finishReason → 记录收口。

### 5.3 业务模型
- `Conversation(id, title, nodes, currentNodeId, cardId?, worldbookIds, regexIds, uiTemplateId, enableMemory, contextConfig, summaryCounters)`。
- `MessageNode(id, parentId, selectIndex, messages)` 分支树（重 roll 产生兄弟节点）。
- `CharacterCard`（SillyTavern v2/v3 全字段 + 本地扩展）：name, avatar, description, personality, scenario, firstMes, altGreetings[], systemPrompt, postHistoryInstructions, creatorNotes, tags[], creator, characterVersion, stSpecVersion, stRawJson, source(builtin/imported/created), readonly, worldbookId?, regexIds[], uiTemplateId?, chatBackground{path,opacity,blur}。
- `WorldbookEntry(strategy, keys[], keysSecondary[], content, comment, position, depth, order, probability, constant, recursion, useRegex, enabled, vectorIndexed)`；strategy: constant / keyword / vector（可组合位标志）。
- `RegexScript(name, find, replace, scopes[], timing[], minDepth, maxDepth, enabled, preset?)`。
- `MemoryItem(content, category, helpful, harmful, neutral, active, embedding?, createdAt, updatedAt, lastRetrievedAt, sourceConversationId?)`。
- `SummaryItem(level: A/B/C, roundIndex, content, embedding?)`。
- `ToolApprovalState`：sealed（Auto / Pending / Approved / Denied(reason) / Answered(answer)）。
- `ProviderSetting` sealed：OpenAICompatible(id, name, baseUrl, apiKey, models[])；内置档案：DeepSeek / ArkCodingPlan / Custom。

### 5.4 :core:common
- `Call.await()`（suspendCancellableCoroutine 桥接 OkHttp）。
- `JsonInstant`：kotlinx-serialization 单例，`encodeDefaults = true`，字段序稳定（KV 守护）。
- `PngTextChunkReader` / `PngTextChunkWriter`：手工解析/写入 PNG chunk（长度+类型+数据+CRC32），支撑 `chara` 字段导入与导出。

## 6. AI 层（:core:ai）—— 真流式 6 不变性

```kotlin
interface Provider<T : ProviderSetting> {
    suspend fun listModels(setting: T): List<Model>
    suspend fun generateText(setting: T, messages: List<UIMessage>, params: TextGenerationParams): MessageChunk
    fun streamText(setting: T, messages: List<UIMessage>, params: TextGenerationParams): Flow<MessageChunk>
    suspend fun generateEmbedding(setting: T, texts: List<String>): List<FloatArray>
}
```

**[INVARIANT-STREAMING] 六大不变性**（守护文件：`core/ai/…/openai/ChatCompletionsAPI.kt` 等三协议实现 + `di/DataSourceModule.kt`）：
1. `callbackFlow` + `okhttp3.sse.EventSources.createFactory(client).newEventSource(request, listener)`；
2. `onEvent → trySend(chunk)` 逐 event 零节流（禁止缓冲/合并/定时 flush）；
3. `awaitClose { eventSource.cancel() }`；
4. OkHttpClient `readTimeout = 10 MINUTES`（DI 集中定义）；
5. SSE 建立后取消 call timeout（长响应不被整体超时打断）；
6. `MutableStateFlow<Conversation>` 单一真源 + UI `collectAsStateWithLifecycle()`，1 字 = 1 次更新。

- 三协议实现：OpenAI 兼容（一等公民：chat/completions SSE，覆盖 GLM/DeepSeek/方舟/OpenRouter）、Anthropic（messages API）、Google（`:streamGenerateContent?alt=sse`）。
- `ProviderManager`：注册 type → Provider 实现映射。
- **文本标签兜底 `TagToolCallParser`**（GLM-5.2 等无原生 FC 模型）：
  - 流式增量扫描 `<tool_calls>` 开标签 → 命中后正文进入 pending 缓冲（防 JSON 闪现正文气泡）→ 闭合标签后解析为 Tool parts；
  - 两种格式：行式 `tool_name:{json}`、JSON 数组；
  - 截断容错：流意外结束（maxTokens 截断）时对未闭合块尽力解析；
  - 启用条件：模型能力声明不支持原生 FC，或响应中出现标签（自动降级）。
- 内置供应商档案：
  - **DeepSeek**：`https://api.deepseek.com`，模型 deepseek-v4-pro / deepseek-v4-flash，body 附 `"reasoning_effort": "max"`；
  - **火山方舟 CodingPlan**：coding v3 端点，模型 glm-5.2（1024K 上下文 / 128K 输出）、deepseek-v4-pro、doubao-embedding-vision（vision+embedding），body 附 `"thinking": {"type": "enabled"}`；
  - 全部可编辑、可删除（除至少保留一个自定义位）。
- KV 守护：消息序列化稳定化（固定字段序、紧凑 JSON、时间戳不进前缀）+ `KvPrefixStabilityGoldenTest`。

## 7. 数据层（P3）

- Room **自版本 1 起步**（绿地不伪造迁移历史；架构保持迁移友好：导出 schemas、AutoMigration 就绪）。
- 实体清单：
  - `ConversationEntity(id PK, title, cardId?, pinned, archived, createdAt, updatedAt, lastMessageAt, messageCount, worldbookIdsJson, regexIdsJson, uiTemplateId, enableMemory, contextConfigJson, summaryCountersJson)`
  - `MessageNodeEntity(id PK, conversationId FK CASCADE + index, parentId, selectIndex, messagesJson, createdAt)`
  - `CharacterCardEntity`（§5.3 全字段）、`WorldbookEntity(id PK, name, enabled, cardId?)`、`WorldbookEntryEntity`、`RegexScriptEntity`、`MemoryEntity`、`SummaryEntity(id PK, conversationId FK, level, roundIndex, content, embeddingBlob, createdAt)`、`FavoriteEntity(id PK, type, conversationId, nodeId, messageId, excerpt, createdAt)`
  - sqlite-vec 虚表 `memory_vec / worldbook_vec / summary_vec`：DAO raw SQL 维护（Room 不映射虚表），onOpen 回调 CREATE IF NOT EXISTS。
- DAO：Flow 响应式查询 + PagingSource + 投影 DTO（列表页轻量化）。
- `SettingsStore`（DataStore Preferences）：`settingsFlow: StateFlow<Settings>` + `suspend fun update(fn)`；Settings 含：供应商档案列表、外观（主题模式/引用高亮/气泡样式）、生成参数（temperature/topP/maxTokens/上下文策略/历史轮数 0-200）、记忆开关与容量、显示设置。

## 8. 生成管线（P4）—— Agentic 6 不变性

### 8.1 ChatService（生成任务唯一所有者）
- 每会话 `MutableStateFlow<Conversation>` 单一真源；`sendMessage / regenerateAtNode / stopGenerating / handleToolApproval`；任务跑在 appScope（离页不中断 + 后台通知）；`errors: StateFlow<List<ChatError>>`。

### 8.2 GenerationHandler.generateText（[INVARIANT-AGENTIC]）
```
for (step in 0 until 256) {
    流式生成（逐字 emit → 思考卡片/正文气泡）
    待执行工具 = 末消息未执行 Tool parts
    if (待执行.isEmpty()) break            // 三 break 之一：不调工具
    if (待执行.any { it.approvalState is Pending }) break  // 之二：等待审批
    for (tool in 待执行) {
        output = toolDef.execute(input)    // 失败 → 错误 JSON；取消 → 重抛
        原地回填 tool.copy(output = …)      // 绝不新建 TOOL 消息
    }
    emit(更新后 messages)                  // 循环继续
}
```
- **禁 tool_choice = required**（仅 auto / none）。
- **被动工具**（记忆召回/世界书召回）：经系统提示注入，不进 tools 参数、不预执行。

### 8.3 RP 两阶段闭环（maxLoops = 3）
- 外层循环 ≤3 轮，每轮 = 阶段一「推理 + 工具规划」（流入思考卡片；工具调用执行并原地回填）→ 阶段二「基于结果生成最终回复」（流入正文气泡）。
- 阶段二消息序列 = 阶段一前缀 + 阶段一 assistant 消息（前缀逐字节一致，保 KV 命中）。
- 阶段一未产生任何工具调用 → 提前收口（break）。
- **内置提示词强化**：Agentic 协议段注入系统提示——硬性要求 >2 轮思考、>1 次主动工具调用（除被动工具外），不依赖 tool_choice。

### 8.4 系统提示组装顺序（KV 分层）
```
[稳定前缀]
  1. 角色卡 system prompt
  2. NSFW 块（NsfwBlock.kt 占位，用户手动填写）
  3. 世界书常驻条目
  4. C 级摘要（永久）
  5. 各工具 systemPrompt()
[半稳定段]
  6. B 级摘要
  7. 长期记忆 Top-K 注入
[动态尾段]
  8. A 级摘要（最近轮）
  9. 裁剪后对话历史（裁剪器保证不拆散工具调用/结果对）
 10. 本轮用户输入
```

### 8.5 NSFW 占位
`app/…/data/ai/prompts/NsfwBlock.kt` 独立常量文件留空占位，注释明确「由用户手动填写；禁止任何审查/过滤/改写逻辑触碰此内容」。

## 9. RP 生态（P5）

| 功能 | 规格 |
|------|------|
| 角色卡导入 | PNG（tEXt `chara` base64 → spec v2/v3 解析器）+ JSON；导入自动落位（提示词/开场白/世界书/正则各归其位并自动启用）；头像 1:1 裁剪（min(w,h) 左上起） |
| 角色卡导出 | PNG 导出（写回 tEXt chunk）+ JSON 导出 |
| 内置默认卡 | 「鹿溪」（Task-V0.4.1 附录提示词），readonly 保护 |
| 世界书 | 三策略召回：常驻（每轮按 position 注入）/ 关键词（扫描最近对话 + 递归扫描已注入条目，命中 keys + 概率 roll + order 排序）/ 向量（用户输入 + AI 正文切片 → embedding → sqlite-vec Top-K + 相似度阈值）；三策略按 entry.strategy 共存 |
| 正则脚本 | 五作用域（user/ai/reasoning/worldbook）× 四时机（display/send/接收×2）× 深度区间；replace 支持 `{{match}}` / `$1`；内置预设：思维链剥离 / 引用 / 旁白 / md 代码块 / 标签清理 |
| UI 模板 | `LuzzyTemplateEngine`：{{placeholder}} 注册表（时间/日期/角色名/用户名…）+ 轻量条件段，无重型依赖 |
| 收藏 | 消息/会话收藏 + 收藏页 |
| 会话导出 | MD / JSON / PNG 长截图（系统分享面板） |
| 三级摘要 | A 级每轮 ≤50 字（盲续五要素）；B 级每 10 轮合并 ≤10 条；C 级每 50 轮固化永久；生成完成后异步执行不阻塞回复；嵌入写 vec 表 |
| ACE 长期记忆 | Execute（Top-K 注入 + 检索工具）→ Reflect（LLM 反思本轮记忆有用/有害/无关 → 更新计数）→ Update（提取新事实 → 余弦 ≥0.92 去重合并 → 新增）；评分淘汰（helpful−harmful + 失活时长，容量可配）；记忆管理页 |

## 10. UI 层（P6/P7）

### 10.1 路由表（Navigation3 `entry<T>` 模式）
| Route（sealed NavKey） | 页面 |
|------|------|
| Home | 会话列表（置顶/滑动删除/搜索） |
| Chat(conversationId) | 聊天页 |
| Characters | 角色卡库 |
| CharacterDetail(cardId) | 角色卡详情/编辑 |
| Worldbooks / WorldbookDetail(bookId) | 世界书列表/编辑 |
| RegexEditor(cardId) | 正则编辑器 |
| Memory | 记忆管理 |
| Favorites | 收藏 |
| Search | 全局搜索 |
| History | 历史会话 |
| Settings / SettingsProviderList / SettingsProviderDetail / SettingsGeneration / SettingsAppearance / SettingsPrompt / SettingsAbout | 设置族 |

`NavDisplay` + entryDecorators（rememberSaveableStateHolderNavEntryDecorator + rememberViewModelStoreNavEntryDecorator）+ SharedTransitionLayout；转场 slide/fade + 预测式返回。

### 10.2 页面要点
- **聊天页**：气泡 + 思考卡片时间线（进行中脉冲/完成/错误三态）+ 工具卡片内嵌 CoT 列 + 输入区两行布局（输入行/操作行）+ 全屏 Markdown 编辑器（上下分区同步滚动）+ 回到底部箭头 + token 统计行 `↑in(cache·%) ↓out · tok/s · s`（三阶段实时更新）+ 20 轮窗口渲染（上滑解锁历史，后端保留全部）。
- **设置页**：供应商卡片（品牌 logo/名称/端点）、模型能力开关（reasoning/vision/embedding/tool）、上下文长度 M/m 单位、历史轮数滑条 0-200。

### 10.3 动效令牌
| 场景 | 时长 | 曲线 |
|------|------|------|
| 进入（页面/容器/弹窗） | 300ms | spring(dampingRatio=0.8, stiffness=380) 或 FastOutSlowIn |
| 交互（按压/切换/展开） | 150ms | tween FastOutSlowIn |
| 退出 | 195ms | tween AccelerateDecelerate |
| 弹窗 | scale 0.92→1 + fade | 同进入 |
| 列表项 | stagger 20ms | 同进入 |
| 思考卡展开 | expandVertically | 195ms |
| 流式正文 | **无打字动画**（原生逐字） | — |

## 11. 测试与质量守护

| 测试 | 守护目标 |
|------|----------|
| ChunkMergeTest | 合并代数（文本/工具/推理增量） |
| TagToolCallParserTest | 标签兜底解析（两格式 + 截断容错） |
| WorldbookRecallTest | 三策略召回 |
| SummarySchedulerTest | 三级摘要调度 |
| AceDedupEliminationTest | ACE 去重与淘汰 |
| PngTextChunkRoundTripTest | PNG tEXt 导入导出往返 |
| KvPrefixStabilityGoldenTest | 同一历史两次组装逐字节相等 |
| ContextTrimmerTest | 裁剪不拆散工具对 |
| RegexScriptTest | 正则作用域/时机 |

发版自检：`docs/INVARIANTS-CHECKLIST.md` 逐项打勾。

## 12. 发布流程（规则 9-12）

- 每次代码变更同步 `CHANGELOG.md`；涉及工具/MCP/内置提示词变更 → 同步 agentic 请求阶段内部工具提示词。
- Release 模板：`### vX.Y.Z — 标题` + 「新增 / 优化 / 修复 / 注意事项」分类要点段 + 构建结果与 versionCode；稳定版（x.y.0）附 APK。
- 流程：INVARIANTS 自检 → JVM 测试 → `assembleRelease`（签名包）→ commit + push（SSH）→ tag → `gh release create`。

## 13. 里程碑与验收

| 里程碑 | 内容 | 验收 |
|--------|------|------|
| P0 | 文档落盘 + 环境核验 + 备份并删除旧仓库 + 新仓库首推 + 脚手架 + 资产管线 | 空壳 App 可运行，Aurora Dual 主题与图标可见 |
| P1 | 领域模型层 + 合并代数 | ChunkMergeTest 绿 |
| P2 | AI 层（三协议 + 流式 + 标签兜底） | 对 DeepSeek 实测逐字输出 |
| P3 | Room + DataStore + 仓库层 | DAO 查询/序列化通过 |
| P4 | GenerationHandler + 两阶段闭环 + 审批续跑 + NSFW 占位 | Agentic 单测 + KV Golden 绿 |
| P5 | 角色卡/世界书/正则/UI 模板/收藏/摘要/ACE | PNG 导入导出往返绿 |
| P6/P7 | UI 全量 + 三态动画 | 全路由可达，动效三态完整 |
| P8 | 打磨 + 自检 + v0.1.0 发版 | 签名 APK + Release + CHANGELOG/README |

## 14. 后续路线（v0.2.0+，不属本次交付）

- **v0.2.0 原生 TRPG 模式**：数据只读原则（游戏状态仅经 GM 工具变更）、GM 工具组（d20_check / roll_damage / combat_resolve / social_resolve / explore_resolve / inventory_* / rest_resolve / map_* / apply_state_delta / advance_time）、本地 D&D 5e d20 检定引擎（skillBonus/DC 引擎计算覆盖 LLM）、Think-1→4 流水线、世界卡 v2.1 四阶段设计模式 + ASG v2 导入（`docs/trpg标准世界卡/` 为验收样例）。
- **v0.3.0+ 候选**：TTS/语音输入、图像生成接入、MCP 扩展、WebDAV 备份同步。
