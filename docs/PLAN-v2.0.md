# PLAN-v2.0 · KV 前缀缓存最大化 × Kotlin 原生聊天后端（B 方案 · 薄切）

> **本版唯一主计划**（v2.0）。立档：2026-09-11（会话 62）。
> 取代 `docs/PLAN-v1.6.0-dsh.md`（转为历史存档）。
>
> **用户拍板口径（原文）**：「我想让 web view 作为前端显示样式，也就是目前的样式不用变动，
> 而后端采用原生 Kotlin……对齐参考项目 rikkahub 和 dsh 的架构设计」「全力做 B 方案，进行深度调研，
> 确定工作计划，确定工作版本为 V2.0，确保 KV 缓存收益最大化，确保现有工具正常调用且不损失精度和质量，
> 确保用户无感知升级」，并授权「**允许你改上游文件**」（仍走登记 patch，硬性规定 2/10 不变）、
> 「**不需要澄清提问，按你推荐的路线走**」。
>
> 前置：原生「助手」模块已彻底移除（`0392b662`）；本计划全部落在
> `app/src/main/assets/rphub/**`（登记 patch）与 `ext/**`、`app/src/main/java/**`（自有代码）。

---

## 0. 调研结论（三条并行深度调研，全部带 file:line 证据）

### 0.1 前缀不稳定的真实清单（**推翻了 3 条既有假设**）

原 `PLAN-v1.6.0-dsh.md` §1.2 列的 7 条里，**3 条是错的**，另有 3 条是调研中新发现的：

| # | 原假设 | 裁决 | 证据 |
|---|---|---|---|
| ① | depth≥1 时 `<active_tools>` 被整段替换 | **错** | `app.js:6056` 的阈值是 `< 4` 不是 `< 1`；`<active_tools>` 文本（`built-in-content.js:120-131`）无 depth 相关输入 → depth 0/1/2/3 **逐字节相同**。只有 depth **4** 才消失并被替换成一句提醒（`app.js:6167`） |
| ② | depth≥1 时 `tools` 字段消失 | **错（且方向相反）** | depth 0/1/2/3 都带 tools；且因 `requireTool` 在 depth≥1 翻假（`app.js:6696` → `api-utils.js:262`），`tools` 在 depth≥1 **多出 1 个 `output_reply` 定义**（4→5） |
| ③ | 检索提醒只在 depth 0 追加到末位 user | **对** | `app.js:6459` + `appendActiveToolReminderToLatestUserMessage`（`app.js:5964-5982`） |
| ④ | 世界书/向量召回按「距尾 depth」插入 + 相邻同角色合并 | **对** | `findDepthIndex`（`data-services.js:990-1002`）、splice（`1004-1012`/`1014-1042`）、合并（`658-701`） |
| ⑤ | UI 模板块插在 system 中间 | **对** | `app.js:6169-6173`（在 `[User Info]` 之后、COT 之前） |
| ⑤b | 副模型结果带 `new Date().toISOString()` 进 messages | **错** | `app.js:5645` 只写 `window.__RPHubLastUiTemplateAnalysis`（`5651`）与 `console.info`（`5652`），**从不进 `messages`**。请求路径全仓无时钟值 |
| ⑥ | 正则按「距尾深度」生效 → 老消息被重新正则化 | **部分对** | 机制属实，但在 **prompt 序列化遍**（`app.js:6460-6467` 传 `depth: array.length-1-index`，过滤在 `4026-4027`）；display 遍不传 depth（`runtime-services.js:24`） |
| ⑦ | 最近 2 条 assistant 的 thinking 回填是滑动窗口 | **对** | `app.js:6247-6255`（窗口）→ `cleanSourceContent`（`6388-6409`）剥离后仅对窗口成员回填。**但 depth 0→1 稳定**（`continuationTargetMessage` 在 `6251` 被 skip），只在**跨用户轮**滑动 |

**调研新发现（原清单没有，但同样是断点）：**

