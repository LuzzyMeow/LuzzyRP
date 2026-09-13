# PLAN-v3.0-p5-agent-loop · KV 缓存最大化 × 完整 Agent Loop × 上游功能对账

> **立档**：2026-09-13（会话 73）。用户拍板：「按 DSH 重做注入策略」+「实现完整的 Agent Loop」+
> 「KV 缓存最大优化与性能最大优化」+「实现上游功能」。
>
> **架构依据**：DeepSeek Harness（`github.com/deepseek-ai/deepseek-harness`，已 clone 到 `/tmp/dsh-src`，
> DeepSeek 官方 agent harness，3352 个 TS 文件的 Cordis 插件 monorepo）。本文所有 DSH 结论均带源码证据。
>
> **与既有文档的关系**：
> - 本仓库的 `docs/PLAN-v1.6.0-dsh.md` 是**我们自己** 2026-09-11 写的（借了 DSH 的名与理念，
>   落在 WebView 版上，已由 patch 047/048/050 部分实现）；本文是它在 **Compose 原生侧**的续篇与升级。
> - `docs/PLAN-v3.0-compose.md` 的 P5「功能对账」由本文细化（原 PLAN 的 P5 项并入本文 C/D 批）。
> - 用户先批准的「全保真注入」已被本次调查推翻并改选 **DSH 式尾部快照**（理由见 §1.3），
>   那是一处**有意为之的方向修正**，不是执行偏差。

---

## 0 · 一句话范围

把 Compose 侧的对话链路从「每轮重建请求 + 3 轮工具循环」升级为
**「DSH 式可重建请求（前缀稳定涌现）+ 完整 Agent Loop（turn/step 两级 / 中断可恢复 / 配对完整 / 压缩）」**，
并完成 P5 剩余的上游功能对账与缺陷清理。

---

## 1 · DSH 调查结论（设计依据，全部带证据）

### 1.1 第一原则：前缀稳定是**涌现**的，不是「管理」出来的

> **"Model-visible ⟺ durably referenced."** … Prefix-cache stability is corollary #1, not the headline:
> an append-only log projected by a per-node pure function yields requests that are append-extensions
> of their predecessors whenever the header is unchanged — **stability is emergent, not managed**.
> —— `.agents/notes/implemented/architecture/2026-07-05-reconstructable-requests.md`

落成三条可照搬的约束：
1. **请求是会话日志的纯函数**（`packages/core/session/src/surface.ts:79-90` 的唯一投影函数）；
2. **历史消息永不改写**（整包 `deepFreeze`，`agent.ts:602-617`）；
3. **凡会变的事实逐出 system**，变成「变了才追加」的尾部快照（详见 §1.2）。

### 1.2 稳定块 / 易变块分离（最值得照搬的一条）

| | DSH 做法 | 证据 |
|---|---|---|
| 不变指令 | system 的**命名 section**，`order` 升序 + 名称字典序**决定化**拼接，空块丢弃 | `system-prompt/src/index.ts:121-154, 273-278` |
| 会变的上下文 | **尾部 user 快照**，开头写「本快照取代先前快照」 | `system-prompt/src/index.ts:297-301` |
| 去重 | **内容没变就一条都不发** | `agent-loop/src/runtime-context.ts:147-158` |
| 工具 schema | 按工具名 code-unit 字典序排（locale 无关），每次装配重新求值 | `system-prompt/src/index.ts:210-239` |
| 工具集变化 | 显式检测并记 `request/header` 事件，触发 `startsSeries`（承认失效、重新整理） | `agent-loop/src/agent.ts:262-266`；`session/src/request-header.ts:53-63` |
| 请求头冻结 | provider/model/effort/采样值逐字段比较，变化记快照**而不是静默漂移** | `llm/src/call-config.ts:3-15, 52-65` |

### 1.3 上游语义 vs DSH 原则的冲突（本次方向修正的理由）

WebView 版 patch 047 当时就登记过这条冲突（`CHANGELOG.md:407`）：

> 已知未完成：世界书 / 向量召回的「距尾 depth」插入点（`at_depth`）与「纯追加」存在**语义冲突**

冲突的数学本质：上游定位算法是**相对深度**（`findDepthIndex` 从尾部倒数，`data-services.js:990-1012`），
而「距尾第 N 条」在对话变长后**必然指向不同的插入点** → 插入点之后的每轮请求都位移 →
**前缀缓存从插入点断裂**。

