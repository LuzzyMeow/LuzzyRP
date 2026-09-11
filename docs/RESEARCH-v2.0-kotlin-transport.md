# RESEARCH · v2.0 原生 Kotlin 聊天传输层

> 状态：**已实现并验证**（构建 + 单测全绿）。
> 范围：把 LLM **传输层**（HTTP + SSE + 三协议线格式 + 流式装配）从 JS 下沉到 Kotlin。
> **上下文装配与渲染仍在 WebView 侧**——这是刻意的「薄切」，不是遗漏。
> 素材来源：v1.5.0 已移除的助手模块（commit `0392b662^`）。**恢复 + 改编，不是重写**。

---

## 1. 交付物

| # | 内容 | 落点 |
|---|------|------|
| A | 原生传输层 + 桥接 + JS 胶水，`assembleRelease` 通过，单 APK | `app/src/main/java/com/luzzymeow/luzzyrp/chat/**`、`assets/ext/luzzy-chat-native.js` |
| B | 单测全绿 | `app/src/test/java/com/luzzymeow/luzzyrp/chat/**` |
| C | 线协议保真门禁 | `chat/llm/WireFidelityTest.kt` |
| D | 本文件 | `docs/RESEARCH-v2.0-kotlin-transport.md` |

---

## 2. 恢复的文件清单与行数

包名从 `assistant.domain.llm` / `assistant.domain.loop` 迁到 **`chat.llm`**（v2.0 自有归属）。

### 2.1 主源码（15 文件 / 1952 行）

| 文件 | 行 | 与 `0392b662^` 的关系 |
|------|----|----------------------|
| `chat/llm/SseFrames.kt` | 92 | **原样恢复**（仅改包名） |
| `chat/llm/JsonLenient.kt` | 42 | 原样恢复 + `@OptIn(ExperimentalSerializationApi)`（消 `explicitNulls` 告警） |
| `chat/llm/SseClient.kt` | 164 | 恢复 + 两处适配（见 §3.1、§3.4） |
| `chat/llm/LlmTransport.kt` | 275 | 恢复 + 改编（`LlmMessage` 增 `raw` 转发位、`LlmDelta` 增 `rawUsage`、`ToolCall` 从 `domain.tool` 迁入） |
| `chat/llm/OpenAiWire.kt` | 192 | **按 v2.0 契约重写**请求体（键序 / extraBody 展开 / tool_choice 三元） |
| `chat/llm/OpenAiTransport.kt` | 72 | 恢复（URL 改为原样 POST） |
| `chat/llm/AnthropicWire.kt` | 245 | **按 v2.0 契约重写**（URL 原样 / 键序 / thinking 守卫 / JS 同构消息转换） |
| `chat/llm/AnthropicTransport.kt` | 71 | 恢复 + 头集对齐 JS |
| `chat/llm/GeminiWire.kt` | 212 | **按 v2.0 契约重写**（端点在适配器内拼、`?key=`、键序、JS 同构转换、`encodeUriComponent`） |
| `chat/llm/GeminiTransport.kt` | 68 | 恢复 + 鉴权由头改为查询串 |
| `chat/llm/RoutingTransport.kt` | 46 | 原样恢复 + 增 `SUPPORTED_PROTOCOLS` |
| `chat/llm/BudgetGuard.kt` | 84 | 原样恢复（仅 KDoc 去掉对已删除类型的引用，**逻辑零改动**，其 9 条单测逐条恢复即为证据） |
| `chat/ChatPlan.kt` | 87 | **新增**：plan JSON → `LlmRequest` |
| `chat/ChatJobs.kt` | 256 | **新增**：任务管理 + 120ms 合批 + 四型事件 |
| `chat/JsCall.kt` | 46 | **新增**：`evaluateJavascript` 调用串与转义 |

### 2.2 测试（15 文件 / 2876 行）

