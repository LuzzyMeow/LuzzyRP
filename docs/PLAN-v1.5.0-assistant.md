# PLAN · v1.5.0「助手」原生 Agent（实施级计划）

> ## ⚠️ 本计划已于 2026-09-11 作废：模块按用户指示彻底移除
>
> **当前状态：历史存档（HISTORICAL ARCHIVE）——本文不再指导任何新工作。**
> 用户于 2026-09-11 明确指示彻底移除「助手」功能（原生 Compose Agent）及所有相关子页面，
> 模块代码 / 资产 / 测试 / 构建接入已全部删除（见 `CHANGELOG.md` v1.5.0「移除」段与
> `docs/WORKLOG.md` 对应会话）。**§2-§17（助手实施）整体作废**；
> **§18（上游同步 1.9.3 调查）仍有效**——属 W1 工作流，其中的实体生成规程至今仍是现行规程
> （正文引用于 `tools/patches/README.md` 末节）。

> **状态（移除前快照）：待用户审阅（2026-09-09 起草拟）**。本文是**实施级计划**——含模块拆分、Room 表结构、
> 目录布局、工具契约、桥接契约与每阶段验收标准，经用户确认后可直接照此开发。
> **v1.5.0 含三条工作流**：W0 文档与纪律（已完成）· W1 上游同步（§18）· W2 助手原生 Agent（§2-§17）。
>
> **上游基线**：RP-Hub 1.9.2（commit `d2f2625`）· **前置调研**：
> [`docs/RESEARCH-assistant-native-agent.md`](RESEARCH-assistant-native-agent.md)
>
> **设计门（硬性规定 9）**：本文只定义**信息架构与组件拆分**，不含视觉设计。进入界面实现前
> 必须完整阅读 4 项设计 SKILL、产出 3 个差异化方向、由用户选定后写入 `DESIGN.md`。
>
> **许可红线**：rikkahub / rikkahub-agent 为 AGPL-3.0，**只借鉴架构思路，不复制任何代码**；
> 新增依赖仅取 Apache-2.0 / MIT。

---

## 0. 一句话

在 LuzzyRP 现有 WebView 壳之上，新增一个**原生 Kotlin「助手」模块**（Compose，非 WebView），
支持多助手、独立会话历史与记忆、Skill、MCP 工具、独立工作区、双模式终端，以及完整的
Agent 渲染体验（markdown / 思考卡 / 工具卡 / 多步骤折叠）。

---

## 1. 定位与边界

### 1.1 是什么

- **一个原生 Agent 客户端**：自带 LLM 协议层、工具循环、记忆、工作区、终端；
- **与 RP-Hub 并列而非取代**：RP 角色扮演仍在 WebView 里跑；助手是第二个「面」；
- **配置单向复用**：从 Web 端只读读取供应商 / Key / 模型（避免用户填两遍、避免密钥二次落盘）。

### 1.2 不是什么

| 不做 | 原因 |
|------|------|
| 不重写 RP-Hub 前端 | 硬性规定 2/3；WebView 线保持 |
| 不与 RP 会话自动同步 | 用户已拍板（助手数据独立存储） |
| 不引入端上嵌入模型 | 用户已拍板（走 OpenAI 兼容 `/embeddings`） |
| 本轮不做后台/定时任务 | 用户已拍板（长任务仅页面前台） |
| 不复制 rikkahub 代码 | AGPL-3.0 不兼容 |
| 不新增内容审查 / 过滤 / 改写 | 硬性规定 1 |

### 1.3 版本归属

**已定：并入当前开发中的 v1.5.0**（2026-09-09 用户指正版本跳号）。CHANGELOG 顶部版本为
`v1.5.0 — 开发中`（文档定位澄清 + 发布纪律固化 + 助手调研），**尚未发布**，助手功能与
**上游同步**直接并入该版本，不另起版本号。

v1.5.0 因此包含**三条并行工作流**：

| 工作流 | 内容 | 状态 |
|--------|------|------|
| **W0 · 文档与纪律** | 二创定位澄清 + 单 APK / 签名一致纪律 | ✅ 已完成 |
| **W1 · 上游同步（Track U）** | 合并上游新提交（1.9.2 → 新基线），见 §18 | ⏳ 待执行 |
| **W2 ·「助手」原生 Agent** | 本计划 §2-§17（P0-P4） | ⏳ 待执行 |

三条工作流互不冲突：W1 只碰 `assets/rphub/`（上游文件 + 登记 patch），W2 只碰原生 Kotlin 侧
（新增包 + 新增 assets 子目录）；唯一交汇点是 W2 的侧栏入口（patch 040），须在 W1 同步完成
之后生成，避免实体前像失配。

若希望分多次交付，则在 **v1.5.0 同一版本号下分阶段增量**（发版时一次性把 CHANGELOG 状态
改为「已发布」并附 APK），不再新增 v1.6.0。

---

## 2. 架构总览

### 2.1 运行时分层

```
MainActivity（单 Activity）
└─ FrameLayout
   ├─ WebView（RP-Hub，现有，不动）
   └─ ComposeView  assistantRoot（初始 GONE）
      └─ AssistantApp（NavHost）
         ├─ 视图层    Compose 屏幕：助手列表/会话/记忆/技能/MCP/工作区/终端/设置
         ├─ 状态层    ViewModel（每屏一个）+ AssistantSessionViewModel（会话状态机）
         ├─ 领域层    AgentLoop（纯 Kotlin，无 Android 依赖，可单测）
         │             ├─ LlmTransport   三协议流式 + toolCalls 解析
         │             ├─ ToolRegistry   内置工具 / Skill 工具 / MCP 工具 统一注册
         │             ├─ ApprovalGate   写类工具逐调用审批
         │             ├─ MemoryEngine   全文注入 / 向量召回
         │             ├─ ContextBuilder 系统提示词 + 记忆 + 技能 + 历史装配
         │             └─ BudgetGuard    轮次 / 时长 / token 上限
         ├─ 数据层    Room（会话/消息/记忆/助手/MCP/技能）+ DataStore（偏好）+ 文件系统（工作区）
         └─ 集成层    Bridge（读 Web 端供应商配置）/ SAF / 通知 / 日历 / 无障碍（P4）
```

### 2.2 模块划分（Gradle 仍单模块 `:app`，用包隔离）

```
com.luzzymeow.luzzyrp
├─ MainActivity.kt                    # 视图容器：WebView + assistantRoot（懒创建）
├─ web/                               # 现有 JSBridge 等（不动）
├─ assistant/                         # ★ 新增根包
│  ├─ AssistantActivity.kt            # 可选：兜底独立 Activity（见 §3）
│  ├─ ui/                             # Compose 屏幕与组件
│  │  ├─ screen/  chatlist/ chat/ memory/ skill/ mcp/ workspace/ terminal/ settings/
│  │  └─ component/  markdown/ thinkingcard/ toolcard/ stepgroup/ approvaldialog/ …
│  ├─ domain/                         # 纯 Kotlin（无 Android 依赖）
│  │  ├─ loop/AgentLoop.kt  loop/AgentEvent.kt  loop/BudgetGuard.kt
│  │  ├─ llm/  LlmTransport.kt  OpenAiProtocol.kt  AnthropicProtocol.kt  GeminiProtocol.kt
│  │  ├─ tool/ Tool.kt  ToolRegistry.kt  ToolResult.kt  builtin/*
│  │  ├─ memory/ MemoryEngine.kt  EmbeddingClient.kt  Retriever.kt
│  │  ├─ skill/ SkillLoader.kt
│  │  ├─ mcp/ McpClient.kt  McpConfigParser.kt  McpToolAdapter.kt
│  │  └─ prompt/ ContextBuilder.kt
│  ├─ data/                           # Room + DataStore + 文件
│  │  ├─ db/ (entities/daos/migrations)  prefs/  workspace/WorkspaceManager.kt
│  ├─ runtime/                        # Android 相关实现
│  │  ├─ proot/ProotRuntime.kt        # 沙盒
│  │  ├─ terminal/ (Session, PtyBridge, GlobalShell, SandboxShell)
│  │  ├─ toolimpl/                    # 需要 Android Context 的工具实现
│  │  └─ permission/PermissionGate.kt
│  └─ bridge/ AssistantBridge.kt      # 与 Web 端/主 Activity 的接口
└─ assets/assistant/                  # ★ 新增：rootfs、proot 二进制、内置技能
```