- **⑧ 世界书触发概率门用 `Math.random()`**（`data-services.js:805, 813`）→ **同一轮内两次调用 system 内容可能不同**（非确定性，缓存必然失配）
- **⑨ 原生图片 parts 是尾相对的**（`app.js:5177-5192` + `latestImageUserHistoryIndex` `5166-5174`）→ 对话变长后**老图片消息丢失 `image_url` parts**，回退成 `<user_image_context>` 文本
- **⑩ `filterBlockedStyleText` 在读时重写 assistant 历史**（`app.js:1467-1500`，经 `1501-1505`）
- **⑪ `cover` 模式工具结果「原位改写」此前所有 tool 消息**（`app.js:8565-8569`）
- **确认：客户端完全没有 token 预算/裁剪**（`tiktoken|countTokens|estimateTokens|maxContext|contextLimit` 全仓 0 命中；`budgetedEntries` 是误名，实为 `resolvedEntries`）。唯一的「缩短」是记忆压缩，而它**搬移前缀**。

### 0.2 「改造前」基线（**已实测**，桌面 Chromium + 真实上游发送链路 + fetch 打桩）

干净配置（无世界书 / 无记忆 / 无 UI 模板 / tools 稳定 / system 哈希恒定 / 6 个连续用户轮）：

| 指标 | 实测值 |
|---|---|
| 请求数 | 6（每轮 1 次，无重试） |
| `system` 长度 / 哈希 | 8170 / 恒定不变 ✅ |
| `tools` 数 | 2（恒定） |
| 相邻两轮**公共前缀占比** | 平均 **0.9101**、末轮 0.9107 |
| 断裂位置 | 稳定落在**距尾约 2 条 assistant**处 —— 与假设 ③（提醒只挂在最新 user 消息上，下一轮该消息变形）和 ⑦（thinking 窗口滑动）**完全吻合** |

> ⚠️ **这是最乐观场景**。真实用户开世界书（`at_depth` 距尾插入）+ 向量记忆时，
> 插入点每轮漂移会让其后**整段**失配，预期远低于 0.91 —— W1 完成后需补测该场景。

**含义**：即便在最优配置下，每轮也有 ~9% 的前缀白付（未命中缓存）。修好 ③⑦ 后应逼近 ~99%。

### 0.3 Kotlin 侧可回收资产（**远超预期**）

| 项 | 结论 |
|---|---|
| 库存 | `0392b662^` 可完整取回 **155 个 .kt / 21,207 行**（main 120 / test 35），**非**此前记录的「约 50 个」 |
| `domain/llm/`（10 文件 1,463 行） | **多数 REUSE AS-IS**：`SseFrames.kt`（零 import）、`JsonLenient.kt`、`OpenAiTransport.kt`、`AnthropicWire/Transport.kt`、`GeminiWire/Transport.kt`、`RoutingTransport.kt`。唯一需编辑的是 `SseClient.kt`（OkHttp + `invokeOnCompletion{ call.cancel() }` + `flowOn(IO)`） |
| `domain/loop/BudgetGuard.kt`（100 行） | **零框架依赖**，REUSE AS-IS（四道闸：轮数/工具超时/总时长/token 预算） |
| `domain/tool/`（16 文件 1,859 行） | 契约层全部 REUSE AS-IS（`Tool.kt`/`Schema.kt`/`ToolRegistry.kt`/`ApprovalGate.kt`/`AuditSink.kt`/`HardlineGuard.kt`/`SsrfGuard.kt`/`Ports.kt`） |
| **需要的依赖** | **只有 2 个**：`kotlinx-serialization-json`、`okhttp`。**协程已在**（`kotlinx-coroutines-android 1.11.0`，是删除提交里唯一幸存的原助手依赖） |
| **编译器插件** | **不需要** —— 155 个删除文件里 `@Serializable` 出现 **0** 次，全是 JSON 树 API。这砍掉一个插件 + KSP 类风险 |
| 现存源码 | 仅 6 个 `.kt` / 540 行；`app/src/test/` 与 `androidTest/` **目录不存在** |
| 桥接 | `LuzzyBridge` 8 个 `@JavascriptInterface`，注册于 `MainActivity.kt:63`（JSI 名 `"LuzzyBridge"`） |
| WebView 暂停 | `pauseTimers/onPause/resumeTimers/onResume` **全仓 0 命中**（删除前在 `MainActivity.kt:248/249/267/268`）→ AGENTS §7 那条坑当前不复现 |
| ⚠️ 首要验证点 | `kotlinx-serialization-json` 与 AGP 9.2.1 内置 Kotlin（仓库注释称 2.2.10）的兼容性**未验证**（助手时代项目用 Kotlin 2.4.0） |