| 文件 | 行 | 用例 | 来源 |
|------|----|-----|------|
| `llm/SseFramesTest.kt` | 83 | 13 | 原样恢复 |
| `llm/JsonLenientTest.kt` | 43 | 6 | 原样恢复 |
| `llm/ToolCallAccumulatorTest.kt` | 100 | 9 | 恢复 + 增快照键序断言 |
| `llm/OpenAiWireTest.kt` | 324 | 26 | 恢复并改写（见 §3.2） |
| `llm/OpenAiTransportTest.kt` | 250 | 14 | 恢复并改写（去掉工具体系依赖） |
| `llm/AnthropicWireTest.kt` | 328 | 28 | 恢复并改写 |
| `llm/GeminiWireTest.kt` | 291 | 25 | 恢复并改写 |
| `llm/AnthropicTransportTest.kt` | 136 | 7 | **新增**（真 socket 端到端） |
| `llm/GeminiTransportTest.kt` | 134 | 6 | **新增**（真 socket 端到端） |
| `llm/BudgetGuardTest.kt` | 91 | 9 | 原样恢复 |
| `llm/RawSseServer.kt` | 126 | — | 恢复并改编（URL 不再固定路径） |
| `llm/WireFidelityTest.kt` | 303 | 17 | **新增**（交付物 C） |
| `ChatPlanTest.kt` | 188 | 16 | **新增** |
| `ChatJobsTest.kt` | 460 | 23 | **新增** |
| `JsCallTest.kt` | 60 | 8 | **新增** |

另有 3 个文件被修改：`app/build.gradle.kts`（+7）、`gradle/libs.versions.toml`（+11）、
`web/LuzzyBridge.kt`（+78/-10）、`MainActivity.kt`（+12/-2）。**签名块与 `splits.abi` 未触碰。**

### 2.3 JS 侧回归（不进 Gradle 测试源集）

| 文件 | 行 | 用例 | 说明 |
|------|----|-----|------|
| `app/src/test/js/chat-native.test.cjs` | 214 | **14** | Node `vm` 沙箱加载**真实资产**，行为级回归（见 §3.8） |

`app/src/test/` 下放一个 `.cjs` 不会被 Gradle 的 Kotlin 编译单元拾取（测试数仍如实为 207），
但可以被 `node app/src/test/js/chat-native.test.cjs` 直接跑，退出码 0 = 全过。

---

## 3. 改了什么、为什么

### 3.1 三协议的 URL 语义统一为「JS 给出、Kotlin 不拼」

v1.5.0 的三个 Wire 都自带 URL 拼接（补 `/chat/completions`、补 `/v1/messages`、补 `/v1beta`），
而 v2.0 契约要求 **OpenAI / Anthropic 原样 POST**（JS 已按供应商配置算好端点，
自建网关的路径形态不可预测）。改动：

- `OpenAiWire.chatCompletionsUrl()` → 删除，改为 `messagesEndpoint()`（只 trim）；
- `AnthropicWire.messagesUrl()` → 删除，传输层直接 trim 后 POST；
- `GeminiWire.streamUrl()` → 改为 `endpoint(base, model, key, stream)`，**在适配器内**拼，
  且密钥进查询串（与 JS 同）。

原「BaseUrl 拼接」的 3 条断言随之删除——这是**契约变更**，不是测试放水。

### 3.2 extraBody 语义：从「受保护字段」改为「对象展开」

v1.5.0 实现按「白名单保护 `model/messages/tools/stream`」忽略冲突键。
但 JS 参考实现（`api-utils.js`）用的是**对象字面量 + 展开**：

```js
{ model, messages, temperature, ...extra, ...(tools.length ? {tools, …} : {}), stream, … }
```

即：展开位置之前显式写的键会被 extraBody 覆盖，之后的键反过来覆盖 extraBody。
这对 wire fidelity 是**语义级**差别，必须逐字对齐。Kotlin 侧用 `buildJsonObject`
顺序 `put` 实现——`JsonObjectBuilder` 底层是 `LinkedHashMap`，
**同名键原地替换、键位不变**，与 JS 展开的 property-order 行为完全一致。
`OpenAiWireTest.extraBody 用对象展开语义` 用整表键序断言把这条钉住了。

### 3.3 messages 逐字节转发

JS 发来的 OpenAI 形态消息里可能有本层不认识的字段（`reasoning_details`、
`extra_content`、供应商私有键）。v1.5.0 的 `LlmMessage` 只有 `content: String`，
转一道就会**丢字段、改键序**。改法：`LlmMessage` 增 `raw: JsonObject?`，
`toOpenAiJson()` 优先返回 `raw`；Anthropic/Gemini 的消息转换也统一
`messages.map { it.toOpenAiJson() }` 后再变换——保证三协议**吃同一份输入**。