| 上游 7 个 position | 是否随轮次漂移 | 本计划的处置 |
|---|---|---|
| `system_top` / `global_note` / `before_char` / `after_char` | **否**（位置固定在 system 内） | **照上游语义，保持稳定块** ✅ |
| `at_depth`（新开 user 消息插进历史中间） | **是**（距尾深度漂移） | **改尾部快照**（DSH 式） |
| `user_top` / `assistant_top`（就地改写已有消息） | **是**（那条消息每轮被重写） | **改尾部快照**（DSH 式） |

**因此：七位置里 4 个保真、3 个改道**。改道的三条**如实登记为偏离**，并在世界书编辑器里
标注「按深度插入 / 前插到消息」这两个位置在本版按「尾部快照」执行。

### 1.4 完整 Agent Loop 的要素（DSH 版）

| 要素 | DSH 做法 | 证据 |
|---|---|---|
| 两级循环 | `kick()` → `turn()` → `step()`，全程 while 非递归 | `agent-loop/src/agent.ts:225-238, 269-350, 352-498` |
| 终止 | **无 tool_call 即停**；`max-tokens` 粘性；无轮数上限 | `agent.ts:486-487, 305-310` |
| 结束原因 | `completed / aborted / blocked / error / max-tokens / interrupted` | `session/src/types.ts:199-221` |
| 工具配对 | 结果按**模型顺序**提交；abort 时给未启动的调用补**合成错误结果** | `tool-calls.ts:147-161, 249-260` |
| 崩溃修复 | 重开时扫未闭合 turn，给悬空 tool_call 补「结果未知，勿盲目重试」 | `session/src/repair.ts:29, 106-107, 133` |
| 中断 | 已收到的部分流内容以 `interrupted: true` **落库保留** | `agent.ts:402-419` |
| 压缩 | token 达 `contextWindow × 0.8` 触发；**system 头永不裁**、保留尾部 `retainRatio`、**不切开工具配对** | `compaction-basic/src/config.ts:20, 23`；`region.ts:117-154` |
| 摘要调用自身吃缓存 | 摘要请求按原前缀原样重放（system 头 + tools + 被裁消息），只在其后追加摘要指令 | `region.ts:515-524` |

**安卓适配**（用户约束：单 Activity、点击触发、无后台常驻）：
- 一次点击 = 启动一个 turn 跑到完或被中断；进程可死，重开时先跑「修复悬空 tool_call」；
- 工具全串行（`maxParallelToolCalls = 1`），省掉并发调度；
- **加一个安全上限**（DSH 无上限是因为它有外部监督；我们加 50 step 防御模型死循环，如实标注这是我们的加法）；
- 不做：多 agent 委派、Cordis waterfall、沙箱/审批、LSP。

### 1.5 两个关键澄清（影响方案可行性）

1. **我们之前实现的不是完整 Agent Loop**——只是 `while (round < 3)` 的工具循环，
   无中断恢复、无配对保护、无压缩、轮数硬编码。
2. **「尾部 user 快照」在三协议下天然安全**——`AnthropicWire.kt:92` 与 `GeminiWire.kt:88`
   **本来就把 system 压成 user**，后置快照不撞任何协议的 system 位置限制。
3. **顺带发现既有缺陷**：`TransportStore.load()/save()` **漏掉 `maxTokens`/`temperature`**
   （`TransportConfig` 有字段但读写漏了）→ 用户改了温度重启即丢。正是 DSH 说的「静默漂移」，A5 修掉。

---

## 2 · 任务全貌（四批）

```
批 A · 请求组装与缓存（地基，后续都依赖它）
  A1 组装纯函数化    A2 稳定块/易变块分离    A3 注入改尾部快照    A4 工具 schema 稳定化
  A5 请求头冻结      A6 请求重建             A7 缓存观测层        A8 命中率展示

批 B · 完整 Agent Loop
  B1 turn/step 两级  B2 终止条件  B3 工具配对完整性  B4 中断保留  B5 压缩  B6 请求重试

批 C · 上游功能对账（P5 剩余）
  C1 五个假数据页  C2 真实角色图  C3 多候选持久化  C4 附件真功能
  C5 表格列宽      C6 撤工作区    C7 reduced-motion

批 D · 设计门项 + 文档
  D1 字号  D2 导入导出  D3 迁移报告页  D4 文档反向修正 + 设计真源回填
```

---

## 3 · 批 A 详案

### A1 · 组装纯函数化 ✅（已落地，待接线）