### 0.4 JS 传输层线格式全图（Kotlin 保真的对照基准）

- 三处 `fetch`：`api-utils.js:82`（OpenAI，`Authorization: Bearer`）/ `459`（Anthropic，`x-api-key` + `anthropic-version: 2023-06-01`）/ `633`（Gemini，key 在 **query string**）。
- **Anthropic 的 URL 是「剥离」不是「追加」**（`api-utils.js:378-385`）：配置的 `apiUrl` 必须已是完整端点；而编辑器 placeholder `https://api.anthropic.com`（`app.js:4769`）会 404。Gemini 相反（自行追加 `/v1beta/models/{id}:…`）。
- **工具调用只在 OpenAI 协议实现**（`tool_use|tool_result|functionResponse|functionCall` 全仓 **0 命中**）；Anthropic/Gemini 适配器**连 `tools` 请求字段都没有**。
- **潜伏缺陷（高置信，静态分析）**：`app.js:6758` `if (responseResult.toolCalls.length)` **无保护**，而 Anthropic/Gemini 的返回（`api-utils.js:524/533/574/680/683/724`）**没有 `toolCalls` 键** → **任何一次成功的 Anthropic/Gemini 回复都会抛 TypeError**（`6738` 有 `activeToolDepth > 0` 短路保护，`6758` 没有）。该行来自上游 1.9.2 覆盖（`757d4e6c`），从未与 LuzzyRP 适配器对账。
- Anthropic/Gemini SSE 解析器比 OpenAI 弱：**要求 `'data: '` 带空格**、不支持多行 data、**无尾部 flush**（最后一个不完整行被丢弃）、解析失败静默吞。
- **无超时**（Anthropic/Gemini）、无重试（除 OpenAI 空工具回复重试 3 次）。
- Anthropic usage **覆盖而非合并** → `message_start` 的 input tokens **永久丢失**。

---

## 1. 架构决策：B 方案 = **薄切**

```
┌──────────────── WebView（JS，样式零改动）────────────────┐
│  Vue 3 视图 + 上下文装配 generateResponse → apiMessages    │
│  ↑ 上下文真源留在 JS：角色卡/世界书/记忆/预设/正则都在      │
│    IndexedDB 与上游 JS 里，远端/原生无法替代                │
│  ext/luzzy-prefix-guard.js  前缀观测                       │
└───────────────────────┬───────────────────────────────────┘
                        │ ① LuzzyBridge.chatStart(planJson) → jobId
                        │ ② 原生 → JS: Luzzy.chatNative.onEvent(jobId, evt)
                        │ ③ JS → 原生: LuzzyBridge.chatAbort(jobId)
┌───────────────────────┴───────────────────────────────────┐
│                    Kotlin 壳后端（新增）                    │
│  ChatTransport  OkHttp + SSE 解帧（三协议统一）             │
│  WireAdapter    OpenAI / Anthropic / Gemini 序列化（复用）  │
│  StreamAssembler 文本 / 思考 / tool_calls 增量组装          │
│  ChatLoop       续写调度 + 取消 + 超时 + 重试               │
│  CacheLedger    缓存命中记账 → 回推 JS 供用量页/门禁         │
└───────────────────────────────────────────────────────────┘
```

**为什么薄切（而不是把上下文装配也搬进 Kotlin）**：
RP 的工具与上下文素材（角色卡、世界书、记忆分片、正则、UI 模板、IndexedDB）**全部是 JS 侧本地能力**，
远端/原生无法执行。把装配搬进 Kotlin 只会制造**永久双真源**，而 KV 缓存收益**与循环位置正交**
（服务端只看见 `messages` 的字节）。薄切 = 拿到后端化的全部收益，零双真源。