### 3.4 空响应体容错与密钥脱敏

- `Response.body` 在解析到的 OkHttp 4.12 上是 `ResponseBody?`（可空），
  原代码按不可空写会编译失败；已按可空处理，并补一条「响应没有正文」的可重试错误。
- `redactSecrets` 增加 **`AIza…`（Google API Key）** 形态打码：Gemini 的密钥走查询串，
  最容易从错误响应体里漏出来。
- Anthropic 的 `message_stop` 帧**不再返回 `finishReason = "stop"`**：
  它会顶掉 `message_delta.stop_reason`（`tool_use` / `end_turn`），
  而抗截断判断要用后者。终局兜底由 `ChatJobs` 的 `finishReason ?: "stop"` 负责。

### 3.5 与已移除模块的解耦

- `ToolCall` 从 `assistant.domain.tool.Tool.kt` **迁入 `chat.llm`**。
  理由：原 `Tool.kt` 还带着 `ToolResult` / `ToolContext` / `WorkspaceAccess` /
  `ApprovalGate` 一整套已被移除的工具与工作区体系，传输层只用到 `ToolCall` 一个类型。
- **`Schema.kt`（JSON Schema DSL）未恢复**：v2.0 的工具定义由 JS 直接给 OpenAI 形态，
  Kotlin 不需要构造 schema。唯一需要 Kotlin 侧自带的定义是抗截断的 `output_reply`，
  已按 `api-utils.js` 的 `replyTool` **逐字段同构**落在 `llm/OpenAiWire.kt` 的 `ReplyTool`。
- `BudgetGuard` 恢复且逻辑零改动；但在 v2.0 中传输层是**单次请求**语义，
  暂未接入多轮循环，仅作为后续把工具循环下沉时的现成零件保留（其单测全绿）。

### 3.6 桥接与线程模型（契约实现细节）

- `chatStart` 在 JavaBridge 线程解析 plan 并 `scope.launch(start = LAZY)` 登记后
  **立即返回 jobId**，不做任何 IO；
- 事件出口是 `LuzzyBridge.eventSink`（可设属性），`MainActivity` 用
  `webView.post { webView.evaluateJavascript(js, null) }` 保证 UI 线程；
  用 `post` 而非 `runOnUiThread`：后者在 Activity 已销毁时会抛，`post` 只是排队丢弃；
- 合批：`DeltaBatcher` + 一个 120ms ticker 协程，`flushIfDue()` 双向去重（收集循环与 ticker
  都可能触发，内部加锁），任务收尾强制 `flush()`。**首个增量立即发出**（首字延迟优先），
  此后按 120ms 节流——与 JS 侧 `STREAM_RENDER_INTERVAL = 120` 同档；
- 降级：`chatStart` 非法入参返回 `""`、`chatAbort` 未命中返回 `false`、
  三个方法全体 `try/catch(Throwable)`——**绝不跨越 JS 边界抛异常**；
- `MainActivity.onDestroy` 先 `bridge.shutdownChatJobs()` 再 `removeView` + `destroy`。

### 3.7 【修复】`onEvent` 终态事件被静默丢弃（集成方发现，本版修正）

**现象**：`onEvent` 原本先 `handlers.delete(id)` 再 `dispatch(id, event)`。
`dispatch` 靠 `handlers.get(id) || fallbackHandler` 取处理器——条目已被删除，
而「只注册 per-job 处理器、不设全局兜底」的调用方（正是 v2.0 适配器的形态）
**收不到 `done` / `error`**。后果：调用方 Promise 永不 settle，
真机上表现为**一直「生成中」**——与仓库已记录的 CDP 探针事故同类
（`isGenerating` 卡在 `true`）。

**修复**（由集成方直接改在 `luzzy-chat-native.js`，本 Agent 保留其顺序并复核）：
先 `dispatch`、再在终态时 `handlers.delete`。本 Agent 补做了三件事：

1. **确认无其它依赖旧顺序**：全文件只有 `dispatch` 读 `handlers` / `fallbackHandler`
   （`grep` 实证：写入方仅 `onEvent`（终态）、`abort`（主动取消）、`setHandler`（显式注册）），
   因此交换顺序没有连带影响；