- `chat/PromptAssembler.kt`：**纯 Kotlin 无 Android 依赖**，`assemble(Input) → List<LlmMessage>`。
  上游 messages 序列照抄（`app.js:4613-4918`），含 `safeTargetLimit`、`findDepthIndex`、
  `mergeConsecutiveRoleMessages` 的等价实现。
- `chat/WorldBookActivator.kt`：激活扫描（子串 / 正则强制 i / constant / 概率 / 扫描深度），
  概率用可注入 `Dice` → 测试完全确定。**30 个 JVM 单测全绿**。
- `WorldBookTool` 换真库（`withEntries` 注入），工具与生成前扫描**共用同一份数据与同一套匹配口径**。
- **人设字段裁决**：上游模板只用 `name` + `personality`（`built-in-content.js:66`），
  `description` 只用于搜索（`app.js:2861`）；但迁移数据的**人设正文在 `description`**（夹具里谢昭/夏梧
  `personality` 为空串）→ 两者都进 prompt，`\n\n` 连接。**如实登记为偏离**。

### A1b · ChatPage 接线真实输入

`ChatPage` 在 `send` / `regenerateFrom` / `regenerate` 三处构造 `PromptAssembler.Input`：
角色（从 `CharacterEntity.payload` 取 name/description/personality/mes_example/first_mes）、
用户（`kv["user"]` 或 profile）、预设（`PresetRepository.enabled()`）、
世界书（`WorldBookRepository.load()` → `WorldBookActivator.activate()`）。

### A2 · 稳定块 / 易变块分离

**新增 `chat/PromptSections.kt`**（纯函数）：system 内容变成**命名 section + order**：

| order | section | 来源 |
|---|---|---|
| 100 | 破限预设 | 预设列表里 name == 破限 |
| 200 | 其余 system 预设 | 预设列表 |
| 300 | 世界书 `system_top` / `global_note` | 世界书 |
| 400 | 用户信息块 | 设置页用户档案 |
| 500 | 角色块 `[Character]` | 角色卡 |
| 600 | 世界书 `before_char` / `after_char` | 世界书 |
| 700 | 工具提示 | 静态文本 |

**决定化排序**：同 order 内按 section 名 code-unit 序（DSH `compareToolNames` 等价）；
世界书条目在 section 内按 **`comment` 字典序 + 稳定键**排序——**不按用户拖拽顺序**，
否则用户调整顺序会让整个 system 前缀位移（那属于「用户主动改设置」，允许失效，
但要记成显式事件而不是隐式漂移）。

### A3 · 注入策略改尾部快照（**本批核心**）

**新增 `chat/RuntimeSnapshots.kt`**（纯函数，DSH `RuntimeContextProjection` 的 Kotlin 等价）：

```kotlin
data class Snapshot(val name: String, val order: Int, val text: String)

object RuntimeSnapshots {
    /** 上一次已发出的快照文本（每会话一份，随会话持久化）。 */
    fun project(current: List<Snapshot>, retained: String?): String? {
        if (current.isEmpty() && retained == null) return null
        val body = current.sortedBy { it.order }.joinToString("\n\n") { it.text }
        val text = if (body.isBlank()) CLEARED else "$HEADER\n\n$body"
        if (text == retained) return null        // ★ 没变就一条都不发
        return text
    }
    const val HEADER = "Current runtime context. This snapshot supersedes earlier runtime-context snapshots."
    const val CLEARED = "Current runtime context: none. Earlier runtime-context snapshots no longer apply."
}
```

**分派**：世界书 `system_top`/`global_note`/`before_char`/`after_char` → **稳定块**；
`at_depth`/`user_top`/`assistant_top` + 记忆召回 → **快照**。

**快照落点**：追加在**历史之后、本轮 user 输入之前**（DSH 放在 claimed 用户消息之后；
我们置其前，语义相同且不打断「最后一条是 user」这一各协议通用要求）。

**去重的持久化**：`retained` 随会话存（`kv` 按 scope 记一行），否则重启后第一次请求会重复发快照。

### A4 · 工具 schema 稳定化 + 变化检测

- schema 规范化序列化（键序固定）——现有 `WorldBookTool.schema` 已是固定键序，补**单测**钉住字节稳定；
- 新增 `RequestHeader` 指纹（protocol + baseUrl + model + temperature + maxTokens + tools 哈希）；
  每轮比对：变了记一次 `headerChanged`（日志 + 观测层计数），没变不产生任何额外内容。

### A5 · 请求头冻结（缓存纪元）

- **修既有缺陷**：`TransportStore` 补读/写 `maxTokens` 与 `temperature`；
- 配置变化时视为**缓存纪元断裂**：记录一次事件（观测层可见），而不是让它静默混进下一轮请求。