**为什么单模块**：AGP 9 + 单模块是当前工程纪律（`settings.gradle.kts` 注释「仅 :app 单模块」）；
包隔离 + `domain` 不依赖 Android 足以保证可测试性与边界。若后续体积/编译时间失控再拆模块（§15 D2）。

### 2.3 与现有壳的接线

| 接线点 | 现状 | 改动 |
|--------|------|------|
| `MainActivity` 布局 | 仅 WebView | 加 `FrameLayout` 内 `ComposeView`（懒创建，见 §3.2） |
| 返回键 | WebView 回退 | 新增优先级：助手可见 → 先关助手 |
| 系统栏 | `LuzzyBridge.setSystemBarStyle` | 助手切换时复用同一策略 |
| 侧栏入口 | 无 | 扩展层 DOM 注入（原型）→ 登记 patch（落地），见 §3.3 |
| 供应商配置 | Web 端 IndexedDB | 新增桥接只读方法，见 §12 |
| 权限 | INTERNET 等 | 新增按需声明，见 §11.4 |

---

## 3. 入口与宿主形态

### 3.1 形态：同 Activity 原生覆盖层（用户已选 D1-B）

- `assistantRoot: ComposeView` 初始 `View.GONE`，**首次点击才创建**（懒加载，避免冷启动成本）；
- 显示时：`assistantRoot.visibility = VISIBLE`，WebView 保持存活（状态不丢）；
- 关闭时：`VISIBLE → GONE`，助手侧停订阅、释放长列表缓存，**不 destroy**。

### 3.2 懒创建与内存

```kotlin
// MainActivity 内（示意）
private var assistantView: ComposeView? = null
fun showAssistant() {
    val view = assistantView ?: ComposeView(this).also {
        it.setContent { LuzzyAssistantTheme { AssistantApp(onExit = ::hideAssistant) } }
        root.addView(it, MATCH_PARENT_PARAMS)
        assistantView = it
    }
    view.visibility = View.VISIBLE
}
fun hideAssistant() { assistantView?.visibility = View.GONE }
```

低端机策略：助手隐藏超过 5 分钟 → 清空 `AssistantApp` 的组合内容（保留 ViewModel 数据）。

### 3.3 侧栏入口：两段式

| 阶段 | 做法 | 验收 |
|------|------|------|
| 原型 | `assets/ext/luzzy-assistant.js` DOM 注入按钮 + `MutationObserver` 重注入 + 锚点缺失静默降级 | 点击 → 原生层出现；Web 端零改动 |
| 落地 | 新增 patch `040-assistant-entry`：`ui-components.js` 底部簇加条目（外观之上）+ `index.html` 标记；`luzzy-ext.js` 监听点击调 `Luzzy.openAssistant()` | verify-markers 新增校验项全绿；折叠态/激活态正常 |

> 侧栏不新增需要 Vue 渲染的 `currentView` 值——点击即调桥接，DOM 里只多一个按钮。

---

## 4. 数据模型

### 4.1 Room 表（`assistant.db`，version 1）

```kotlin
@Entity("assistant")
data class AssistantEntity(
    @PrimaryKey val id: String,              // uuid
    val name: String,
    val avatarPath: String?,                 // 工作区相对路径
    val systemPrompt: String,                // 助手提示词（可含变量）
    val providerId: String?,                 // 引用 Web 端供应商 id（只读）
    val modelId: String?,                    // 模型（providerId::bareId 语义）
    val temperature: Float?, val topP: Float?, val maxTokens: Int?,
    val extraBodyJson: String?,              // 请求体扩展（JSON）
    val paramsJson: String?,                 // 其他参数（stop/seed/reasoning_effort…）
    val memoryMode: String,                  // full | embed | hybrid
    val embeddingModelRef: String?,          // 嵌入模型（providerId::bareId）
    val memoryTopK: Int, val memoryThreshold: Float,
    val workspaceMode: String,               // sandbox | host
    val createdAt: Long, val updatedAt: Long, val sortOrder: Int
)

@Entity("conversation", indices = [Index("assistantId"), Index("updatedAt")])
data class ConversationEntity(
    @PrimaryKey val id: String,
    val assistantId: String,
    val title: String,
    val summary: String?,                    // 自动标题/摘要（可空）
    val createdAt: Long, val updatedAt: Long,
    val archived: Boolean
)

@Entity("message", indices = [Index("conversationId"), Index("createdAt")])
data class MessageEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val role: String,                        // user | assistant | tool | system
    val content: String,                     // markdown 原文
    val reasoning: String?,                  // 思考内容（思考卡）
    val toolCallsJson: String?,              // 本轮 toolCalls
    val toolCallId: String?, val toolName: String?,
    val status: String,                      // complete | streaming | error | cancelled
    val createdAt: Long,
    val tokenUsageJson: String?
)

@Entity("memory", indices = [Index("assistantId"), Index("scope"), Index("createdAt")])
data class MemoryEntity(
    @PrimaryKey val id: String,
    val assistantId: String,                 // 归属助手（用户要求「助手分隔记忆」）
    val scope: String,                       // global | assistant
    val type: String,                        // fact | preference | task | note
    val content: String,
    val source: String,                      // user | agent | import
    val conversationId: String?,
    val embedding: ByteArray?,               // float32 小端；无嵌入模型时 null
    val embeddingModelRef: String?, val dim: Int,
    val createdAt: Long, val updatedAt: Long, val lastUsedAt: Long?
)

@Entity("skill", primaryKeys = ["id"])
data class SkillEntity(
    @PrimaryKey val id: String,
    val name: String, val description: String,
    val body: String,                        // Markdown 技能正文
    val source: String,                      // builtin | file | url
    val enabledGlobal: Boolean,
    val createdAt: Long, val updatedAt: Long
)

@Entity("skill_binding", primaryKeys = ["skillId", "assistantId"])
data class SkillBindingEntity(val skillId: String, val assistantId: String, val enabled: Boolean)

@Entity("mcp_server", primaryKeys = ["id"])
data class McpServerEntity(
    @PrimaryKey val id: String,
    val name: String,
    val transport: String,                   // http | sse（stdio 见 §9.3）
    val url: String?, val headersJson: String?,
    val command: String?, val argsJson: String?, val envJson: String?,  // stdio 预留
    val enabledGlobal: Boolean,
    val toolAllowlistJson: String?,          // null = 全部工具
    val lastConnectedAt: Long?, val lastError: String?
)

@Entity("mcp_binding", primaryKeys = ["assistantId", "serverId"])
data class McpBindingEntity(val assistantId: String, val serverId: String, val enabled: Boolean)

@Entity("tool_audit", indices = [Index("createdAt")])
data class ToolAuditEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val assistantId: String, val conversationId: String,
    val toolName: String, val argsJson: String, val resultPreview: String,
    val approved: Boolean, val ok: Boolean, val durationMs: Long, val createdAt: Long
)
```

**FTS 搜索**：`message` 建 `FTS4`/`FTS5` 镜像表（`message_fts(content, title)`）用于会话内/跨会话
关键词检索；Room 通过 `@Fts4` 实体或原生 SQL 建虚表（Android SQLite 自带 FTS4，FTS5 视系统版本，
优先 FTS4 保兼容）。

### 4.2 文件系统布局

```
filesDir/assistant/
├─ db/assistant.db
├─ workspaces/<assistantId>/           # 每助手独立工作区
│  ├─ files/                           # Agent 可读写区（沙盒模式下映射为 /workspace）
│  ├─ attachments/                     # 导入附件（SAF 拷贝入）
│  ├─ exports/                         # 导出中转
│  └─ .meta/workspace.json             # 元数据（创建时间/大小/配额）
├─ sandbox/                            # proot 运行时（全局一份，可重建）
│  ├─ proot                            # 静态 aarch64 二进制（随包或首次释放）
│  ├─ rootfs/                          # Alpine rootfs（解压后 ~60-120MB）
│  └─ tmp/
├─ skills/                             # 用户导入的技能 Markdown
└─ logs/                               # 诊断日志（脱敏，禁含密钥）
```

**配额**：单工作区默认上限 2GB（可调），超限拒绝写入并提示（避免塞爆存储）。

### 4.3 DataStore（助手偏好，与 Web 端 `settings` 解耦）