2. **加回归门禁**（`app/src/test/js/chat-native.test.cjs`，14 条，全过）：
   沙箱加载真实资产，断言
   ① 只注册 per-job 时 `done` / `error` 必须送达；
   ② 终态交付后处理器被摘（同一 jobId 不重复送达）；
   ③ 4 条降级面（无桥 / 三方法缺一 / `start` 抛错 / 非法事件串）。
   ——顺带钉住了两个「改顺序就会红」的隐含契约：**非终态事件不摘处理器**、
   **全局兜底不被终态事件连带删除**；
3. **在文件头写明调用顺序契约**：事件是推来的，因此必须
   `setHandler(jobId, fn)` **先于** `start(plan)`，否则首个事件到达时还没有处理器。

**两条路径的最终结论**（集成方点名要的）：

| 场景 | 结果 |
|------|------|
| 仅 per-job 处理器 | ✅ 送达后再摘除 |
| 仅全局兜底 | ✅ 送达（`handlers.get` 未命中 → 回落兜底；随后的 `handlers.delete(id)` 对不存在的键是空操作，兜底本身不受影响） |
| 两者都有 | ✅ per-job 优先，兜底保留给后续 job |
| `abort` 主动取消 | ✅ 先摘处理器（`abort` 路径）——原生侧中止时**不发**终态事件（`ChatJobsTest.abort 中止在跑的任务且不再发 done` 保证），故不会漏事件 |
| 事件串非法 | ⚠️ 解析失败即 `return`，**不摘处理器**（可能悬留）。原生侧永远发合法 JSON，实际不可达；已在测试中显式断言「非法帧不误摘处理器」 |

**残留的固有竞态**（不是 bug，是推模型的语义）：处理器注册晚于终态事件到达时，
该事件无处可去。原生侧 `chatStart` 返回后至少要走一次网络往返才可能有首个 delta，
而适配器在 `start` 返回后同一同步块内注册，窗口远小于网络时延；契约已在文件头写明。

### 3.8 jobId 回显（集成方点名确认）

**原生侧全程原样回显 `plan.jobId`，不生成任何 id**，链路如下：

```
ChatPlan.parse       jobId = root.strOrNull("jobId")      // 原样读
ChatJobs.start       return plan.jobId                    // 原样返回
                     runJob(plan.jobId, …) → emitTo = { emit(jobId, it) }
ChatJobs.emit        sink.onEvent(jobId, eventJson)
ChatJsCall.onEvent   b.onEvent('<jobId 转义>', '<json 转义>')
```

`ChatJobsTest` 用 4 个形态各异的 id（uuid / 短串 / 含单双引号 / 含空格与斜杠）
断言 `start` 的返回值与全部事件的 jobId 逐字节等于 plan 里的值；
`JsCallTest` 另有 2 条专测 id 与事件串的 JS 字面量转义（含 `'` 逃逸防护）。
集成方按 `setHandler(plan.jobId, fn)` 注册即可命中。

### 3.9 JS 胶水层的其余约定（`assets/ext/luzzy-chat-native.js`）

- 三方法齐备才 `ready() == true`（缺一个发出去只会拿到静默空串）；
- `capabilities()` 在无桥时返回 `{available:false, reason:'no-native-bridge'}`，
  **形状与原生一致**，调用方无需分支；
- `onEvent` 是原生推入口：解析失败静默丢弃，终态（`done`/`error`）后自动摘处理器；
- 处理器自身抛错不外溢（否则会打断后续事件）；
- **不 patch 上游 fetch、不改 `index.html`**——接线由前端集成方负责（任务约束）。

---

## 4. 最终依赖（实际解析结果）

`releaseRuntimeClasspath` 实测：

| 坐标 | 声明 | **解析结果** |
|------|------|-------------|
| `org.jetbrains.kotlinx:kotlinx-serialization-json` | 1.9.0 | **1.9.0**（→ `-json-jvm:1.9.0`、`-core:1.9.0`） |
| `com.squareup.okhttp3:okhttp` | 4.12.0 | **4.12.0**（→ `com.squareup.okio:okio:3.6.0`） |
| `org.jetbrains.kotlin:kotlin-stdlib` | AGP 内置 | **2.2.20**（AGP 9.2.1 内置 Kotlin；`2.2.10 → 2.2.20` 为依赖引擎对齐） |
| `org.jetbrains.kotlinx:kotlinx-coroutines-android` | 1.11.0 | **1.11.0** |
| `junit:junit`（仅测试） | 4.13.2 | **4.13.2** |
| `org.jetbrains.kotlinx:kotlinx-coroutines-test`（仅测试） | 1.11.0 | **1.11.0** |
| `com.squareup.okhttp3:mockwebserver`（仅测试） | 4.12.0 | 已声明但**未被任何测试使用**（见 §6） |

