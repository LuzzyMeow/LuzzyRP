# RESEARCH · 世界书（World Info）架构与注入调研

> **任务来源**：用户 2026-09-15 指令——深度调研 SillyTavern 世界书架构与注入方式、
> 调研上游 RP-Hub 的世界书注入实现，然后按参考图实现功能。
> **硬约束**：**不得破坏现有 KV 前缀缓存优化**（批 A 验收：公共前缀 1.0000、命中 89%）。
> **调研日期**：2026-09-15（会话 82）。全部结论带来源；未经验证的推断已显式标注。

---

## 一、三方对照总表（本任务的核心产出）

| 能力 | SillyTavern（参考图规格） | 上游 RP-Hub 1.9.3 | LuzzyRP 现状（v3.0.0） | 差距 |
|---|---|---|---|---|
| 条目字段 | `comment`(memo) / `content` / `key` / `keysecondary` / `selectiveLogic` / `constant` / `vectorized` / `position` / `order` / `depth` / `probability` / `useProbability` / `sticky` / `cooldown` / `delay` / `scanDepth`(条目级) / `caseSensitive` / `matchWholeWords` / `group` / `groupWeight` / `automationId` / `role` | `comment` / `content` / `keys` / `enabled` / `constant` / `position`(7 值) / `order` / `scanDepth` / `probability` / `useProbability` / `useRegex` | 与上游**逐字段同构** + `scope`（全局/角色） | **缺 8 项**（见下） |
| 激活策略 | 常驻 🔵 / 关键词 🟢 / 向量 🔗 | 常驻 / 关键词（子串，大小写不敏感） | 同上游 + 正则 | 缺向量（已有记忆向量，可复用） |
| 次级关键词 | `keysecondary` + 四逻辑（AND_ANY / AND_ALL / NOT_ANY / NOT_ALL） | ❌ 无 | ❌ 无 | **缺**（参考图有「次级关键词匹配」） |
| 概率 | `probability` 0-100 | ✅ 同 | ✅ 同（每轮每条目掷一次） | 一致 |
| 注入位置 | 8 值：before_char / after_char / AN_top / AN_bottom / at_depth(+role: system/user/assistant) / EM_top / EM_bottom / outlet | 7 值（system_top / global_note / before_char / after_char / at_depth / user_top / assistant_top） | ✅ 7 值（同上游） | **缺 @Depth 的 role 细分**（参考图三档：⚙️system/👤user/🤖assistant） |
| 扫描深度 | 全局 + 条目级覆盖 | 全局 + 条目级覆盖 | ✅ 两者都有 | 一致 |
| 大小写敏感 | 全局开关 + 条目级覆盖 | ❌（恒不敏感） | ❌（恒不敏感） | **缺**（参考图有「区分大小写」） |
| 全词匹配 | 全局开关 + 条目级覆盖，**默认开**；文档明示中日韩需关 | ❌ | ❌ | **缺**（参考图有「全词匹配」且为开） |
| 定时效果 | sticky / cooldown / delay（按消息数计；粘性期内忽略概率；冷却在粘性结束时开始） | ❌ | ❌ | **缺**（参考图「延时作用」三行） |
| 递归激活 | recursive scan + max steps + 非递归/阻断递归/延迟到递归 | ❌ | ❌ | 缺（参考图未展示，本轮不做） |
| 包含组 | inclusion group + weight + priorize + group scoring | ❌ | ❌ | 缺（参考图未展示，本轮不做） |
| 预算 | Context% / Budget（token 上限） | ❌ | ❌（有压缩机制 B5） | 缺（参考图未展示） |
| 附加匹配源 | 角色描述/性格/场景/人设/角色笔记/创作者笔记 | ❌ | ❌ | 缺（本轮不做） |
| 触发器类型 | normal/continue/impersonate/swipe/regenerate/quiet | ❌ | ❌ | 缺（本轮不做） |
| 注入实现 | 构建 prompt 时按 position 分组拼装（worldInfoBefore / worldInfoAfter / depth 条目插进聊天历史 / AN 前后） | `resolveWorldInfoEntries` 分组 → 组装请求时按 position 拼 | `PromptSections`（稳定块 4 position）+ `RuntimeSnapshots`（漂移 3 position 改道尾部） | ✅ 已有骨架，且**已按缓存性质改道** |

## 二、SillyTavern 关键机制（来源：官方文档 + `world-info.js` 源码）

### 2.1 激活判定链（`checkWorldInfo` / `WorldInfoBuffer`）

1. **扫描文本构建**：最近 `scanDepth` 条消息（**逐条**可覆盖），消息**带角色名前缀**
   （`include_names` 默认开，v1.12.6 起用 `\x01` 作分隔符便于正则锚定）；
   再按需追加**附加匹配源**（角色描述/性格/场景/人设等）与**递归缓冲**。