| 键 | 说明 |
|----|------|
| `active_assistant_id` | 当前助手 |
| `theme_mode` | 只读跟随 Web 端（写入由 Web 端负责） |
| `tool_global_switch_<name>` | 内置工具全局开关 |
| `approval_policy` | 逐调用审批 / 白名单自动批准（按工具） |
| `sandbox_rootfs_version` | rootfs 版本（升级时重装） |
| `terminal_*` | 字号、回滚行数、光标样式等 |
| `mcp_timeout_ms` / `mcp_max_retries` | MCP 连接参数 |

---

## 5. Agent 循环

### 5.1 事件模型

```kotlin
sealed interface AgentEvent {
    data class TurnStarted(val turn: Int) : AgentEvent
    data class ReasoningDelta(val text: String) : AgentEvent      // 思考卡
    data class TextDelta(val text: String) : AgentEvent           // 正文（markdown）
    data class ToolCallStarted(val call: ToolCall) : AgentEvent   // 工具卡
    data class ToolCallApproval(val call: ToolCall) : AgentEvent  // 审批弹窗
    data class ToolCallProgress(val id: String, val chunk: String) : AgentEvent // 流式输出
    data class ToolCallFinished(val id: String, val result: ToolResult) : AgentEvent
    data class Usage(val input: Int, val output: Int) : AgentEvent
    data class TurnFinished(val reason: String) : AgentEvent       // stop|max_rounds|cancelled|error
    data class Error(val message: String, val retryable: Boolean) : AgentEvent
}
```

### 5.2 循环（纯 Kotlin，可单测）

```kotlin
class AgentLoop(private val transport: LlmTransport, private val tools: ToolRegistry,
                private val memory: MemoryEngine, private val prompt: ContextBuilder,
                private val approval: ApprovalGate, private val budget: BudgetGuard) {
    fun run(assistant: AssistantConfig, conversation: Conversation, userInput: String): Flow<AgentEvent> = flow {
        val history = prompt.build(assistant, conversation, userInput, memory)
        repeat(budget.maxRounds) { turn ->
            emit(AgentEvent.TurnStarted(turn))
            val calls = mutableListOf<ToolCall>()
            val text = StringBuilder()
            transport.stream(history, tools.schemas(assistant)).collect { d ->
                d.reasoning?.let { emit(AgentEvent.ReasoningDelta(it)) }
                d.content?.let { text.append(it); emit(AgentEvent.TextDelta(it)) }
                calls += d.toolCalls
                d.usage?.let { emit(AgentEvent.Usage(it.input, it.output)) }
            }
            history += assistantMessage(text.toString(), calls)
            if (calls.isEmpty()) { emit(AgentEvent.TurnFinished("stop")); return@flow }
            for (call in calls) {
                emit(AgentEvent.ToolCallStarted(call))
                if (approval.needsApproval(call)) emit(AgentEvent.ToolCallApproval(call))
                val result = withTimeout(budget.toolTimeoutMs) { tools.execute(call) }
                history += toolMessage(call, result)
                emit(AgentEvent.ToolCallFinished(call.id, result))
            }
        }
        emit(AgentEvent.TurnFinished("max_rounds"))
    }
}
```

要点：取消 = 取消 `Flow` 收集并关闭 SSE；工具异常统一转 `ToolResult.Error` 回灌给模型（不中断整轮）；
每轮后检查 token 预算，超限触发上下文压缩（§5.4）。

### 5.3 三协议适配（对齐 Web 端语义）

| 协议 | 流式 | 工具调用 | 备注 |
|------|------|---------|------|
| OpenAI | `stream: true` + SSE `delta` | `delta.tool_calls[]` 增量拼接 | 主路径 |
| Anthropic | SSE `content_block_delta` | `tool_use` block | `reasoning` 走 thinking block |
| Gemini | SSE `streamGenerateContent` | `functionCall` part | 抗截断走 continuation |

统一抽象 `LlmTransport.stream(messages, tools): Flow<LlmDelta>`；协议差异全部收敛在实现类内。
**空闲超时 120s**、**连接超时 30s**、**重试 2 次（指数退避，仅幂等请求）**。

### 5.4 上下文装配与压缩

```
[系统提示词]
  ├─ 助手提示词（含变量：{{time}} {{model}} {{assistant_name}} {{workspace}} …）
  ├─ 全局启用的 Skill（按声明顺序，正文注入）
  ├─ 助手启用的 Skill
  ├─ 记忆块（见 §7：full → 全文；embed → 召回 Top-K；hybrid → 召回 + 最近事实）
  └─ 工具使用约定（由 ToolRegistry 自动生成）
[历史消息]（超限时压缩：保留最近 N 轮 + 工具调用对 + 旧轮摘要）
[本轮用户输入]
```

压缩策略：token 估算（字符数/3.5 近似）→ 超过模型上下文 70% 触发 → 把最旧轮次替换为
一条 `system` 摘要（摘要由同一模型生成，异步、可取消、失败则退化为截断）。

---

## 6. 需求 1-2：多助手 与 会话历史

### 6.1 多助手

- 数据：`assistant` 表；每助手独立提示词/模型/记忆/技能/MCP 绑定/工作区；
- UI：助手列表（头像 + 名称 + 最近会话时间 + 未读标记）；支持新建 / 复制 / 归档 / 删除；
- 切换：顶部助手切换器（下拉），切换即切换 `active_assistant_id`，会话列表随之过滤；
- 删除语义：软删除（`archived`）+ 二次确认弹窗，工作区目录默认保留（用户可勾选一并删除）。

### 6.2 会话历史与检索

| 能力 | 实现 |
|------|------|
| 按助手分隔 | `conversation.assistantId` 过滤（默认视图） |
| 按日期分组 | 列表按 `updatedAt` 分组：今天 / 昨天 / 7 天内 / 本月 / 更早 |
| 按日期筛选 | 顶部筛选器：全部 / 今天 / 7 天 / 30 天 / 自定义区间 |
| 关键词检索 | FTS4 全文索引（标题 + 正文）；输入即搜（300ms 防抖）；命中高亮 |
| 高级检索 | 关键词 + 助手 + 日期区间 组合；结果按会话聚合（显示命中条数与前 2 条片段） |
| 会话操作 | 重命名 / 归档 / 删除 / 导出（Markdown/JSON）/ 置顶 |
| 空态 | 无会话/无搜索结果 两种空态文案 |

> 检索**只查原生助手会话**，不跨到 RP-Hub 会话（用户已拍板数据独立）。

---

## 7. 需求 3：助手记忆（嵌入模型支持）

### 7.1 三种模式（用户需求原话的落地）

| 模式 | 条件 | 行为 |
|------|------|------|
| `full` | 未配置嵌入模型 | 记忆条目**全文注入**系统提示词（按 token 预算裁剪，最近的优先） |
| `embed` | 配置了嵌入模型 | 对用户输入做嵌入 → 与记忆向量算余弦 → 取 Top-K（默认 K=8）+ 阈值（默认 0.35）→ 注入 |
| `hybrid` | 配置了嵌入模型 | 向量召回 Top-K + 最近 N 条事实（默认 N=5，去重） |

自动降级：配置了嵌入模型但**调用失败**（网络/额度）→ 本轮退化为 `full` 并记一条日志 + UI 提示。

### 7.2 嵌入与检索实现

- **嵌入来源**：复用 Web 端供应商（OpenAI 兼容 `POST /embeddings`），模型引用 `providerId::bareId`；
  批次上限 32 条/请求，失败重试 1 次；
- **向量存储**：`memory.embedding` BLOB（float32 小端）+ `dim`；
- **检索**：第一版 **纯 Kotlin 余弦暴力扫描**（万条量级 <10ms，可单测、零 NDK）；
  后续若记忆规模上万再评估 `sqlite-vec`（Android 有预编译 loadable 库，但 Room 加载扩展需绕，
  且引入 NDK/打包复杂度）——列入 §15 D4；
- **缓存**：用户输入嵌入结果按内容哈希缓存（LRU 128 条），避免重复计费。

### 7.3 记忆工具（暴露给 Agent）

| 工具 | 参数 | 行为 |
|------|------|------|
| `memory_write` | `content`, `type?`, `scope?` | 写入一条记忆；有嵌入模型则同步生成向量（失败则先落盘、后台补嵌） |
| `memory_search` | `query`, `topK?` | 向量/全文检索，返回条目 + 相似度 |
| `memory_update` | `id`, `content` | 更新内容并重算向量 |
| `memory_delete` | `id` | 删除（需审批） |
| `memory_list` | `scope?`, `limit?` | 列出最近记忆 |