**「无感知升级」的四条硬保证**：
1. **零数据迁移**：IndexedDB / localStorage 结构**一个字段都不动**，仍由 JS 独占。
2. **零重配置**：供应商/密钥/模型仍从原 `settings` 读取。
3. **可降级**：`settings.transportMode`（`auto|js|native`，默认 `auto`）。`auto` 下原生不可用/初始化
   失败/首帧出错 → **自动回落现有 JS 代码路径**（该路径**保留不删不改**）。
4. **同签名同包名**：`assembleRelease` + `apksigner verify --print-certs` 指纹与 v1.4.0 一致。

---

## 2. 工作节点（W0–W6，一次性全做）

| 节点 | 内容 | patch | 可验证性 |
|---|---|---|---|
| **W0** | 观测层 + 门禁 + 基线 | 047（挂载） | 桌面门禁 + 真机 |
| **W1** | 前缀稳定化 | **047** | 桌面门禁（含负控） |
| **W2** | 缓存可见性（Anthropic 断点 + 用量页命中率） | **048** | 桌面门禁 |
| **W3** | 潜伏缺陷修复（Anthropic/Gemini 三条） | **051** | 桌面门禁 + JVM 单测 |
| **W4** | Kotlin 传输后端 + 桥接 + 特性开关 | **050** | **JVM 单测（真 socket）+ 构建** |
| **W5** | 全门禁 + 构建 + 文档 | — | 全套 |
| **W6** | 真机实测（需设备） | — | 真机 |

> **顺序理由（硬约束）**：W1/W2 必须在 W4 **之前**。因为前缀观测层挂在 `window.fetch` 上
> （`api-utils.js` 三处都调全局 `fetch`，全仓无提前捕获），**传输一旦搬进 Kotlin，该挂钩点即消失**。
> 所以在 JS 侧先把 KV 收益做实并测出前后对比，再迁移传输；W4 完成后由 `CacheLedger` 接管观测。

---

## 3. 逐节点任务卡

### W1 · 前缀稳定化（**patch 047**，最高价值）

| # | 改动 | 点位 | 为什么安全 |
|---|---|---|---|
| A1 | **移除重复的检索提醒注入**：该句与 `<active_tools>` 内 `built-in-content.js:123` 的 `${reminder}` **同源同文本**，属冗余；移除后信息量为零损失，却消除「上一轮那条 user 消息每轮变形」 | `app.js:6459`（条件包起来或直接停用该调用） | 提醒文本仍在 system 里，模型可见性不变 |
| A2 | **thinking 回填窗口改为非滑动**：不再「最近 2 条」按尾部计数，改为**在消息进入上下文时确定并冻结**（判据不依赖对话长度） | `app.js:6247-6255` + `6388-6409` | 保留 COT 连续性意图，去掉滑动 |
| A3 | **世界书 / 向量召回 / at_depth 插入点改「绝对锚」**：不再按距尾 depth 计算落点，改为按**绝对消息序号**（进入上下文即固定） | `data-services.js:990-1012`、`1014-1042` | 只改**注入位置**，不改触发条件与内容 |
| A4 | **请求期正则的 depth 判据由「距尾」改「绝对索引」** | `app.js:6460-6467` | `app.js:4026-4027` 的 minDepth/maxDepth 语义改为绝对深度 |
| A5 | **世界书概率门确定化**：`Math.random()` 改为**按轮次定种子**，保证同一轮内多次调用结果一致 | `data-services.js:805, 813` | 概率分布不变，只去掉「同一轮两次调用不一致」 |

> **不做**：不改过滤 / 正则 / 世界书 / 记忆的**语义**，只改**注入位置与时机**（不损失精度与质量）。

### W2 · 缓存可见性（**patch 048**）

| # | 改动 | 点位 |
|---|---|---|
| B1 | Anthropic 路径加 `cache_control: {type:'ephemeral'}` 断点（system 末 + 稳定历史边界） | `api-utils.js` Anthropic 分支 |
| B2 | 用量页增「缓存命中率」列 = `cacheReadTokens / inputTokens` | `ui-components.js` 用量区（`cacheReadTokens` 已被 `normalizeApiUsage` 归一，只是从未展示命中率） |

