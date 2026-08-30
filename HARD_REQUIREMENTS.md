# LuzzyRP 硬性规定（HARD_REQUIREMENTS）

> **本文件是项目的最高约束**，位于仓库根目录，任何开发 Agent 接手任务前必须完整阅读并遵守。
> 违反任何一条即为不合格交付。发版前必须逐项通过 `docs/INVARIANTS-CHECKLIST.md` 自检。

---

## 一、十二条硬性规定

### 规定 1 · 流式输出不可破坏
所有模式下，模型的输出必须是**模型原生的流式输出**：模型出一个字，思考卡片内和正文气泡内就出一个字。
**1 字 = 1 次状态更新**，禁止模拟打字机、禁止节流/批量 flush、禁止非流式请求伪装流式。
- 守护落点：`core/ai` 各协议 `streamText` 实现（文件头 `[INVARIANT-STREAMING]` 注释块）+ `app/.../service/ChatService.kt` + `di/DataSourceModule.kt`（OkHttp 超时配置）。
- 回归测试：`KvPrefixStabilityGoldenTest`（前缀稳定）+ 人工验收（对 DeepSeek/方舟实测逐字）。

### 规定 2 · Agentic 模式不可破坏
所有模式都要支持 Agentic 多轮思考与多轮工具调用；除被动工具外，硬性要求每模式模型进行 **>2 轮次的思考** 和 **>1 轮次的主动工具调用**；必须持续强化/更新内置提示词（不得依赖 `tool_choice` 强制）。
- 守护落点：`app/.../data/ai/GenerationHandler.kt`（`[INVARIANT-AGENTIC]` 注释块）+ `app/.../data/ai/prompts/AgenticProtocol.kt`（内置提示词强化段）。
- 回归测试：`GenerationLoopTest`。

### 规定 3 · NSFW 提示词不可触碰
`app/.../data/ai/prompts/NsfwBlock.kt` 内的 NSFW 内置提示词内容（当前为占位，由用户手动填写），**禁止任何审查、过滤、改写逻辑触碰该内容**；不得添加内容过滤器、敏感词拦截、输出改写。
- 守护落点：`NsfwBlock.kt` 文件头警示注释。

### 规定 4 · 代码守护注释
凡涉及代码改写与开发，必须在不变性落点代码内部写**详细中文注释**，明确：
- `[INVARIANT-xxx]` 标记 + 禁止修改的范围；
- 允许修改的范围；
- 为什么（对应本文件哪条规定）。
让后续开发 Agent 清楚哪些内容禁止修改。

### 规定 5 · 技术栈锁定
项目源码严格以 **Android 原生 Kotlin + Jetpack Compose** 开发（含附属功能、工具与 UI）。参考成品：
- https://github.com/rikkahub/rikkahub
- https://github.com/CherryHQ/cherry-studio
- https://github.com/STA1N156/RP-Hub
- https://github.com/lobehub/lobehub
- https://github.com/SillyTavern/SillyTavern

### 规定 6 · 图标 / 字体 / 动画
- 所有新容器、按钮、弹窗、页面、下拉扩展框等前端内容的 Icon **仅限** 从 `docs/game-icon-pack` 与 `docs/lobe-ui-master` 提取使用（运行时经 `GameIcons.kt` / `LuzzyIcons.kt` 注册表），**禁止自行绘画图标样式**。
- 系统规定内置字体仅限 `docs/AlibabaPuHuiTi-3` 与 `docs/AlibabaSans`；**只有非简体中文和英语时回退系统默认字体**。
- 整体 UI 保持一致性、美观性；所有新 UI 必须具备**进入 / 交互 / 退出三态丝滑动画**（令牌：进入 300ms / 交互 150ms / 退出 195ms），以最高质量与精度做最佳视觉效果。
- 守护落点：`app/.../ui/theme/`（AuroraColor / MotionTokens / LuzzyMixedFontFamily）+ `app/.../ui/icons/`。

### 规定 7 · 禁止占位符
不得遗留任何占位符；所有工作、开发代码以**最完整、功能最全面**为质量标准，不得简化与占位。（唯一例外：`NsfwBlock.kt` 内容由用户手动填写。）

### 规定 8 · KV 缓存命中不可破坏
版本新功能升级时，需确保新功能无法破坏模型输入缓存命中 / KV 缓存机制。要求：
- 消息序列化稳定化（固定字段序、紧凑 JSON、时间戳等易变字段不进前缀）；
- 系统提示按「稳定前缀 / 半稳定段 / 动态尾段」三层组装；
- 工具结果原地回填（不新建 TOOL 消息）。
- 回归测试：`KvPrefixStabilityGoldenTest`（同一历史两次组装逐字节相等）。

### 规定 9 · 工具变更同步
凡涉及任何工具（SKILL 工具、MCP 工具、内置工具等）的更新，必须同步 `CHANGELOG.md`，并同步 agentic 请求阶段的内部工具提示词（如需）。

### 规定 10 · 版本文档同步
新版本内容必须同步更新 `CHANGELOG.md` 与 `README.md`（要求美观且极其详细）。

### 规定 11 · 工作区整洁
整理项目工作区，清理冗余文件，做好文件分类（docs 内：plan / task-archive / archive / 参考资料分区）。

### 规定 12 · 发布流程
`android` 目录资源同步最新构建产物确认可编译后，编译新版本 APK，随后提交推送远程仓库；按仓库旧版本 release 排版格式编写新的、美观且极其详细的 release 内容推送（**仅稳定版更新附 APK**）。

---

## 二、真流式 6 大不变性（规定 1 的技术展开）

| # | 不变性 | 守护落点 |
|---|--------|----------|
| S1 | `Provider<T>` + `ProviderManager` 管理多 LLM 供应商 | `core/ai/.../provider/Provider.kt`、`ProviderManager.kt` |
| S2 | `streamText` 返回 `Flow<MessageChunk>` | 同上 |
| S3 | `callbackFlow` + `okhttp3.sse.EventSources.createFactory().newEventSource()`；`onEvent → trySend` 逐 event 零节流；`awaitClose { eventSource.cancel() }` | `core/ai/.../openai/ChatCompletionsAPI.kt` 等三协议 |
| S4 | OkHttp `readTimeout = 10 MINUTES` | `app/.../di/DataSourceModule.kt` |
| S5 | `MutableStateFlow<Conversation>` 单一真源 + `collectAsStateWithLifecycle()` | `app/.../service/ChatService.kt`、各 ViewModel |
| S6 | 1 字 = 1 次更新（逐 event 直通 StateFlow，无节流无动画缓冲） | S3 + S5 组合保证 |

## 三、Agentic 6 大不变性（规定 2 的技术展开）

| # | 不变性 | 守护落点 |
|---|--------|----------|
| A1 | `GenerationHandler.generateText`：`maxSteps = 256` for 循环 | `app/.../data/ai/GenerationHandler.kt` |
| A2 | 工具结果原地回填（不新建 TOOL 消息，保持 KV 缓存命中） | 同上 |
| A3 | 三 break 条件：不调工具 / 等待审批 / 无可执行工具 | 同上 |
| A4 | `ToolApprovalState` 五状态机（Auto/Pending/Approved/Denied/Answered）续跑 | `core/model/.../ToolApprovalState.kt` + ChatService |
| A5 | 禁止 `tool_choice = required`（仅 auto / none） | 各协议实现 |
| A6 | 被动工具通过系统提示注入，不预执行 | `PromptAssembler` + `PassiveTools.kt` |