### 7.4 与 Web 端 RP-Hub 记忆的关系

**完全独立**：RP-Hub 的记忆在 IndexedDB（角色/分支维度），助手记忆在 Room（助手维度）。
两者不互相读写、不自动迁移。若未来要互通，作为显式「导入/导出」动作单独立项。

---

## 8. 需求 4：Skill

### 8.1 技能形态

Markdown 文件（`skills/*.md`），约定 front-matter：

```markdown
---
name: 周报生成
description: 把本周会话与日历整理成周报
tools: [calendar_read, memory_search, ask_user]
---
（技能正文：给模型的流程说明 / 检查清单 / 输出格式约定）
```

### 8.2 加载与启用

| 维度 | 实现 |
|------|------|
| 来源 | 内置（`assets/assistant/skills/` 随包）、文件导入（SAF 选 `.md`）、URL 导入（可选） |
| 全局启用 | `skill.enabledGlobal = true` → 所有助手注入正文 |
| 指定助手启用 | `skill_binding(assistantId, skillId, enabled)` |
| 注入方式 | 正文进系统提示词；`tools:` 声明仅作**提示**，实际可用性仍由工具全局开关决定（防止技能绕过权限） |
| 冲突 | 同名技能以「用户导入 > 内置」覆盖，UI 明示来源 |
| 校验 | front-matter 解析失败 → 拒绝导入并提示（不静默吞） |

### 8.3 安全

技能正文是**提示词内容**，不是代码；但技能可诱导模型调用工具 → 因此**工具权限不因技能放宽**，
写类工具仍需审批（§11.1）。

---

## 9. 需求 5：MCP 工具

### 9.1 支持范围（用户已拍板）

| 传输 | 本轮 | 说明 |
|------|------|------|
| **HTTP（Streamable HTTP）** | ✅ 支持 | JSON-RPC over POST + 可选 SSE 流 |
| **SSE（旧式 HTTP+SSE）** | ✅ 支持 | 兼容存量服务器 |
| **stdio（npx/uvx 等本地进程）** | ⏳ 预留 | 需要环境内有 Node/Python 运行时——待 §9.3 条件满足后开启 |

### 9.2 JSON 导入

支持两种常见格式（自动识别）：

```jsonc
// ① Claude Desktop / 通用 mcpServers 格式
{ "mcpServers": { "filesystem": { "command": "npx", "args": ["-y", "@modelcontextprotocol/server-filesystem", "/path"] },
                  "remote": { "url": "https://example.com/mcp", "headers": { "Authorization": "Bearer x" } } } }

// ② 单服务器配置（或数组）
{ "name": "remote", "type": "http", "url": "https://example.com/mcp", "headers": {...} }
```

导入流程：解析 → 归一化 → **逐条预览（名称/传输/可达性检测）** → 用户确认 → 落库；
`${ENV_VAR}` 占位符在导入时提示用户填值（密钥存 DataStore 加密区，不入库明文日志）。

### 9.3 stdio 的前置条件（诚实说明）

Android 应用**不能直接 spawn `npx`**（没有 Node 运行时、且沙盒进程模型受限）。三条可行路径：

1. **在 proot 沙盒里跑 MCP stdio 服务器**（沙盒内已有 node/python）→ App 通过 `proot` 子进程
   的 stdin/stdout 做 JSON-RPC。**本轮列为 P4 可选**（依赖 §10 终端落地）；
2. 用户已装 Termux → 通过 `am`/`run-as` 协作（不可靠，不推荐）；
3. 服务器本身提供 HTTP/SSE 端点 → 走 §9.1 主路径。

**结论**：本轮先让「JSON 导入 + HTTP/SSE 连接 + 工具调用」闭环可用；stdio 在终端沙盒上线后
作为 P4 追加，且只在沙盒模式下允许（宿主模式不允许 spawn）。

### 9.4 客户端实现

- `McpClient`：`initialize` → `tools/list` → `tools/call`；`notifications/tools/list_changed` 触发刷新；
- 工具命名空间：`mcp__<serverId>__<toolName>`，避免与内置工具撞名；
- 超时：连接 15s / 调用 60s（可配）；失败 2 次后标记服务器 `lastError` 并在 UI 显示；
- 结果映射：MCP `content[]`（text/image/resource）→ 统一 `ToolResult`；图片落工作区 `attachments/`；
- 权限：MCP 工具**全部按「外部工具」处理**——首次调用逐个确认（可对该服务器「本次会话内始终允许」）。

---

## 10. 需求 6-7：独立工作区 与 双模式终端

### 10.1 工作区（每助手独立）

| 项 | 设计 |
|----|------|
| 根目录 | `filesDir/assistant/workspaces/<assistantId>/` |
| 目录 | `files/`（Agent 读写）、`attachments/`、`exports/` |
| 沙盒映射 | proot 启动时 `--bind workspaces/<id>/files:/workspace` |
| 宿主模式映射 | Agent 直接以 App 私有目录为工作区根；`/sdcard` 需 SAF 授权后 bind |
| 工具 | `workspace_list/read/write/patch/delete/move/mkdir`（路径必须落在工作区内，**拒绝越界**） |
| 配额 | 默认 2GB；单文件默认 64MB 上限（可调） |
| 导入/导出 | SAF 选择器拷入 `attachments/`；导出写 `exports/` 后由用户选择保存位置 |

**路径安全**：所有路径先 `canonicalPath` 归一，必须 `startsWith(workspaceRoot)`，否则拒绝并审计
（防 `../` 逃逸与符号链接穿越）。

### 10.2 终端双模式（用户已选：沙盒 = proot 真 Linux；全局 = 宿主文件系统）

| 模式 | 实现 | 可见范围 | 能力 |
|------|------|---------|------|
| **沙盒（proot）** | 释放 proot 静态二进制 + Alpine rootfs 到 `filesDir/assistant/sandbox/`；`proot -r rootfs -b <workspace>:/workspace -b /dev -b /proc -0 /bin/sh` | 仅 rootfs + 工作区 | apk 装包、python3、node、git、编译脚本（用户要的「程序编译工具」在此落地） |
| **全局（宿主）** | 直接 `ProcessBuilder("/system/bin/sh")`（App 权限） | App 私有目录 + SAF 授权目录 | 系统命令（ls/cat/grep/sed/awk…）；**不能** apk/pip；无 root |

**共同能力**：PTY 交互（若 `ProcessBuilder` 不支持 PTY，则用管道 + 行缓冲，牺牲交互式全屏程序）、
ANSI 渲染、会话内滚动回看、复制/粘贴、清屏、Ctrl-C（杀进程组）、退出确认。

**沙盒首启**：后台解压 + 进度提示（一次性）；rootfs 版本号写 DataStore，升级时重装（保留工作区）。

**rootfs 分发**（体积决策，§15 D3）：随包（APK +约 3-10MB 压缩包）或首次 Wi-Fi 下载（+60-120MB 解压）。
建议**随包内置最小 Alpine**（~3-5MB 压缩），node/python 由用户在沙盒内 `apk add`（按需、可撤销）。

### 10.3 终端安全

- 沙盒模式：Agent 在沙盒内可自由执行（隔离由 proot 提供）；
- 全局模式：**每次命令执行前审批**（可开「本次会话内允许只读命令」）；命令黑名单（HARDLINE）：
  `rm -rf /`、`dd if=`、`mkfs`、`reboot`、`pm uninstall`、`su`、`curl|sh` 等**无条件拦截**；
- 输出截断：单次回传上限 200KB（超出写工作区文件并回传路径）。

---

## 11. 需求 8-9：提示词/模型设置 与 渲染

### 11.1 提示词与模型设置

| 项 | 设计 |
|----|------|
| 助手提示词 | 多行编辑器 + 变量插入菜单（`{{time}} {{date}} {{model}} {{assistant_name}} {{workspace}} {{memory_count}}`） |
| 模型 | 从 Web 端供应商的合并模型列表中选择（`providerId::bareId`）；每助手可独立指定 |
| 参数 | temperature / topP / maxTokens / stop / seed / reasoning_effort（按模型能力显示，不支持的置灰） |
| 请求体扩展 | JSON 编辑器（校验 + 格式化 + 冲突提示：不允许覆盖 `messages`/`tools`/`stream`） |
| 预览 | 「预览最终请求」只读面板（密钥脱敏），便于排查 |
| 生效 | 即时保存；会话内切换模型只影响后续轮次 |