> OpenAI 兼容路径（DeepSeek/STA1N 等）是**自动前缀缓存**，不需要指令 —— 靠 W1 把前缀稳住即可。

### W3 · 潜伏缺陷修复（**patch 051**）

| # | 缺陷 | 修复 | 点位 |
|---|---|---|---|
| C1 | **`app.js:6758` 无保护解引用 `responseResult.toolCalls`** → Anthropic/Gemini 每次成功回复必抛 TypeError | 改为可选链 + 空数组兜底（与 `6738` 的保护口径一致） | `app.js:6758` |
| C2 | Anthropic/Gemini SSE **无尾部 flush** → 最后一个不完整行被丢弃 | 补 `if (buffer) 处理尾帧`（与 OpenAI 路径 `344` 对齐） | `api-utils.js:573`、`729` |
| C3 | Anthropic usage **覆盖而非合并** → `message_start` 的 input tokens 丢失 | 改为字段级合并（`input_tokens` 保留、`output_tokens` 更新） | `api-utils.js:516`、`564` |

### W4 · Kotlin 传输后端（**patch 050** + 桥接）

**依赖接入**（⚠️ 先验证兼容性）：`kotlinx-serialization-json` + `okhttp`；**不加编译器插件**。

**回收清单（从 `0392b662^` 取回并改造）**：
- `domain/llm/`：`SseFrames.kt`、`JsonLenient.kt`、`LlmTransport.kt`、`SseClient.kt`、`OpenAiTransport.kt`、`AnthropicWire.kt`、`AnthropicTransport.kt`、`GeminiWire.kt`、`GeminiTransport.kt`、`RoutingTransport.kt`
- `domain/loop/`：`BudgetGuard.kt`
- `domain/tool/`：`Tool.kt`、`Schema.kt`（仅工具调用载荷所需部分）

**新增**：
- `chat/ChatBridge.kt`：`@JavascriptInterface chatStart/chatAbort/chatSnapshot`
- `chat/ChatPlanCodec.kt`：解析 JS 传入的 plan（`messages`/`tools`/`protocol`/`baseUrl`/`apiKey`/`model`/…）
- `chat/ChatEventPump.kt`：把 `Flow<LlmDelta>` 批量推回 JS（复用 patch 044/045 的「低频提交」哲学，避免每帧 `evaluateJavascript`）
- `CacheLedger.kt`：把 `usage` 的缓存字段回推 JS

**JS 侧**：`ext/luzzy-chat-native.js`（自有文件，桥接封装 + `auto` 回落判定），
并在 `app.js` 的请求点加**分支**（`transportMode==='native' && 桥可用` → 走原生；否则原路径），
由 `luzzy-prefix-guard.js` 同款「静默降级」纪律保证不白屏。

**JVM 单测（`app/src/test/`，真 socket，无需设备）**：
- 取回 `RawSseServer.kt`（零依赖 JDK ServerSocket 测试服务器）+ 改造 `OpenAiTransportTest.kt`
- 新增**三协议线格式保真测试**：同一份 plan → JS 序列化 JSON 与 Kotlin 序列化 JSON **逐字段等价**
- 新增 `ToolCallAccumulator` 分片拼装测试（含 name 跨帧、id 冲突、非法 index）

### W5 · 门禁与验收
- `tools/prefix-cache-test.cjs`（新增，含负控）
- 既有三门禁：`page-handoff-test.cjs` / `stream-render-test.cjs` / `model-list-test.cjs`
- `tools/verify-markers.ps1` 全绿（新增 047/048/050/051 标记项）
- 实体重生成 + 双验证（逆向 + 端到端）
- `./gradlew :app:assembleRelease` + `:app:testDebugUnitTest`
- `apksigner verify --print-certs` 指纹核对
- 文档：CHANGELOG / WORKLOG / AGENTS §4.2 登记表 / 本 PLAN 勾选

### W6 · 真机（**当前无设备连接**，`adb devices` 为空）
- 覆盖安装 → 连续多轮读 `cached_tokens / prompt_tokens` 前后对比
- 后台/锁屏保持生成（Kotlin 传输的核心收益之一）