2. **关键词匹配**：`/\x01{{user}}:[^\x01]*?hello/` 这类正则可用分隔符精确锚定「谁说的」。
   纯文本键**不支持逗号**（逗号是分隔符）；正则键可用逗号。
   `matchWholeWords` 默认开，用 `(?:^|\W)(key)(?:$|\W)` 边界匹配 —— **文档明确警告**：
   中日韩等**不用空格分词**的语言应关闭（否则匹配不到）。
3. **评分**：主键命中数 1 分/个；次级键按 `selectiveLogic` 影响评分（AND_ANY 相加、
   AND_ALL 全中才相加、NOT_* 不加）。**得分为 0 的条目不会被激活**。
4. **概率**：`probability` 0-100，**每轮每条目只掷一次**（`probabilityResults` Map 缓存）。
5. **触发图 → 排序 → 分组**：常驻条目优先（`score=Infinity`），其余按 `order` 排序后
   按 `position` 分进 8 个桶；桶内再按 `order` 升序。

### 2.2 定时效果（Timed Effects，`WorldInfoTimedEffects`）

- 记在**聊天元数据**里（`chat_metadata.timedWorldInfo[sticky|cooldown][key]`，key = `world.uid`），
  每条记 `{hash, start, end, protected}`；
- **sticky**：激活后保持 N 条消息；**粘性期内忽略概率检查**；粘性结束**若配了 cooldown 立即进入冷却**；
- **cooldown**：激活后 N 条消息内不能再激活；
- **delay**：聊天消息数 < N 时**不能**激活（`delay=1` = 空聊天不能激活）；
- **不变量**：① 效果只作用于激活它的那条聊天（分支继承父聊天状态）；
  ② **聊天未推进**（swipe/删除最后一条）则移除效果；③ 修改条目会**强制移除**其效果；
  ④ **重复触发不刷新时长**；
- 官方示例：`sticky=3, cooldown=2, delay=2` → `M0 delay / M1 激活 / M2-M4 sticky / M5-M6 cooldown / M7 可再激活`。

### 2.3 注入位置语义（8 值）

`before_char`（角色定义前，影响中等）/ `after_char`（角色定义后，影响较大）/
`EM_top`·`EM_bottom`（作为**对话示例块**解析后插在示例前/后）/
`AN_top`·`AN_bottom`（作者注顶部/底部；**作者注禁用则此位置条目被忽略**）/
`@Depth`（插进聊天历史指定深度，`Depth 0` = 提示词最底；带 **role** 细分
⚙️system / 👤user / 🤖assistant）/ `outlet`（不自动注入，用 `{{outlet::Name}}` 宏手动拉取）。

### 2.4 激活设置（全局）

`scan_depth` / `include_names` / `context%`·`budget`（token 预算，超预算即不再激活；
**常驻条目优先插入**，其次 order 大者；**直接命中键的条目优先级高于被其他条目内容提到的**）/
`min_activations`（配 max depth，**与 max recursion steps 互斥**）/
`recursive` + `max_recursion_steps`（0 = 只受预算限制）/
`case_sensitive` / `match_whole_words`（均**默认关/开**如上）/ `alert_on_overflow`。

## 三、上游 RP-Hub 的注入实现（`data-services.js:789-860`）

```
resolveWorldInfoEntries(entries, messages, settings, options)
├─ 过滤 enabled !== false
├─ 常驻（constant）→ 直接进 triggerMap（score = Infinity，matchedKeys = ['常驻 (Constant)']）
├─ 逐条：scanDepth = 条目级 ?? 全局；maxDepth > 0 时取 min（即「最大深度是上限」）
│         scanDepth === 0 或 keys 为空 → 跳过
│         关键词命中（useRegex ? 正则 : 子串，均大小写不敏感）
│         概率（useProbability === false 或 >= 100 → 必过；否则按条目缓存掷一次）
├─ 排序：常驻优先 → 其余按 order **降序**（注意：与 ST 的桶内升序方向相反）
└─ 分组：system_top / global_note / before_char / after_char / user_top / assistant_top / at_depth
          桶内按 order **升序**
```

**上游的能力边界（实测确认）**：无次级关键词、无大小写/全词开关、无定时效果、无递归、
无包含组、无 token 预算。**参考图展示的是 SillyTavern 的模型，不是上游的**——
即用户要的是「把 ST 的能力带到 LuzzyRP」，同时保留上游已有的 7 位置语义。

## 四、LuzzyRP 现有注入链路（必须守住的不变量）