**结论：`kotlinx-serialization-json` 可用，不必回退 `org.json`。**
关键依据：删除的代码里 **`@Serializable` 使用 0 次**，只需要运行时库的树 API
（`buildJsonObject` / `parseToJsonElement` / `JsonObject`），
**不需要 kotlinx-serialization 编译器插件**，因此不受「本工程未启用该插件」的限制。
探针验证方式：加入依赖 + 一个引用两组 API 的 trivial 文件 → `:app:assembleRelease` 通过。

---

## 5. 验证结果（真实数字）

### 5.1 单元测试（Kotlin / JVM）

```
./gradlew :app:testDebugUnitTest --console=plain
BUILD SUCCESSFUL
```

从 `app/build/test-results/testDebugUnitTest/TEST-*.xml` 统计：

| 项 | 值 |
|----|----|
| 测试类 | **14** |
| 用例总数 | **207** |
| failures | **0** |
| errors | **0** |
| skipped | **0** |

分类：`ChatJobsTest` 23 · `WireFidelityTest` 17 · `ChatPlanTest` 16 · `JsCallTest` 8 ·
`AnthropicWireTest` 28 · `AnthropicTransportTest` 7 · `GeminiWireTest` 25 ·
`GeminiTransportTest` 6 · `OpenAiWireTest` 26 · `OpenAiTransportTest` 14 ·
`SseFramesTest` 13 · `ToolCallAccumulatorTest` 9 · `BudgetGuardTest` 9 · `JsonLenientTest` 6。

其中 **22 条**走真 socket（`RawSseServer` = JDK `ServerSocket` + 真 OkHttp 栈：
`OpenAiTransportTest` 10 · `AnthropicTransportTest` 6 · `GeminiTransportTest` 6），
无需设备即可覆盖连接 / 分帧 / 空闲超时 / 取消关连接 / 重试次数 / 请求头与请求体。

### 5.2 JS 行为回归（Node，不进 Gradle）

```
node app/src/test/js/chat-native.test.cjs
14/14 passed        （退出码 0）
```

覆盖：终态事件交付顺序（2 条回归 + 3 条隐含契约）、降级面 4 条、注册/派发/兜底优先级等。
这是**唯一**能测到 `onEvent` 顺序语义的层（Kotlin/JVM 看不到纯 JS 运行时行为）。

### 5.3 构建

```
./gradlew :app:assembleRelease --console=plain
BUILD SUCCESSFUL in 34s
```

- 产出 **恰好 1 个 APK**：`app/build/outputs/apk/release/app-release.apk`
- 字节大小：**18,493,190 B**（`output-metadata.json` 非 APK）
- 签名：`CN=LuzzyRP, OU=LuzzyMeow, O=LuzzyMeow, L=Internet, C=CN`，
  SHA-256 `ed78235d2945d2c075b5b1ce92c3eb8ab23222382007fb87dd7cc3f32a9dffb1`
  （说明走的是 `luzzy` 签名配置，未回退 debug）
- 编译告警：clean 重建下只有 3 条**既有**弃用告警（`MainActivity.onActivityResult` ×2、
  `WebViewSetup.databaseEnabled` ×1）；**R8 无 missing-class 告警**，无 error
- APK 内含新资产 `assets/ext/luzzy-chat-native.js`（**7407 B**，与源文件 SHA-256
  `80cab628a575b5d2295dc688a2319ca168c270781a3a382d6e0484bdfb94a958` **逐字节一致**）
- `splits.abi` 块**仍为注释**（单包纪律未动）；`versionCode 12 / versionName 1.4.0` 未改

### 5.4 线协议保真（交付物 C）

`WireFidelityTest` 的期望值全部是**手写常量字符串**，输入统一从
**JS 真会发来的那份 plan JSON** 起步（`ChatPlan.parse` → Wire → 序列化），
因此同时覆盖「plan 解析 → 请求体构造」整链。断言 `assertEquals` 整串相等（键序 + 值 + 转义）：