### A6 · 请求 = 状态的纯函数（可重建）

把「构造 history」从 `ChatPage` 三处重复代码（`send` / `regenerateFrom` / `regenerate`，
`ChatPage.kt:455/495/533`）收敛成**一个函数**：

```kotlin
// chat/RequestBuilder.kt（纯 Kotlin）
fun buildMessages(state: ConversationState, userText: String, input: PromptAssembler.Input): List<LlmMessage>
```

三条路径**必然一致**，也为 B 批的「重放」打底。

### A7 · 缓存观测层

WebView 版 `ext/luzzy-prefix-guard.js` 已在用（包装 `window.fetch` 采集公共前缀 + `cached_tokens`）。
原生侧新增 `chat/CacheObserver.kt`（轻量、**只统计不干预**）：

- 每轮请求算「与上一轮 messages 的**公共前缀字符数**」→ 比值；
- 从 `UsageInfo` 取 `cached_tokens`（三家协议均有）→ 命中率 = `cached / prompt`；
- 内存统计供页面显示与 adb 探针读取，**失败静默降级**。

### A8 · 命中率展示

用量页（C1 接真库时）加「缓存命中」列 = `cachedTokens / promptTokens`（DSH 口径见 `StatsPills.tsx:109-121`）。

---

## 4 · 批 B 详案（完整 Agent Loop）

- **B1** `chat/AgentLoop.kt`：turn = 一次用户意图；step = 一次「请求 → 工具 → 回填」；
  状态 `Idle / Running(turn, step) / Aborting / Repairing`。
- **B2** 终止：无 tool_call → `completed`（**主终止条件**）／`max_tokens` **粘性**／`aborted`／`error`／
  **50 step 安全上限** → `stepLimit`（**我们的加法**，DSH 无此上限）。
- **B3** 工具配对：结果**按模型顺序**提交；abort 给未启动调用补合成结果
  `"Error: tool call aborted before dispatch"`；**崩溃修复**：启动时扫未闭合 turn，
  给悬空 `tool_call` 补「上次运行中断，该工具结果未知；仅只读/幂等操作可重试」。
- **B4** 中断保留：点停止后已收到的正文**以 `interrupted` 标记落库**（现为丢弃 = 真实缺陷）。
- **B5** 压缩：`tokens >= contextWindow × 0.8` 触发（`contextWindow` 进 `TransportConfig`，默认保守值）；
  保留 system 头 + 最近 `retainRatio`(0.16) 尾部，从最老的非 system 消息裁；
  **不切开 tool_call/tool_result 配对**；摘要调用**按原前缀重放**（吃一次缓存）；压缩记事件。
- **B6** 网络错误与 `context-overflow` 各重试一次（后者先压缩）。

---

## 5 · 批 C 详案（上游功能对账）

| # | 项 | 做法 |
|---|---|---|
| C1 | **五个假数据页** | 关于（版本走 `BuildConfig` + CHANGELOG 真源）／用量（`records(usage)` + Canvas 折线 + 缓存命中率）／记忆（`memories` 表真实统计 + 清空真执行）／角色卡（真列表 + 真实头像 + 切换 + 删除）／设置（用户档案真绑定 → 喂 A2 用户信息块；供应商配置接 `TransportStore`） |
| C2 | **真实角色图** | `AvatarImage`（`BitmapFactory` + `inSampleSize` + 小 LRU），替换 `SessionsPage.CharacterAvatar`（收了 `avatarPath` 没用）与 `ChatPage` 顶栏演示图；**可见性判据**（`width > 0`） |
| C3 | **多候选持久化** | 候选序列化进 `messages.payload` 私有键（**不改表**），读回重建；「杀进程重开」验证 |
| C4 | **附件真功能** | SAF `PickVisualMedia` → 压缩 → `filesDir/assets/attachments/` → `payload.imageAttachments` → **三家 Wire 加 image part**（OpenAI `image_url` / Anthropic `image` / Gemini `inline_data`）+ 待发缩略图条 |
| C5 | **表格列宽自适应** | 改 `MarkdownText.TableView`（内容估宽 + `ColumnWidth`） |
| C6 | **撤工作区入口** | 上游零对应物（已核实）→ 删按钮与提示分支 |
| C7 | **reduced-motion** | 读 `ANIMATOR_DURATION_SCALE`，为 0 时自绘时长归零 |

**维持现状（不算缺口）**：LaTeX / Mermaid / 代码高亮——**上游本身也没有**（vendor 里零命中）；
HTML 直通上游走 sandbox iframe，Compose 侧需单独立项。