---

## 4. 风险与回滚

| 风险 | 等级 | 缓解 |
|---|---|---|
| A3/A4 改变注入位置，模型对位置敏感 | 中 | 只改位置不改内容；真机抽测 RP 回复质量；扩展层开关可强制旧行为 |
| A2 让 thinking 常驻 → 上下文变长 | 中 | 与既有记忆压缩配套；只在「进入上下文时」冻结一次，不额外增长 |
| kotlinx-serialization 与内置 Kotlin 2.2.10 不兼容 | 中 | **先做最小验证构建**；不兼容则退回 `org.json`（平台内建，零依赖）并只重写 JSON 访问层 |
| C1 修复改变 Anthropic/Gemini 行为（从「报错」变「正常」） | 低 | 这是修 bug；行为变化只可能是变好 |
| 原生传输与 JS 语义漂移 | **高** | 特性开关默认 `auto` + 首帧出错即回落 + 三协议等价单测 |
| 前缀门禁误报 | 低 | 门禁断言「公共前缀占比 ≥ 阈值」而非逐字节全等 |

**回滚**：每个 patch 独立编号、独立提交；`git revert` 单个即可（实体重放通道保证同步可复现）。
W4 因有 `transportMode` 开关，可**运行时**回落到 JS，无需发版。

---

## 5. 明确不做

- 不引入任何第三方 SDK（除 W4 必需的 `kotlinx-serialization-json` / `okhttp`）；
- 不动 `built-in-content.js` 内 `nsfw_rules`（硬性规定 1）；
- **不为缓存牺牲功能**：世界书 / 记忆 / 正则 / 过滤语义一律不变，只改注入位置与时机；
- 不为 Anthropic/Gemini **新增**工具调用支持（那是**新功能**，不是本次范围；本次只修它们被 TypeError 打断的问题）；
- 不改 UI 样式（用户明确要求「样式不用变动」）；新增的用量页命中率列沿用既有 token 与组件配方。

---

## 6. 进度勾选（执行时更新）

- [x] W0 观测层 `ext/luzzy-prefix-guard.js` + patch 047 挂载
- [x] W0 「改造前」基线实测（6 轮，平均前缀复用 **0.9101**）
- [x] W0 门禁 `tools/prefix-cache-test.cjs`（8/8 PASS，含负控）
- [x] W1 patch 047 前缀稳定化 —— **A1 + A2 已落地并实测达标（0.9101 → 1.0000）**
  - [ ] A3 世界书/向量召回 `at_depth` 绝对锚（**与「纯追加」存在语义冲突，需专门设计，本版未做**）
  - [ ] A4 请求期正则 depth 改绝对索引
  - [ ] A5 世界书概率门 `Math.random()` 确定化
- [x] W2 patch 048 Anthropic 缓存断点（用量页命中率是**既有能力**，无需新增 UI）
- [x] W3 patch 051 潜伏缺陷修复 —— **C1 + C4 已落地，Anthropic 端到端验证通过**
  - [ ] C2 Anthropic/Gemini SSE 尾部 flush
  - [ ] C3 Anthropic usage 合并而非覆盖
- [x] W4 patch 050 Kotlin 传输后端 + 桥接 + 卸载适配器（`ext/luzzy-chat-offload.js`）+ 可降级开关
- [ ] W5 全门禁 + 构建 + 文档 + 提交
- [ ] W6 真机实测（**当前无设备连接**）

### 实测数据（可复现）

| 项 | 改造前 | 改造后 |
|---|---|---|
| 相邻两轮公共前缀占比（干净配置 6 轮） | **0.9101** | **1.0000** |
| 每轮被改写的「已存在消息」条数 | 1 | **0** |
| `tools/prefix-cache-test.cjs` | （门禁尚不存在） | 8/8 PASS，退出码 0，负控判红 |
| Anthropic 协议端到端（打桩） | `API Error: 200 {"id":"m1",…}`（**完全不可用**） | 正常产出回复、零 JS 异常 |
| 既有三门禁（stream-render / model-list / page-handoff） | PASS | PASS（无回归） |