### 11.2 渲染（信息架构，视觉待设计门）

| 组件 | 内容 | 折叠行为 |
|------|------|---------|
| **Markdown** | 标题/列表/表格/代码块（语法高亮）/引用/链接/图片/GFM 任务列表；流式期间**增量渲染**（节流 100ms） | 代码块可折叠（>20 行） |
| **思考卡** | 模型 reasoning 内容；流式期逐字展开，结束后收起为一行摘要 | 默认折叠，点击展开 |
| **工具卡** | 工具名 + 参数（JSON 美化）+ 状态（等待审批/执行中/成功/失败）+ 耗时 + 结果预览 | 默认折叠，执行中展开 |
| **多步骤折叠** | 连续 ≥3 个工具调用 + 思考节点 → 聚合为「步骤组」卡（N 步 · 总耗时），展开看明细；单个步骤保留独立卡 | 组默认折叠 |
| **审批弹窗** | 工具名 / 参数 / 风险说明 / 「允许一次」「本会话始终允许」「拒绝」 | — |
| **流式** | 文本/思考/工具进度三类事件分别走不同渲染通道；**渲染节流 100ms**（对齐 Web 端 120ms 的降载思路） | — |

实现建议：Markdown 用 Compose 渲染库（如 `multiplatform-markdown-renderer`，Apache-2.0）
或自研 AST + `AnnotatedString`（体积更小、可控性强）——**待 §15 D5 定**。
性能红线：单条消息 >50KB 时降级为「纯文本 + 手动展开」，避免一次组合爆炸。

---

## 12. 工具目录（含用户追加的 6 类）

### 12.1 分级与默认开关

| 档 | 默认 | 说明 |
|----|------|------|
| **T0 只读** | 开 | 无副作用 |
| **T1 写（应用内）** | 开（逐调用审批） | 只动助手自己的数据 |
| **T2 写（设备/外部）** | **关** | 需用户显式开启 + 审批 |
| **T3 高危** | **关** | 屏幕自动化、短信、提权等 |

### 12.2 内置工具清单

| 工具 | 档 | 参数 | 说明 |
|------|----|------|------|
| `ask_user` ★ | T1 | `question`, `options[]`, `allow_multiple?` | **澄清提问**：暂停循环，UI 弹选项卡，用户选择后作为工具结果返回（等价于本 Agent 的澄清机制） |
| `get_time` ★ | T0 | `timezone?`, `format?` | 系统时间/时区/星期（对齐 `getDeviceInfo` 风格） |
| `get_device_info` | T0 | — | 机型/系统/可用存储 |
| `web_search` | T0 | `query`, `max_results?` | 内置无 Key 引擎（默认）+ 可选自带 Key（Tavily/Exa/Brave/SearXNG…） |
| `web_fetch` | T0 | `url`, `extract_mode?` | 抓取 + 正文提取；**私网/回环地址 DNS 层拦截**；30s 上限；分页防爆上下文 |
| `clipboard_read` / `clipboard_write` | T1 | `text?` | 复用现有桥接能力 |
| `run_code` ★ | T1 | `language`(js/python), `code`, `timeout_ms?` | **程序编译/执行**：沙盒模式下走 proot（node/python3）；无沙盒时 JS 走内置引擎（待 D6），Python 明确报「需沙盒」 |
| `workspace_*` | T1 | 见 §10.1 | 文件读写/补丁/移动/删除/列目录 |
| `terminal_run` | T1/T2 | `command`, `mode`, `timeout_ms?` | 一次性命令执行（非交互）；全局模式需审批 |
| `calendar_read` ★ | T2 | `from`, `to`, `query?` | **查询日历**：`READ_CALENDAR` 运行时权限；返回标题/时间/地点/描述 |
| `calendar_write` ★ | T2 | `title`, `start`, `end`, `location?`, `description?`, `reminder_minutes?` | **编辑日历**：`WRITE_CALENDAR`；插入/更新/删除（删除需审批） |
| `memory_*` ★ | T1 | 见 §7.3 | 记忆读写检索 |
| `skill_list` / `skill_read` | T0 | `name?` | 列出/读取已启用技能正文 |
| `mcp__<server>__<tool>` | T2 | 由服务器声明 | MCP 工具（首次逐调用确认） |
| `send_to_rp_chat` | T2 | `conversationId`, `text` | **预留**：把结果回填 RP 会话（用户已选「不自动同步」，此项默认关、显式触发） |
| `screen_*` / `sms_send` / `contacts_read` | T3 | — | **本轮不实现**，仅登记分级 |

★ = 用户本轮追加要求的工具。

### 12.3 工具契约

```kotlin
interface Tool {
    val name: String
    val description: String
    val tier: ToolTier                 // T0..T3
    val parameters: JsonSchema         // 用于 tool_calls 的 JSON Schema
    suspend fun execute(args: JsonObject, ctx: ToolContext): ToolResult
}

sealed interface ToolResult {
    data class Ok(val text: String, val attachments: List<Attachment> = emptyList()) : ToolResult
    data class Error(val message: String, val retryable: Boolean = false) : ToolResult
    data class NeedUserInput(val prompt: AskUserPrompt) : ToolResult   // ask_user 专用
}
```

`ToolContext` 提供：助手 id、工作区路径、沙盒句柄、审批器、日志器、取消信号。

---

## 13. 权限与安全

### 13.1 三层模型（借鉴 rikkahub-agent 的工程实践，非复制代码）

1. **每工具开关**：T2/T3 默认关闭，需用户在设置里逐项开启并阅读风险说明；
2. **逐调用审批**：写类工具弹审批卡（参数可见、可「本会话始终允许」）；
3. **HARDLINE 底线**：危险操作**无条件**拦截（不受审批影响）——
   `rm -rf /`、`dd if=/dev/*`、`mkfs`、`reboot`、`pm uninstall`、`su`、`curl|sh`、
   覆盖系统目录、修改其他应用数据等。

### 13.2 数据与隐私

- API Key / MCP 密钥：DataStore 加密区 + 内存短生命周期；**禁止写日志**（日志过滤器白名单）；
- `web_fetch`：DNS 解析后拒绝私网/回环/链路本地地址（防 SSRF）；
- 工作区路径穿越防护（§10.1）；
- 审计：所有工具调用落 `tool_audit`（参数脱敏），可在设置中查看/清空；
- 备份：Android 自动备份（`allowBackup=true`）**排除** Key 与工作区（`dataExtractionRules`）。

### 13.3 合规

- 仅侧载分发（nsfw_rules 年龄条款），助手不改变这一约束；
- **不引入任何内容审查/过滤/改写**（硬性规定 1）；
- AGPL 项目只借鉴思路（§0）。

---

## 14. 桥接契约（与 Web 端 / 主 Activity）

| 方法 | 方向 | 签名 | 用途 |
|------|------|------|------|
| `openAssistant()` | JS → Kotlin | `()` | 侧栏入口显示原生层 |
| `getAssistantConfig()` | JS → Kotlin | `(): String(JSON)` | **只读**返回激活供应商的 `providerId/name/apiUrl/apiKey/models/activeModelId`（前端可脱敏展示） |
| `onAssistantVisibilityChanged` | Kotlin → JS | `(visible: Boolean)` | 助手显隐时通知前端（暂停/恢复轮询等） |
| `sendAssistantResult(json)` | Kotlin → JS | `(json)` | 预留：显式回填 RP 会话 |

新增桥接方法按 AGENTS §5.4 流程：`AssistantBridge.kt` + `luzzy-bridge.js` 封装（存在性检测 + 降级）
+ `luzzy-ext.js` 调用 + CHANGELOG 登记。

---

## 15. 待决清单