---

## 6 · 批 D 详案

| # | 项 | 做法 |
|---|---|---|
| D1 | **用户可调字号** | `AppSettings` + `SettingsStore` 加 `fontScale`；缩放只改两处（`MarkdownTokens` + `Typography`），**不动 336 处 `.sp` 字面量**；设置页滑杆 |
| D2 | **导入导出** | SAF（`CreateDocument`/`OpenDocument`）；导出预设 `presets.json` 与 世界书 `world_info.json`（`records` payload **就是上游格式，零映射**）+ 角色卡 V2 JSON；导入走 `PresetEntry.from`/`WorldEntry.from`；**PNG 卡（tEXt）留下一批** |
| D3 | **迁移报告页** | 从 `kv[legacy.migrationCounts]` 渲染（成功/跳过/失败 + 明细），入口在设置页 |
| D4 | **文档** | §8 清单逐条划掉；`DESIGN-compose` 新增「§24 请求组装与缓存」「§25 Agent Loop」 |

---

## 7 · 验收标准

### 7.1 缓存（**本批最重要的可量化指标**）

1. **观测层实测**：连续 5 轮真实对话，`avgCommonRatio`（相邻两轮公共前缀占比）
   **≥ 0.95**（目标 1.0000，与 WebView 版 patch 047 同级）；
   **负控**：故意改一次设置，比值应掉到 < 0.5 并记一次 `headerChanged`。
2. `cached_tokens / prompt_tokens` 在用量页可见，且同一会话后半段显著高于第一轮。
3. 单测钉住「不变则不发」：同一快照求值两次 → 第二次返回 `null`。

### 7.2 Agent Loop

1. 单测（假传输）：无 tool_call 即停；有 tool_call 则继续；max_tokens 粘性；50 step 上限触发 `stepLimit`。
2. 工具配对：abort 后**每个 tool_call 都有对应结果**（含合成错误）；崩溃修复用例造悬空配对 → 被补上。
3. 中断保留：点停止后已生成内容**在库里**（不是只在内存）。
4. 压缩：造超阈值会话 → 触发一次压缩 → **配对未被切开** + 摘要落在稳定边界。

### 7.3 功能对账

- 每项一条断言（**可见性判据优先于数据判据**，上一批的教训）；
- 五个页面各一条「数据来自库」断言 + 亮/暗截图。

### 7.4 门禁

`ANDROID_SERIAL=emulator-5554 ./gradlew checkChat` 全绿（单测 + 仪器化）。
**每批独立提交、独立跑门禁**。

---

## 8 · 文档反向修正清单（D4 输入）

| # | 文档 | 仍写着 | 实际 |
|---|---|---|---|
| R1 | `DESIGN-migration.md` §11.4-1 | 占位符替换未实现 | 已修（`chat/Placeholders.kt`） |
| R2 | `DESIGN-compose.md` §20.3-3 | 预设/世界书编辑未验收 | 已实现（§23） |
| R3 | `DESIGN-migration.md` §8.5 / §10.6 | 迁移入口接线未做 | 已做（§11 端到端实测） |
| R4 | `DESIGN-migration.md` §10.6 | 设置持久化未做 | 已做（`AppSettings`/`SettingsStore`） |
| R5 | `PLAN-v3.0-compose.md` | P4 主条目未勾 + 一行重复 | 已收口；删重复行 |
| R6 | `DESIGN-migration.md` §8.5 | presets/worldinfo「必须重做」 | 已实现增删排序；**待核实**措辞后改 |
| R7 | `CHANGELOG.md:407` | at_depth 与纯追加冲突未解 | **本计划 A3 解决**（尾部快照） |

---

## 9 · 风险与明确不做

**风险**

| 风险 | 等级 | 缓解 |
|---|---|---|
| 尾部快照改变提示词**结构**（模型对位置敏感） | 中 | 真机抽测回复质量；快照带作废声明与完整内容；稳定块内已含 4 个 position |
| 压缩会**真正切断前缀**（不可避免） | 中 | 只在阈值触发 + 记事件 + 摘要调用自身吃缓存（DSH 同法） |
| Agent Loop 重构面大 | **高** | 分步提交：A（组装）→ B1/B2（循环）→ B3-B6（健壮性）；每步跑门禁 |
| 三家 Wire 的图片 part | 中 | 每协议一条单测 + 负控（不支持图片要**如实报错**，不静默丢图） |
| `contextWindow` 用户不知道填多少 | 低 | 默认保守值 + 提示文案；填错只影响压缩时机 |