```
ChatPage ──► RequestBuilder.plan(state, userText, input, freshTurn, …)   [纯函数]
              ├─ historyOf(state)         历史逐字搬运（不改写、不合并、不重排）
              ├─ RecallEngine.search()    记忆召回（进快照）
              ├─ PromptAssembler.assembleDetailed(effective)
              │    ├─ PromptSections.stableSections()   ← 稳定块：破限/system 预设/
              │    │      world_system_top / world_global_note / user-info / character /
              │    │      world_before_char / world_after_char / tool-hint
              │    └─ PromptSections.snapshotSections() ← 尾部快照：world at_depth /
              │          user_top / assistant_top + memory-recall
              └─ RegexScripts.applyToPromptMessages()  提示词侧正则 + 文风过滤（最后一步）

RuntimeSnapshots.project(current, retained)
  ├─ 内容与上次**逐字相同** → 不发（多数轮次前缀完全不变 ← 命中率的来源）
  └─ 变了才发新的（旧快照留在历史里，前缀永不被改写）
```

**三条硬不变量（改世界书时一条都不能破）**：

1. **稳定块只放「不随轮次变」的内容**——世界书的 4 个稳定 position 走 `PromptSections`；
2. **随轮次漂移的内容一律进尾部快照**——`at_depth` / `user_top` / `assistant_top` 已改道，
   且**内容不变就不发**（这是 1.0000 前缀占比的直接来源）；
3. **同 order 内的顺序必须决定化**——`worldSection` 按 `comment` 字典序排（不是用户拖拽序），
   否则拖拽一下整段 system 位移、缓存静默失效。
   **`snapshotSections` 同理**：按 `compareBy(comment, order, content)` 决定化。

## 五、结论：新增能力往哪儿落（本任务的实现约束）

| 新能力 | 落点 | 对缓存的影响 | 处理 |
|---|---|---|---|
| 次级关键词（四逻辑） | 激活层（`WorldBookActivator`） | **无**（只影响「是否命中」） | 直接加 |
| 大小写敏感 / 全词匹配 | 匹配层 | **无** | 直接加，**默认值照 ST**（大小写不敏感 / 全词关——见下「偏离说明」） |
| 概率 | 已有 | 无 | 复用 |
| 粘性 / 冷却 / 延迟 | **激活状态层**（需要跨轮状态！） | **无**（状态只决定「本轮哪些条目激活」，不改变稳定块字节） | 新增状态存储，见下 |
| `@Depth` 的 role 细分（system/user/assistant） | **尾部快照**内 | **有影响**：快照文本会多一行 role 标记 → 快照变化时本来就会重发，**不违反纯追加** | 落在快照的渲染里（role 影响的是「快照内怎么标注」，不新增消息） |
| 全词匹配默认值 | — | — | **偏离 ST**：ST 默认开且文档警告中日韩要关；**我们的用户主要写中文** → 我们**默认关**（并在 UI 写明），避免「开了全词导致中文条目不触发」的坑 |

**粘性/冷却/延迟的状态放哪**（关键设计决策）：

- 它们**必须跨轮持久化**（ST 存在 `chat_metadata.timedWorldInfo`）；
- 我们的对应物 = **分支作用域的 kv**（`LuzzyStore` 的 kv 表，按 `scopeId` 分桶，
  与 `legacy.migrationReport` 同族）——**不放进消息序列**（否则会改历史字节，破坏不变量 1）；
- 分支继承：ST 的「分支继承父聊天状态」在我们的模型里 = 复制分支时带走该 kv 键
  （`BranchModel` 的复制路径需要带上——**本轮先按「分支各自独立」实现并如实登记**，
  继承语义留待分支复制链路一并处理）。

## 六、待用户拍板的实现范围（本轮做多少）

**参考图直接展示的（建议本轮全做）**：次级关键词+四逻辑 / 大小写敏感 / 全词匹配 /
`@Depth` role 三档 / 粘性·冷却·延迟 / 完整条目编辑页（含「注入位置」二级选择页、
「激活策略」页、数值输入弹层）。

**参考图未展示、本轮不做**（如实登记，避免被当成已完成）：递归激活、包含组与权重、
token 预算（Context%/Budget）、附加匹配源（角色描述/场景/人设）、触发器类型、
outlet、向量化激活（🔗，我们已有记忆向量管线，可作为后续独立项接入）。

## 七、来源

- SillyTavern 官方文档 `World Info`：https://docs.sillytavern.app/usage/core-concepts/worldinfo/（全文已读）
- SillyTavern 源码 `public/scripts/world-info.js`（6289 行，本轮读关键段：
  `world_info_logic` / `WorldInfoBuffer` / `WorldInfoTimedEffects` / `world_info_position` /
  `getWorldInfoSettings`）：https://github.com/SillyTavern/SillyTavern/blob/release/public/scripts/world-info.js
- 上游 RP-Hub `assets/js/data-services.js:789-860`（`resolveWorldInfoEntries`，本地 `rp-hub-reference/`）
- 本地实现：`WorldEntry.kt` / `WorldBookActivator.kt` / `WorldInfoSettings.kt` /
  `PromptSections.kt` / `RuntimeSnapshots.kt` / `RequestBuilder.kt`