| # | 待决 | 选项 | 我的建议 |
|---|------|------|---------|
| ~~D1~~ | ~~版本号~~ | — | **已定：并入 v1.5.0**（2026-09-09 用户指正，不跳号；见 §1.3） |
| D2 | 是否拆 Gradle 模块 | 单模块包隔离 / `:assistant` 独立模块 | **单模块**（当前工程纪律），体积失控再拆 |
| D3 | rootfs 分发 | 随包内置最小 Alpine / 首次下载 | **随包内置**（离线可用），重包由用户沙盒内 `apk add` |
| D4 | 向量检索实现 | 纯 Kotlin 余弦 / sqlite-vec | **纯 Kotlin 余弦**（零 NDK、可单测），上万条再评估 |
| D5 | Markdown 渲染 | 第三方库（KMP markdown renderer）/ 自研 AST | **先第三方库**（快），若体积/性能不达标再自研 |
| D6 | 无沙盒时的 JS 执行 | 不提供 / 内置 JS 引擎（QuickJS 等） | **不提供**（明确报「需沙盒」），避免再引一个引擎 |
| D7 | 会话导出格式 | Markdown / JSON / 两者 | **两者**（MD 给人看，JSON 可再导入） |
| D8 | 自动标题 | 本地截断 / 模型生成 | **模型生成**（异步、失败回退截断） |

---

## 16. 分阶段路线

> 每阶段独立可交付、可发版；每阶段结束更新 CHANGELOG + WORKLOG，并跑 verify-markers。
> **Track U（上游同步，§18）独立成线**，与下表并行；唯一排期约束是「U 先于助手侧栏入口 patch 040」。

### U · 上游同步（先做，约 2-4 天）

**交付**：合并上游 **1.9.3**（`d2f2625` → `4aef0bb`，5 文件 +297/−351）+ 2 枚冲突实体手工合并
（index.html / app.js）+ 实体 9 枚按新规程重生成 + 指纹表与硬编码基线点更新 + 回归专项。

**验收**：`node --check` 全 PASS；verify-markers 全绿；实体逆向 9/9 + 端到端 9/9；§18.8 十项
回归全过（尤其**工坊页 JS 执行**与**万相广场一键导入**）；nsfw 块逐字节一致。

**已确认事实**：上游版本 **1.9.3**（公告 id 10207）· 7/9 实体可直接重放 · 2 枚冲突点已定位到字节
（`index.html:2761` 缺空行 / `app.js:771` 广场导入分支）· `character/index.html` 大改版后 007 仍可重放
（`uiHTML` 行未被动）· 无新增 CDN、无新增文件。

### P0 · 骨架与链路（约 3-5 天）

**交付**：`ComposeView` 懒加载覆盖层 + 侧栏 DOM 注入入口 + 空壳导航（助手列表/会话页占位）
+ 桥接 `openAssistant` / `getAssistantConfig` + Room 建库（§4.1 全表）+ 返回键优先级。

**验收**：真机点击侧栏「助手」→ 原生页出现；返回键先关助手；Web 端状态不丢；
冷启动耗时增量 <50ms；`./gradlew assembleRelease` 通过且**仍是单 APK**。

### P1 · 最小可用 Agent（约 1.5-2 周）

**交付**：`AgentLoop` + OpenAI 协议流式 + 工具（`ask_user` / `get_time` / `get_device_info` /
`web_fetch` / `clipboard_*` / `workspace_*`）+ 审批门 + 会话持久化 + 会话列表（日期分组）
+ markdown/思考卡/工具卡/步骤折叠渲染 + 助手设置（提示词/模型/参数/请求体）。

**验收**：多轮工具调用闭环；`ask_user` 能暂停并拿到选择；停止按钮可中断；断网/超时有明确提示；
密钥不出现在日志；会话重启后完整恢复。

### P2 · 记忆 / 技能 / MCP / 历史检索（约 1.5-2 周）

**交付**：记忆三模式 + `memory_*` 工具 + 嵌入接入 + 技能导入/启用 + MCP JSON 导入与 HTTP/SSE
连接 + FTS 关键词检索（按助手/日期/关键词）+ 会话导出。

**验收**：无嵌入模型时全文注入、配置后走相似度（可对比两次请求体）；技能全局/助手级生效；
导入一个 HTTP MCP 服务器并成功调用其工具；搜索能在 1000 条消息下 <200ms 返回。

### P3 · 工作区与双模式终端（约 1.5-2 周）

**交付**：工作区管理 UI + `run_code` / `terminal_run` + proot 沙盒（rootfs 释放 + Alpine + 工作区
bind）+ 全局宿主 shell + 终端 UI（ANSI/滚动/复制/Ctrl-C）+ HARDLINE 黑名单 + 配额。

**验收**：沙盒内 `apk add python3` 后能跑 Python 脚本；全局模式能列 App 目录；危险命令被拦截；
路径穿越被拒绝；工作区配额生效。

### P4 · 增强（按需，可拆多版）

**交付**：Anthropic/Gemini 协议 + stdio MCP（沙盒内）+ 日历工具（`calendar_read/write`）
+ 搜索多引擎 + 子代理/上下文压缩增强 + 审计面板 + 屏幕自动化（T3，默认关，需用户明确要求）。

**验收**：三协议各跑通一轮；日历读写需运行时授权且失败有提示；stdio MCP 在沙盒内可连。

---

## 17. 风险与红线

| # | 风险 | 等级 | 处置 |
|---|------|------|------|
| R1 | AGPL 代码混入 | 高 | 代码评审时核对来源；只读 README/文档，不 clone 进工作区 |
| R2 | AGP 9.2.1 内置 Kotlin + Compose 编译器插件接入 | 中 | P0 第一步先做「空 Compose 页编译通过」验证 |
| R3 | proot 在 Android 16 上的兼容性（ptrace 限制） | 中 | P3 首日做「alpine 启动 + echo」冒烟；不行则降级为受限 shell（方案 B 兜底） |
| R4 | rootfs/APK 体积 | 中 | 随包最小 Alpine；`node/python` 由用户按需 `apk add` |
| R5 | WebView + Compose 共存内存 | 中 | 懒创建 + 隐藏释放；真机低内存实测 |
| R6 | 嵌入接口费用/延迟 | 中 | 结果缓存 + 批量 + 失败降级全文 |
| R7 | MCP 服务器质量不可控 | 中 | 逐调用审批 + 工具允许清单 + 错误可见 |
| R8 | 日历/无障碍等权限被拒 | 低 | 优雅降级：工具不可用并提示如何授权 |
| R9 | 渲染性能（长消息/大量工具卡） | 中 | 节流 + 折叠 + 超长降级纯文本 |
| R10 | 与上游同步的耦合 | 低 | 原生模块与 `assets/rphub/` 零交叉；侧栏入口走登记 patch + 标记 |
| R11 | **上游同步 · `character/index.html` 大改版**（+170/−285） | 高 | 007 CDN 本地化锚点需重定位；同步后专项回归工坊页 JS 执行（v1.3.0 历史缺陷）；见 §18 |
| R12 | **上游同步 · `app.js` 角色卡生成区改动**（+84/−18） | 中 | 三方合并逐块核对；实体以新基线重生成 + 逆向/端到端双验证 |
| R13 | 上游同步与助手入口 patch 的先后顺序 | 中 | 强制「U 先于 patch 040」（§18.9）；否则实体前像失配 |
| R14 | **实体生成规程缺陷（本次新发现）** | 高 | 至少 2 枚实体（index/app）是在**二创工作树**上生成的，不是在上游纯净基线上生成 → 换基线后前像失配（§18.4.1/§18.4.3）。已给出修正规程 §18.6，9 枚全部按新规程重生成 |
| R15 | `app.js` 上游签名变更（`importCharacterData` / `selectCharacter` 改选项对象） | 中 | **运行期才炸**的隐患：U5 必须全局搜索调用点逐个核对，不能只看编译 |

**红线**：不复制 AGPL 代码；不引入审查逻辑；密钥不落日志；路径不越界；危险命令硬拦截；
仅侧载分发；上游文件零裸改（入口 patch 必须登记 + verify-markers 全绿）。

---

## 18. Track U · 上游同步（并入 v1.5.0）

> **触发**：用户指示「把调研合并上游更新也纳入计划，上游也更新了，本版本也要实现」。
> **本节是完整调查结论**（2026-09-09 实测），可直接照 U1-U12 执行，**不需要执行者再自行判断合并方式**。
> 参考克隆已锚定 `4aef0bb`（直连 GitHub fetch 被重置，经 gh-proxy 镜像 fetch 成功）。

### 18.1 上游新版本事实（已确认）