**明确不做**

- 多 agent / 子 agent 委派、Cordis waterfall、沙箱与审批、LSP、MCP（DSH 特有）
- 角色卡 PNG（tEXt 块）导入导出（需自写 PNG chunk 编解码）
- 宽屏 `PermanentNavigationDrawer`（需宽屏设备验证）
- 嵌入模型替换词面召回（需嵌入模型）
- LaTeX / Mermaid / HTML 直通 / 代码高亮（上游也没有）
- 真机覆盖安装实测（**仍等用户授权**）

---

## 10 · 执行顺序与停止点

```
批 A（组装 + 缓存）→ 【停止点 1：用户真机试一轮，确认回复质量与缓存命中】
→ 批 B（Agent Loop）→ 【停止点 2：确认工具循环与中断行为】
→ 批 C（上游功能）→ 批 D（设计门项 + 文档）
```

**停止点 1 的理由**：A 批改变了提示词的**结构与位置**（模型对位置敏感），
且「人设字段裁决」与「注入改道」都会**影响回复质量**——这类改动必须由用户亲眼确认，
不能靠测试替代（既有纪律：门禁管正确性，人管观感）。

---

## 11 · 执行暂停点（2026-09-13，如实登记）

批 A 走到一半**主动暂停**，原因与重做方案：

**已完成并保留**
- A1 组装纯函数层（`PromptAssembler`/`WorldBookActivator`/`PromptSections`/`RuntimeSnapshots`），
  JVM 测试全绿；`WorldBookTool` 换真库；人设字段裁决（description+personality）已实现。
- A4/A5 已完成（本提交）：温度/最大输出持久化修复 + schema 字节稳定门禁。

**已实现但已回退（A6/A7 接线）——回退原因是两处真缺陷**
1. **快照落盘位置错了**：追加在消息列表**末尾**，但请求里它在用户消息**之前**
   → 存储顺序 ≠ 请求顺序 → 下一轮前缀照样从快照处断裂（与要解决的问题同型）。
2. 新增 `ChatMessage.Snapshot` 后，LazyColumn 的组合在测试中出现
   `SlotTable`/`SnapshotStateObserver` 运行时崩溃。

**重做方案（下次接手照此执行，勿再从零摸索）**
1. `send()` 重构为：先取 bundle（IO）→ **若发出快照，先落快照、再落用户消息**
   （快照在存储里的位置 = 它在请求里的位置）→ 再跑 turn。
2. 快照消息用 payload 标记持久化 + `toChatMessage` 认回 + 界面**不渲染但进请求**
   （`fromHistory` 标记已有）。
3. 验收加一条**字节级断言**：`turn1 请求 ⊂ turn2 请求`（纯追加的可执行证明）。
4. 注意：`ChatEngineTest` 的「召回块注入 system」断言要改成「召回块在尾部快照」；
   `ChatMessageTest` 的「消息类型」断言要更新为三变体。

**门禁现状（回退后）**：单测全绿（含 A4/A5 新增）；
仪器化在**长跑多轮的模拟器**上有一个用例
（`流式生成后正文上屏且出现用量脚注`）报
`Detected multithreaded access to SnapshotStateObserver`——
**stash 基线（已知全绿的 cb012be7）同样复现** → 判定为**环境劣化**（模拟器冷启动数小时 +
几十次 APK 装卸 + 无 GPU 软渲染），处置 = 干净重启模拟器后重跑（本会话早些时候
同一错误类重启后即消失）。重启后若仍红，再按 WORKLOG 会话 73 的记录继续查。

---

## 11.1 · 重做执行记录（会话 74，2026-09-13）

**上面的三步已按序完成**（提交 `3a22cded`）。执行中修正/发现了四件事，逐条登记：