- OpenAI：基础形态、extraBody 精确落点（`max_tokens` 与 `tools` 之间）、
  `tool_choice` 三种分支、tools 簇出现与否、messages 逐字节透传；
- Anthropic：基础形态、无 thinking、extraBody 落点（`thinking` 与 `stream` 之间）、不发 tools、无 system 键；
- Gemini：基础形态、extraBody 展开在最外层末尾、端点逐字符预期；
- 跨协议：三协议 body 都不含密钥。

计划中「JS 侧我另外写」的部分（如上游 `builtin-content` 预设文案）不在此列。

---

## 6. 未能验证 / 已知边界（诚实清单）

1. **真机未测**。本机未连设备，`adb` 流程（release 覆盖安装 → 真机发一条消息）未执行。
   JVM 侧能覆盖的（分帧 / 超时 / 取消 / 重试 / 头集 / body）都已覆盖，但
   **`evaluateJavascript` 的实际投递、JavaBridge 线程行为、WebView 生命周期竞态
   只能在真机上确认**。
2. **未与 JS 侧联调**。JS 集成（把 plan 交给 `chatStart`、消费四型事件）由另一位负责人
   在 `rphub/assets/js/**` 完成；本 Agent 按任务约束**未触碰那些文件**，
   因此「端到端一次真实对话」尚未跑通。四型事件形状已按契约逐字符冻结在
   `ChatJobsTest` 里，JS 侧可直接对着断言写。
3. **`mockwebserver` 依赖已声明但未被使用**——测试全部走自建 `RawSseServer`
   （零依赖、可控到「写一半保持连接」这种粒度）。若要清理可移除该声明。
4. **Anthropic/Gemini 的 tools 未发送**：这是**照 JS 参考实现**的行为
   （上游两个适配器都不带 tools），不是遗漏。若 v2.0 期望这两协议也支持工具调用，
   需要 JS 侧先明确，再补 tools 转换与单测。
5. **`BudgetGuard` 未接入运行时**：v2.0 传输层是单次请求语义，
   预算闸门属于「多轮工具循环」范畴；该类恢复且测试全绿，供后续下沉时直接复用。
6. **`temperature` 为 null 时该键省略**（JS 侧 `undefined` 会被 `JSON.stringify` 丢掉，语义相同）；
   但若 JS 显式传 `null`，Kotlin 侧同样省略——与 JS 的 `JSON.stringify(null)` 会产出
   `"temperature":null` **不同**，属已知微差（契约里 temperature 恒有值，实际不可达）。
7. **签名指纹未与上一版 Release 逐位比对**（手边没有旧 APK）。已确认
   `CN=LuzzyRP`（非 debug 签名）且 `keystore.properties` 存在；
   发布前仍应按规定跑一次与上一版的指纹比对。
8. **`docs/WORKLOG.md` 未追加**：该文件由另一条工作流在改（`git status` 可见其处于修改中），
   为避免冲突留给集成方登记。
9. **偏离 AGENTS.md §5.4 的桥接封装落点**：仓库规范要求「新增桥接方法必须同步
   `assets/ext/luzzy-bridge.js` 封装」，但本任务约束把 `luzzy-bridge.js` 列为**不可触碰**
   并指定新建 `assets/ext/luzzy-chat-native.js`。因此 `chatStart` / `chatAbort` /
   `chatCapabilities` 的封装与降级落在**新文件**里，`luzzy-bridge.js` 未改。
   若集成方希望严格保持 §1.4/§5.4 的形式，需在 `luzzy-bridge.js` 补三个转发封装
   （或把该约定登记为 patch 050 的一部分）——**该动作超出本 Agent 的授权范围**。
10. **`CHANGELOG.md` 未登记**：任务约束禁止触碰。按硬性规定 5，v2.0 的原生传输层与
    §3.8 的顺序修复都应进 CHANGELOG「新增 / 修复」段，留给集成方。
11. **未跑 `tools/verify-markers.ps1`**：本 Agent 只新增 `ext/` 下的独立文件、未改任何上游
    `rphub/**` 文件，理论上不影响标记门；但门禁是否需为 `luzzy-chat-native.js` 增项，
    由集成方决定。