| 项 | 值 |
|----|-----|
| **上游版本号** | **RP-Hub 1.9.3** |
| 公告 id / 更新时间 | `10207` / `09/08 15:30`（built-in-content.js `window.RPHubLatestUpdate`） |
| 公告标题 | `网站公告`（**未变**，patch 038 品牌化继续适用） |
| 最新 commit | `4aef0bb`（2026-09-08 07:34Z） |
| 新增提交数 | 4（`a220eed` → `0360abf` → `aa3d590` → `4aef0bb`） |
| 改动合计 | 5 文件 · **+297 / −351** |

**上游自报新功能（公告原文）**：
1. 角色卡工坊与万相广场新增**一键导入**功能；
2. 支持角色卡工坊**抗截断模式**；
3. **大幅优化 Diff 匹配与智能修改的成功率**；
4. **全面焕新角色卡管理页面**；
5. 优化开屏动画；
6. 优化剧情 UI 面板的出现时机；
7. 修复沉浸模式下宽度异常。

### 18.2 逐文件改动清单（实测）

| 文件 | 行数 | 具体改动 |
|------|------|---------|
| `index.html` | +1 | 第 2771 行 `add-character-modal` 新增 `@generate="showAddCharacterMenu = false; currentView = 'generator'"` |
| `ui-components.js` | +20/−9 | `AddCharacterModal`：emits 加 `generate`；「导入聊天记录」`label` → `button @click="$emit('generate')"`（新增「生成角色卡」入口）；两处文案微调（"新建角色卡"说明、"支持全部分支与聊天数据"） |
| `app.js` | +84/−18 | ① 新增**万相广场一键导入**：`squareImportPending` / `getSquareFrame()` / `RPH_FORUM_READY` / `RPH_FORUM_IMPORT_CARD` 消息分支（含 100MB 上限、PNG 解析、自动切换）；② `onSquareLoad` 广播 `RPHUB_IMPORT_READY`；③ `selectCharacter(index, isNewImport, { silent })` 加 silent 参数并返回布尔；④ `importCharacterData(raw, avatar, { askImageGeneration, activate })` 改选项对象（**签名变更**） |
| `character/index.html` | +170/−285 | 工坊页**Diff 机制重构**：弃用 `<<<<<<<FIND/END/REPLACE` 文本块解析（删 `processDiffs` 约 110 行）→ 改**原生 tool 调用** `edit_character_card`（新增 `characterDiffTool` schema、`processDiffToolCalls`、`requestWorkshopCompletion` 加 `tools/requireTool/onToolCalls`、`applyConfirmedDiffs` 加原文快照校验与倒序替换） |
| `built-in-content.js` | +40/−39 | 预设文案行尾/内容调整（`buildNextResponsePrompt`、`buildActiveToolSystemPrompt`、`story_panels`、`timestamp` 等）；公告换 1.9.3 |

**无新增/删除文件**（`git diff --name-status` 仅 5 个 `M`）。

### 18.3 安全面与合规预检（已实测）

| 检查 | 结果 |
|------|------|
| `nsfw` 块（含 `<nsfw_rules>`）逐字节 | ✅ **完全一致**（起点 offset 23907、长度 599、`-ceq` True） |
| 上游是否新增 CDN 引用（需补 vendor） | ✅ 无（`index.html` / `character/index.html` 的 `+` 行无 cdn/unpkg/jsdelivr/fonts.googleapis） |
| `vendor/` / `assets/fonts/` / `novel/index.html` | ✅ 零改动 |
| 上游是否新增/删除文件 | ✅ 无 |

### 18.4 实体重放实测结果（仓库外干净目录 + 上游 1.9.3 文件）

**方法**：`C:\Temp\luzzy-dryrun5` 建干净 git 仓库 → 落地上游 1.9.3 的 11 个文件 →
逐枚 `git apply --check --ignore-whitespace --directory="app/src/main/assets/rphub" <entity>`。

| 实体 | 结果 |
|------|------|
| `007-029-novel-html.patch` | ✅ OK |
| `007-character-html.patch` | ✅ OK（**关键：工坊页大改版后 007 仍可重放**） |
| `009-035-core-utils-js.patch` | ✅ OK |
| `012-035-runtime-services-js.patch` | ✅ OK |
| `012-035-ui-components-js.patch` | ✅ OK |
| `015-032-api-utils-js.patch` | ✅ OK |
| `016-035-data-services-js.patch` | ✅ OK |
| **`012-035-index-html.patch`** | ❌ **FAIL @ `index.html:2761`** |
| **`012-036-app-js.patch`** | ❌ **FAIL @ `assets/js/app.js:771`** |

**结论：7/9 可重放，2 枚必须手工合并后重生成。**

#### 18.4.1 `index.html` 冲突精确定位

- 失败 hunk：`@@ -2761,11 +3075,329 @@`（preimage 起点 1.9.3 第 2761 行）。
- **根因（已定位到字节）**：该 hunk 的 preimage 上下文**缺少 `</model-selector-modal>` 与
  `<add-character-modal>` 之间的空行**——1.9.3 第 2768 行是「仅含 `\r` 的行」（CRLF 混行），
  而 hunk 期望它直接跟 `<add-character-modal`。即：**实体是在二创工作树（该处被替换为
  patch 012 注释）上生成的，不是在上游纯净基线上生成的**；此前「端到端 9/9 PASS」是在
  已被字符串块/实体改写过的树上验证的，故未暴露。
- **影响面**：仅此 1 个 hunk 失败 → 该实体整体拒绝应用（git apply 全或无）。
- **手工合并方式**：以三方合并结果为准（本次已跑：`git merge-file` 在该区域无冲突，
  上游新增的 `@generate` 行与我方 012/014/035 的插入互不重叠）；具体做法：
  1. 用 1.9.3 `index.html` 为底，套用我方现有 012/014/017/018/021/022/024/025/027/028/032/033/035/037/039 全部改动；
  2. 保留上游第 2771 行 `@generate`（**新增功能，不得丢**）；
  3. 保留我方 `<model-selector-modal>` 的 `:format-model-text="formatModelRefText"` 与紧随其后的
     `<!-- [LuzzyRP patch 012] 供应商管理器 -->` 块（当前工作树第 3078、3084 行位置）；
  4. 合并后 `node --check` 不适用（HTML）→ 用桌面冒烟 + `verify-markers` 校验。
- **重生成实体**：合并完成后按 §18.6 规程以 1.9.3 基线重新生成 `012-035-index-html.patch`。

#### 18.4.2 `app.js` 冲突精确定位

- 失败 hunk：`@@ -771,... @@`（`window.addEventListener('message', ...)` 处理器区）。
- **根因**：上游在 `let workshopImportPending = false;` 之后新增了
  `squareImportPending` / `getSquareFrame()` / 整个 `RPH_FORUM_*` 消息分支（约 44 行），
  并把注释改为 `// Each embedded page may only use its own message bridge.`。
- **影响面**：该 hunk 失败 → 整个 app.js 实体拒绝应用（app.js 含 012/015/017/020/021/022/024/025/026/028/029/031/035/036/037/039 等大量二创改动，**不可丢**）。
- **手工合并方式**（三方合并；本次 `git merge-file` 在该区域同样无冲突）：
  1. 以 1.9.3 `app.js` 为底，套用我方全部二创改动；
  2. **保留上游新增的广场导入分支与 `squareImportPending`**；
  3. 注意上游 `importCharacterData` 与 `selectCharacter` 的**签名变更**——我方若有调用点需同步改为
     选项对象（`{ askImageGeneration }` / `{ activate }` / `{ silent }`），这是**编译期不会报错、
     运行期才炸**的隐患，必须全局搜索调用点逐个核对；
  4. `node --check` 必须 PASS。
- **重生成实体**：合并完成后重新生成 `012-036-app-js.patch`。

#### 18.4.3 预存工具缺陷（本次新发现，须一并修）

- `apply-patches.ps1` 第 84/90 行**硬编码基线 `d2f2625`**（`git -C $refDir show "d2f2625:$RelativePath"`）。
  同步到 1.9.3 后该兜底判定必然失效 → **必须改为可配置基线**（建议：从 `tools/upstream-fingerprints.txt`
  头部解析 commit，或新增 `$BaselineCommit` 参数，默认读参考克隆 `FETCH_HEAD`）。
- 实体生成缺陷：至少 2 枚实体是在二创工作树上生成的（§18.4.1 根因）。**重生成规程必须明确：
  以「上游纯净基线 + 三方合并结果」为对，而不是以二创工作树为对**（§18.6）。