| # | 项 | 结果 |
|---|---|---|
| 1 | 三步全部落地 | 「先落快照、再落用户消息」由 `RequestBuilder.Plan.appends` 一次性给出；快照以 `ChatMessage.Snapshot` + payload 标记持久化并在界面隐藏；验收断言 `turn1 ⊂ turn2` 做成 **JVM 字节级**测试（11 条） |
| 2 | **`retained` 改从日志推出**（修正 §3-A3 的 kv 方案） | kv 版在「重新生成历史中段」「快照落盘失败」两情形下判据失真；日志（最后一条快照消息）才是「模型看到过什么」的真源——重启天然安全、重放天然正确、落盘失败自愈。`PromptInputSource` 的三个 kv 快照方法已删 |
| 3 | **快照行的 `role` 用独立值 `snapshot`**（不是 `user` + 只靠 payload 标记） | 会话总览的「N 条」与预览行都由 SQL 聚合而来；独立 role 让 `WHERE role='user'` 天然排除快照，不必在每条 SQL 里写「payload 不含某标记」的脆判据。payload 标记**同时保留**（任一击中即认回），`scopeStats` 改为只数 `user`/`assistant` |
| 4 | **A4 的「请求头指纹 / headerChanged」此前未实现** | 本次随 A7 补齐（协议+端点+模型+采样值+工具集哈希，不含 apiKey），并成为 §7.1-2 负控的判据 |
| 5 | **重放路径不再发快照** | 「重新生成 / 编辑后重跑」用 `PromptAssembler.Input.emitSnapshot = false`：新快照在日志里没有位置可落，发了就会让请求与日志分叉（那是同一类缺陷的另一面） |
| 6 | 顺带修一处真缺陷 | `regenerate` / `regenerateFrom` 把目标用户消息**同时**放进 history 与本轮输入 → 请求里同一句话出现两遍 |

**验收实测**（`BatchACacheAcceptanceTest`，生产全链路 + 假传输）：

| 判据 | 实测 |
|---|---|
| 连续五轮 `avgCommonRatio` | **1.0000**（验收线 0.95，目标 1.0） |
| 命中率（cached/prompt） | 0.8000（4,000/5,000 tokens，假传输给的固定值） |
| 负控（改预设 + 换模型） | 当轮公共前缀 **0.0000**（< 0.5）· 累计 0.6152（跌破 0.95）· 缓存纪元变化 **1** |

**门禁现状（会话 74）**：JVM 单测 **475 条 / 0 失败，连跑 3 次稳定**。
仪器化测试（`checkChat`）本轮**未跑**——用户明确要求不使用模拟器；
`ChatPage` 的接线（落盘顺序、快照不渲染）因此**只有纯函数层被自动覆盖**，
UI 那一跳留给真机验收（判据与探针命令见 §11.2）。

### 11.2 · 真机验收步骤（会话 74 起用）

真机 = `df97f3c4`（小米 25098PN5AC / Android 16，日常机）。release 包不可 `run-as`、
数据库读不到，所以判据走 **logcat 探针 + 界面目视**：

```bash
adb -s df97f3c4 logcat -c && adb -s df97f3c4 logcat -s LuzzyCache
# 连续发 5 轮以上，每轮应出现两行：
#   落盘 idx=N 顺序=Snapshot→User      ← 快照先于用户消息（A6 的核心不变式）
#   本轮 轮 N · 公共前缀 … · 命中 …（cached/prompt） · 纪元变化 …   ← PLAN §7.1 三项指标
```

1. **落盘顺序**：`顺序=Snapshot→User`（首轮发快照时）；只有 `User` 说明本轮内容没变（去重生效）。
2. **公共前缀**：连续多轮后应 ≥ 0.95；若明显偏低，先看「纪元变化」是否在涨
   （改设置/换模型/换工具都会让缓存纪元断裂，那是**真实**失效，不是缺陷）。
3. **界面不渲染快照**：对话里**不该**出现 `Current runtime context…` 这类正文。
4. **用量页**：侧栏「用量」→「前缀缓存 · 本次运行」应显示与日志一致的数字。

### 11.3 · 真机实测记录（会话 74，`df97f3c4`）

**批 A 验收实测（§7.1）——达标，但轮数如实登记**

| 判据 | 实测 |
|---|---|
| `avgCommonRatio` | **1.0000**（验收线 0.95）——**2 轮**（用户认为够；「连续 5 轮」的字面要求由 JVM 侧 `BatchACacheAcceptanceTest` 覆盖） |
| 命中率 | 该轮 `输入 10,956（缓存 9,756）` = **89.0%**；会话累计 0.4710 |
| 缓存纪元变化 | 0（同模型同设置，无虚假失效） |
| 落盘顺序 | `落盘 idx=5 顺序=Snapshot→User` → **存储顺序 = 请求顺序**（A6 的核心不变式，真机成立） |

**真机才看得见的两个缺陷（已修，见 WORKLOG 会话 74 §2）**：旧设置搬运标记被误种导致
供应商配置永久丢失；首帧早于启动准备导致「未配置」不刷新。

