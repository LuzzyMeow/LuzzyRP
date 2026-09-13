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

## 11 · 进度勾选（执行时更新）

### 批 A
- [x] A1 组装纯函数化（`PromptAssembler` + `WorldBookActivator`，30 单测绿）
- [ ] A1b ChatPage 接线真实输入（角色/用户/预设/世界书）
- [ ] A2 稳定块/易变块分离（`PromptSections.kt` + 决定化排序）
- [ ] A3 注入改尾部快照（`RuntimeSnapshots.kt` + retained 持久化）
- [ ] A4 工具 schema 稳定化 + 变化检测
- [ ] A5 请求头冻结（含修 `TransportStore` 漏存 maxTokens/temperature）
- [ ] A6 请求 = 状态纯函数（三条路径收敛）
- [ ] A7 缓存观测层（`CacheObserver.kt`）
- [ ] A8 用量页命中率

### 批 B
- [ ] B1 turn/step 两级 + 状态机
- [ ] B2 终止条件（finish_reason 驱动 + 50 step 安全上限）
- [ ] B3 工具配对完整性（abort / 崩溃修复）
- [ ] B4 中断保留部分内容
- [ ] B5 压缩（阈值 + 保配对 + 稳定边界）
- [ ] B6 请求重试

### 批 C
- [ ] C1 五个假数据页接真库 　[ ] C2 真实角色图 　[ ] C3 多候选持久化
- [ ] C4 附件真功能 　[ ] C5 表格列宽 　[ ] C6 撤工作区 　[ ] C7 reduced-motion

### 批 D
- [ ] D1 字号 　[ ] D2 导入导出 　[ ] D3 迁移报告页 　[ ] D4 文档反向修正 + 设计真源