### 18.5 其他需随同步更新的硬编码点

| 文件 | 位置 | 现值 | 应改为 |
|------|------|------|--------|
| `LuzzyBridge.kt` | 第 109 行 | `UPSTREAM_VERSION = "1.9.0"` | `"1.9.3"` |
| `README.md` | 第 16 行 | 上游基线 **1.9.2** | **1.9.3** |
| `README.md` | 第 27 行 | Upstream 徽章 `RP--Hub%201.9.2` | `RP--Hub%201.9.3` |
| `AGENTS.md` | §9 标题 + 正文 | 「上游基线 1.9.2 / commit d2f2625」 | 1.9.3 / `4aef0bb` |
| `tools/upstream-fingerprints.txt` | 头部 | `1.9.2 (commit d2f2625)` | `1.9.3 (commit 4aef0bb)` + 全表 13 项重算 |
| `tools/apply-patches.ps1` | 84/90 行 | 硬编码 `d2f2625` | 参数化/从指纹表头解析 |
| `CHANGELOG.md` | v1.5.0「同步」段 | 待合并 | 改为「已同步 1.9.3」+ 构建结果 |
| `docs/WORKLOG.md` | 会话 26 追记 | — | 追加执行记录 |

### 18.6 实体重生成规程（本次修正版，写入 AGENTS §4.2）

> 会话 25 的规程存在缺陷（以二创工作树为对生成），本次修正：

```
1. 落盘「上游纯净基线」：git -C rp-hub-reference show 4aef0bb:<file> > /tmp/base/<file>
2. 落盘「合并后工作树」：app/src/main/assets/rphub/<file>
3. 生成实体：git diff --no-index --ignore-cr-at-eol /tmp/base/<file> <worktree-file>
   → 头路径改写为 a/<relpath> … b/<relpath>（去掉临时目录前缀）
4. 验证（仓库外干净目录）：
   a. 逆向：上游基线 → git apply 实体 → 与合并后工作树 LF 归一逐字节比对（须为空 diff）
   b. 端到端：上游基线全量 → apply-patches.ps1 实跑 → 9 枚全 [OK] 且结果与工作树一致
5. 前像 blob id 必须等于「上游纯净基线」的 LF 归一 blob id（脚本已按此判定）
```

### 18.7 执行清单 U1-U12（照此执行，无需临场判断）

```
U1  参考克隆锚定 4aef0bb（已完成）；记录 commit 与公告版本号 1.9.3
U2  覆盖上游文件到 assets/rphub/：index.html、assets/js/{app,ui-components,built-in-content}.js、
    character/index.html（其余文件零改动，不必动；vendor/ fonts/ 排除）
U3  字符串块段重放（apply-patches 字符串段）：001-006/009/010/011b —— 预期全 OK
U4  实体段重放：7 枚直接 OK；012-035-index-html 与 012-036-app-js 按 §18.4.1/§18.4.2 手工合并
U5  全局搜索 app.js 中 importCharacterData / selectCharacter 的调用点，按新签名（选项对象）修正
U6  node --check 全部 JS（rphub 9 + ext 4）必须 PASS
U7  修正 §18.5 全部硬编码点（LuzzyBridge / README / AGENTS / apply-patches 基线参数化）
U8  指纹表全表重算（以 4aef0bb 工作树字节），R1/R2 复核 nsfw 与 styles.css
U9  实体以「1.9.3 纯净基线 + 合并结果」重新生成全部 9 枚（§18.6），逆向 9/9 + 端到端 9/9 双验证
U10 verify-markers 全绿（硬性规定 10）
U11 回归实测：§6.2 全量 + 上游新功能专项（见 §18.8）
U12 文档同步：CHANGELOG（同步段改「已同步 1.9.3」）、README 徽章/基线、AGENTS §9 快照、WORKLOG
```

### 18.8 回归专项清单（上游 1.9.3 新功能 + 历史盲区）

| # | 项目 | 要点 |
|---|------|------|
| 1 | **角色卡工坊页 JS 执行** | v1.3.0 曾因 007 残缺 hunk 导致整页不执行；本次工坊页大改，必须首屏走查（打开工坊页 → 控制台无报错 → 按钮可点） |
| 2 | 工坊 **Diff 工具调用** | 新版走 `edit_character_card` 原生 tool（`requireTool: true`）——需用支持 tool 的模型实测一次修改流程 |
| 3 | 工坊 **抗截断模式** | 公告新功能，实测长输出续写 |
| 4 | **万相广场一键导入** | 新消息桥（`RPH_FORUM_*`）→ 导入 PNG 角色卡并自动切换；断网/超大文件（>100MB）报错路径 |
| 5 | **「生成角色卡」入口** | 角色卡弹窗新按钮 → 跳 `currentView='generator'` |
| 6 | 角色卡管理页焕新 | 全面改版，走查列表/搜索/批量删除/CharacterDeck |
| 7 | 开屏动画 / 剧情面板时机 / 沉浸模式宽度 | 上游三项优化，走查我方「开卷」开屏与 028/037/039 等既有改动无冲突 |
| 8 | 既有二创回归 | 供应商管理器/三协议/记忆链路（016/020/026/031/036/017）/用量折线图/外观页/关于页检索高亮/公告品牌化 |
| 9 | 数据兼容 | 老 localStorage/IndexedDB 数据可读（§6.2） |
| 10 | 断网可用性 | 飞行模式走查（vendor 全本地化，无新增 CDN） |

### 18.9 排期与顺序约束

| 约束 | 说明 |
|------|------|
| **U 先于 W2 的侧栏入口 patch 040** | patch 040 要改 `ui-components.js`；先做则同步时需再重放一次，且实体前像会失配 |
| U 可与 W2 的 P0-P2 并行 | 原生 Kotlin 侧与 `assets/rphub/` 零交叉 |
| U 完成后重跑桌面冒烟 | 工坊页大改，须专项 |
| **U 完成前不得发版** | 上游新功能与既有补丁必须同时稳定 |

### 18.10 已决 / 待决

| # | 事项 | 状态 |
|---|------|------|
| U-A | 上游版本号 | ✅ 已确认 **1.9.3** |
| U-B | `character/index.html` 大改版后 007 是否可重放 | ✅ **可**（实测 OK；`uiHTML` 行未被上游改动） |
| U-C | 是否拆版 | ✅ **已决：不拆**（用户 2026-09-09 指示「先同步、再做助手，最后一次性发版」） |
| U-D | `apply-patches.ps1` 硬编码基线如何参数化 | ⏳ 建议从指纹表头解析（U7 实施） |

---

## 19. 参考来源

- [rikkahub/rikkahub](https://github.com/rikkahub/rikkahub)（AGPL-3.0）—— 架构与能力参考
- [ExTV/rikkahub-agent](https://github.com/ExTV/rikkahub-agent)（AGPL-3.0）—— 工具分级、审批门、HARDLINE、工作区/终端实践参考
- [PRoot](https://proot-me.github.io/) / [Termux PRoot Wiki](https://wiki.termux.com/wiki/PRoot) / [proot-distro](https://github.com/termux/proot-distro) —— 无 root 沙盒
- [Alpine Linux Downloads](https://alpinelinux.org/downloads/) —— rootfs 来源
- [sqlite-vec Android/iOS](https://alexgarcia.xyz/sqlite-vec/android-ios.html) —— 备选向量检索
- [Android Calendar Provider](https://developer.android.com/identity/providers/calendar-provider) —— 日历工具权限与 API
- [multiplatform-markdown-renderer](https://github.com/mikepenz/multiplatform-markdown-renderer) —— Markdown 渲染备选
- [Compose 性能](https://developer.android.com/develop/ui/compose/performance) —— 渲染节流与列表优化
- [MCP 传输：stdio / SSE / Streamable HTTP](https://gingerlabs.ai/blog/mcp-transport-comparison) —— MCP 连接选型
- [AGP 9.0 内置 Kotlin 迁移](https://blog.jetbrains.com/kotlin/2026/01/update-your-projects-for-agp9/) —— 构建接入前提
- 本项目：[`docs/RESEARCH-assistant-native-agent.md`](RESEARCH-assistant-native-agent.md)（前置调研）·
  `app/src/main/java/com/luzzymeow/luzzyrp/`（现有壳）· `assets/ext/`（扩展层约定）