**输入法不抬起输入岛 —— 已修（并更正本节早先的错误结论）**：
根因是**应用侧不消费 IME inset**（`enableEdgeToEdge()` 让 `adjustResize` 失效，
系统改用 inset 通道而全应用没有一处消费它）。修法：聊天页 Scaffold 加 `imePadding()`。
真机复验：发送键 y `2465 →（键盘弹起）1481 →（收起）2465`，分层探针
`compose ime == viewIme == 1036`、`decorH == screenH`。
⚠️ 本会话中途我曾判成「MIUI 根本不投递 inset（`imeBottom=1px`）」并在本节写成「未修好」——
**那是错的**：当时默认输入法正处于坏掉的注册状态，重新注册后同一台机器读到 1036px。
两个因素叠加：应用侧真缺陷（已修）+ 输入法环境坏状态。教训：单次环境测量不足以下
「平台不投递」这种结论。范围仅聊天页底栏；其它页面的编辑器走
`androidx.compose.ui.window.Dialog`（独立窗口，路径不同），**尚未真机验证**，登记为待办。

**顺带修掉一条概率性门禁**：`CotParserTest.解析成本的量级` 用挂钟卡「冷解析 < 1ms」而空载
实测 180µs（5.5× 余量），本机同时编译 + 装机时飘到 1246µs 假红（差值 0.25ms）。
已放宽为灾难性回归冒烟界（冷 < 6ms），符合本仓库「门禁判据必须确定性」的纪律。

**用户实测的 4 条主观发现**（真流式失效 / 不自动吸附置底 / 正则高亮缺失 / 首轮 CoT 在正文出现又消失）
**本轮未修**，分诊意见见 WORKLOG 会话 74 §4——其中 #1、#2 落在会话 74 改过的
`send`/`runTurn`/列表项路径上，**下一轮第一件事是与上一版 v3.0 包做 A/B**，
在拿到 A/B 结论前**不得**把它们写成「既有缺陷」或「已修」。

**新探针的坑（已修）**：`落盘顺序` 原先打印 `::class.simpleName`，release 包经 R8 改名后
日志变成 `顺序=bp→cp` 这种不可读形式。改用字面量标签（`appendLabel`）后真机可读。

---

## 12 · 进度勾选（执行时更新）

### 批 A
- [x] A1 组装纯函数化（`PromptAssembler` + `WorldBookActivator`，30 单测绿）
- [x] A1b ChatPage 接线真实输入（角色/用户/预设/世界书）
- [x] A2 稳定块/易变块分离（`PromptSections.kt` + 决定化排序）
- [x] A3 注入改尾部快照（`RuntimeSnapshots.kt`；retained 改由**日志**推出，见 §11.1-2）
- [x] A4 工具 schema 稳定化 + 变化检测（请求头指纹随 A7 补齐，见 §11.1-4）
- [x] A5 请求头冻结（含修 `TransportStore` 漏存 maxTokens/temperature）
- [x] A6 请求 = 状态纯函数（`RequestBuilder`；三条路径收敛 + 落盘顺序 = 请求顺序）
- [x] A7 缓存观测层（`CacheObserver.kt`）
- [x] A8 用量页命中率（真数据段；余下总用量/趋势图仍待 C1）

### 批 B
- [x] B1 turn/step 两级 + 状态机（`chat/AgentLoop.kt` 取代 `ChatEngine`；`AgentLoopTest` 18 条）
- [x] B2 终止条件（finish_reason 驱动 + 50 step 安全上限）——含 max_tokens **粘性**
- [x] B3 工具配对完整性（顺序 / abort 合成结果 / 工具抛异常不掀翻 turn）
- [x] B3b **工具轨迹落库**（`chat/ToolTrail.kt` + payload 键 `luzzyToolTrail`；展开进请求时补悬空）
- [x] B4 中断保留部分内容（补上 `interrupted` 标记的记账与落库；**UI 显示未做**）
- [ ] B5 压缩（`contextWindow` 进配置 + 保配对 + 摘要重放吃缓存）—— **未开始**
- [ ] B6 请求重试（网络错误 / context-overflow 各一次）—— **未开始**
- [x] B7 不变量守卫：工具续跑也必须纯追加（新老各一条逐字节用例）

### 批 C
- [ ] C1 五个假数据页接真库 　[ ] C2 真实角色图 　[ ] C3 多候选持久化
- [ ] C4 附件真功能 　[ ] C5 表格列宽 　[ ] C6 撤工作区 　[ ] C7 reduced-motion

### 批 D
- [ ] D1 字号 　[ ] D2 导入导出 　[ ] D3 迁移报告页 　[ ] D4 文档反向修正 + 设计真源
