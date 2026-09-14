# PLAN · 世界书（World Info）多书架构 + SillyTavern 能力对齐

> **立档**：2026-09-15（会话 82）。**任务来源**：用户指令——
> 「先停下并记录当前工作节点，然后完成世界书相关开发任务，才可继续测试」。
> **调研真源**：`docs/RESEARCH-worldbook-sillytavern.md`（三方对照表 + ST 机制 + 注入链路不变量）。
> **硬约束**：**不得破坏 KV 前缀缓存优化**（批 A 验收：公共前缀 1.0000 / 命中 89%）。

---

## 0. 用户已拍板（不再重复问）

| 决策 | 取值 |
|---|---|
| 实现范围 | **参考图展示的全部**：次级关键词四逻辑 / 大小写敏感 / 全词匹配 / `@Depth` 三档 role / 粘性·冷却·延迟 / 完整条目编辑页 |
| 全词匹配默认值 | **默认关**（偏离 ST，中文友好；UI 写明） |
| `@Depth` role | **按缓存安全方式做**：role 成为尾部快照内的标注，不插历史、不改字节 |
| 架构 | **多本世界书**（列表 + 搜索 + 新建/导入；每本有名字与条目集；角色可绑定书） |
| 旧数据 | **自动迁移**：全局桶 → 「默认世界书」；角色卡内嵌 `worldInfo` → 该角色名下的书，一条不丢 |

**本轮不做**（如实登记，别当成已完成）：递归激活与 max steps / 包含组与权重 / token 预算
（Context%·Budget）/ 附加匹配源（角色描述·性格·场景·人设·创作者笔记）/ 触发器类型 /
outlet / 向量化激活（🔗）/ ST 的 outlet 宏。

---

## 1. 数据层（最大改动，风险最高）

### 1.1 存储模型

**现状**：`records(kind=global_worldinfo, owner="")` 一个全局桶 + 角色卡 payload 里的 `worldInfo` 数组。

**新模型**（照 ST：书是一等公民，条目属于书）：

| 存什么 | 落在哪 | 说明 |
|---|---|---|
| 世界书（书本身） | `records(kind=worldbook, owner="<bookId>")`，单条记录 `{id, name, createdAt, updatedAt}` | 一书记一行，便于列列表 |
| 条目 | `records(kind=worldbook_entry, owner="<bookId>")`，每条一行（`sortIndex` 即书内顺序） | **一条一行**：与消息表同款理由（高频增量写不重写整本） |
| 角色绑定 | 角色卡 payload 的 `worldBookIds: [...]`（**新键**），保留旧 `worldInfo` 字段不删 | 不删旧键 = 回滚安全 |
| 全局启用 | `kv["worldbook.enabled"] = ["<bookId>", …]` | ST 的「global World Info selector」等价物 |
| 定时效果状态 | `kv[scopeId]["worldbook.timedEffects"]` | 见 §2.4 |

### 1.2 迁移（`WorldBookMigration`）

```
① 全局桶 records(global_worldinfo) 非空 → 建一本名为「默认世界书」的书，
   条目按原顺序搬入（逐条保留 payload 其余键），并把该书加入全局启用列表；
② 每个角色卡 payload.worldInfo 非空 → 建一本「<角色名> 的世界书」，
   条目搬入，并把该书 id 写进该角色的 worldBookIds；
③ 幂等：写 `kv["worldbook.migrated"]` 标记；二次运行直接跳过；
④ 旧键**不删**（回滚安全 + 「迁移对旧数据只读」纪律沿袭）；
⑤ 纯 Kotlin（`data/world/WorldBookMigration.kt`），输入输出都是纯数据 → JVM 可测。
```

**验收**：迁移前后条目数 / 逐条 content 与 fields 相等（JVM 单测用真夹具
`app/src/test/resources/legacy/webview-db-fixture.json` 的 `rp_hub_worldinfo` 键）。

### 1.3 类型扩展（`WorldEntry` 新字段，全部给默认值 → 旧数据零破坏）

| 新字段 | 类型 | 默认 | 对齐 |
|---|---|---|---|
| `secondaryKeys` | `List<String>` | 空 | ST `keysecondary` |
| `secondaryLogic` | enum `AND_ANY / AND_ALL / NOT_ANY / NOT_ALL` | `AND_ANY` | ST `selectiveLogic`（0/1/2/3 数字映射） |
| `caseSensitive` | `Boolean` | `false` | ST `caseSensitive` |
| `matchWholeWords` | `Boolean` | **`false`**（用户拍板，偏离 ST） | ST `matchWholeWords` |
| `depthRole` | enum `SYSTEM / USER / ASSISTANT` | `SYSTEM` | ST `@Depth` 三档 |
| `sticky` / `cooldown` / `delay` | `Int` | `0` | ST 同名（0 = 关） |

**别名与写回**：照既有纪律——读取认 ST 原始键名（`keysecondary` / `selectiveLogic` /
`caseSensitive` / `matchWholeWords` / `role`）与我们的规范名；`mergeInto` 写规范名并删别名。
`WorldEntry.ALIAS_KEYS` 相应扩充。**旧字段（keys/constant/position/order/depth/scanDepth/
probability/useProbability/useRegex）语义一字不改**。

### 1.4 导入导出（CCv3 / ST 格式）

- **导入**：认三种形态——① 裸数组（`[{...entries}]`）；② ST 的
  `{entries: {uid: {...}}}` 对象映射；③ 角色卡 `character_book`（CCv3）。
  字段名走既有 `from()` 的别名表 + 新增 ST 键名映射。**导入 = 新建一本书**（不合并）。
- **导出**：`{name, entries: [...]}`（ST 可读），文件名 `<书名>.json`。
- 现有「导出世界书（全局书）」入口改为「按书导出」（世界书页每本书的菜单里）。

---

## 2. 激活层（`WorldBookActivator` 扩展）

### 2.1 激活判定链（对齐 ST，保持纯函数 + 可测）

```
for entry in book.entries (enabled only):
  ① delay 门：chatMessageCount < entry.delay → 不激活（本轮直接跳过，不参与后续）
  ② 常驻（constant）→ 命中（score = ∞），跳过关键词与概率？（ST：常驻仍受概率/粘性影响 → 照 ST）
  ③ 关键词：
       主键任一命中（按 caseSensitive / matchWholeWords / useRegex）
       + 次级键按 secondaryLogic（AND_ANY / AND_ALL / NOT_ANY / NOT_ALL）
       → 得分 0 则不激活
  ④ 粘性缓冲：若该条目处于 sticky 有效期内 → **直接激活且忽略概率**（ST 规则）
  ⑤ 冷却：若处于 cooldown 有效期内 → 不激活
  ⑥ 概率：每轮每条只掷一次（既有 Dice 机制复用）
```

**签名变化**：`activate(rows, recentMessages, settings, dice, state)` —— 多一个
**纯数据**的 `TimedEffectState`（`Map<entryKey, EffectWindow>`）与 `chatMessageCount`。
仍是纯函数（无 IO、无时间依赖）→ 现有 22 例单测全部可保留。

### 2.2 匹配细节（照 ST，逐条写明）

- `matchWholeWords=true`：单键用 `(?:^|\W)(key)(?:$|\W)` 边界；多词键（含空格）退化为子串包含；
- 多词键与 `\W` 边界在中文场景的局限：**UI 明确写「中文建议关闭」**（默认已关）；
- 正则键：既有 `regexOf`（强制 `i`）保留；**新增**：`caseSensitive=true` 时**不加 `i`**
  （ST 的 `#transformString` 语义）；非法正则仍静默不匹配（不炸整轮）；
- 扫描文本：现为 `\n` 连接纯文本。**新增角色名前缀**（ST 的 `include_names`，默认开）：
  `"<角色名>：<正文>"` 逐条前缀 → 让「谁说的」可被正则锚定。
  **缓存影响**：无（扫描文本只用于匹配判定，不进请求）。

### 2.3 `@Depth` role 三档（缓存安全路径）

- 快照渲染时按 role 分组：`world-dynamic` 快照文本内部分段
  `[system] …` / `[user] …` / `[assistant] …`，**仍是同一条快照消息**（不新增消息、不插历史）；
- 快照内容变 → 本来就重发（`RuntimeSnapshots.project` 的既有语义），**纯追加不变**；
- 与用户的「不破坏 KV 缓存」要求一致（用户已拍板此路径）。

### 2.4 定时效果状态（跨轮持久化）

```kotlin
// 纯数据 + 纯函数推进（可 JVM 测）
data class TimedEffect(val hash: Int, val start: Int, val end: Int, val protected: Boolean)
class TimedEffectState(val sticky: Map<String, TimedEffect>, val cooldown: Map<String, TimedEffect>)

TimedEffects.advance(state, entries, chatMessageCount): TimedEffectState
  ├─ 聊天未推进（count <= start 且 !protected）→ 移除该效果（ST 规则）
  ├─ count >= end → 移除；若是 sticky 结束且条目配了 cooldown → **立即进入冷却**
  └─ 本轮激活的条目 → 种下/续期（sticky 不刷新时长，ST 规则）
```

- 存 `kv[scopeId]["worldbook.timedEffects"]`（**不进消息序列** → 不改历史字节）；
- 分支：本轮**各自独立**（复制分支不带走效果状态）；ST 的「分支继承父状态」语义如实登记为
  后续项（需与分支复制链路一起改）。
- 条目被编辑 → **强制移除**其效果（ST 规则；按 `hash` 判定，hash 由条目关键字段算出）。

---

## 3. 注入层（**零破坏**是硬指标）

- `PromptSections.stableSections`：**不动**（4 个稳定 position 语义不变）；
- `PromptSections.snapshotSections`：`at_depth` 条目按 `depthRole` 加标注（§2.3），
  排序仍按 `compareBy(comment, order, content)` **决定化**；
- `RuntimeSnapshots`：**不动**（内容不变即不发）；
- `RequestBuilder`：**不动签名**（世界书输入仍来自 `PromptAssembler.Input.worldEntries`，
  只是「哪些条目激活」由新的 `activate` 决定）；
- **回归门禁**：`BatchACacheAcceptanceTest`（五轮纯追加 + 字节级断言）必须继续全绿；
  `RequestBuilderTest` 的世界书相关用例 + 新增「带定时效果时前缀仍纯追加」用例。

---

## 4. UI（照参考图，4 个层级）

| 页面 | 照图实现 | 落点 |
|---|---|---|
| **世界书列表** | 搜索框 + 书行（书名 + ⋯ 菜单）+ FAB「新建」→ BottomSheet（新建世界书 / 导入世界书 · 支持 CCv3 Spec 与 SillyTavern 格式） | `ui/pages/world/WorldBookListPage.kt`（新） |
| **编辑世界书** | 书名（必填 *）+ 「条目」区（搜索 / 添加）+ 条目行（激活策略图标 🔵常驻/🟢关键词 + 名称 + 编辑/复制/删除 + 启用开关）+ 底部大「保存」 | `ui/pages/world/WorldBookEditPage.kt`（新） |
| **编辑条目** | 名称 * / 内容（多行）/ 激活策略（二级页：常驻·关键词）/ 激活概率（数值弹层 0-100）/ 注入位置（二级页 8 值）/ 关键词匹配（关键词 + 次级关键词 + 扫描深度 + 区分大小写 + 全词匹配）/ 延时作用（粘性·冷却·延迟，各配数值弹层 + 说明文案） | `ui/pages/world/WorldEntryEditPage.kt`（新） |
| **二级选择页** | 激活策略 / 注入位置 / 数值输入（照图 7/8/6 的 BottomSheet 形态 + 说明文案逐条照抄语义） | 同上文件内的 Sheet 组件 |

**视觉纪律**：全部复用 `PageKit`（`SettingCard` / `SettingRow` / `LuzzySwitch` / `EntryCard` /
`SectionTitle`）+ 织机 Loom token（**零新色相**）；**不照抄参考图的紫色主题**（那是 ST 谱系 App 的品牌色）。
**设计门**：本轮是「照参考图实现既有页面族」——属**功能实现**，视觉语言沿用既有设计真源
（P4-C 的 `DESIGN-compose §23` 已立「纸页清单 + 抽页编辑」范式）；
因新增了「书名 + 底部大保存按钮」等结构，**落档 `DESIGN-compose §31`** 并在完成后走
五维 critique。**若你认为这仍需三方向门，请在我开工前纠正**。

---

## 5. 分阶段执行（每阶段可独立验收）

| 阶段 | 内容 | 验收 |
|---|---|---|
| **S1 数据层** | `WorldEntry` 新字段 + `WorldBook` 存储模型 + `WorldBookMigration`（自动迁移）+ 导入导出 | JVM 单测（新字段读写往返 / 别名 / 迁移前后逐条相等 / 幂等 / 导入三形态）；**旧夹具 100% 通过** |
| **S2 激活层** | 次级关键词四逻辑 / 大小写 / 全词 / 扫描文本角色名前缀 / `TimedEffects` 推进 | JVM 单测（四逻辑真值表 / 全词中英文差异 / 粘性冷却延迟时间线照 ST 示例 `M0..M7`） |
| **S3 注入层** | `@Depth` role 标注 + 快照渲染 | **`BatchACacheAcceptanceTest` 全绿** + 新增「定时效果不破前缀」用例 |
| **S4 UI** | 4 层级页面 + 从聊天页/侧栏入口接通 | 仪器化用例（列表/编辑/条目字段往返）+ 模拟器逐页截图 + `read_image` 审查 |
| **S5 收尾** | 文档（RESEARCH 已就位 / DESIGN §31 / CHANGELOG / WORKLOG）+ 门禁（JVM + checkChat）+ 提交 | 全绿 + 工作区干净 |

**每阶段结束跑一次 JVM 全量**；S3 后与最终各跑一次 `checkChat`。

---

## 6. 风险与对策

| 风险 | 对策 |
|---|---|
| 数据层重构破坏既有世界书数据 | 迁移纯函数 + 逐条相等断言 + **旧键不删**（回滚安全）；迁移幂等标记 |
| 破坏 KV 前缀缓存 | 新能力全部落在**激活层**（不改请求字节）或**尾部快照内**（本来就会重发）；`BatchACacheAcceptanceTest` 作硬门禁 |
| 全词匹配在中文静默失效 | 默认关 + UI 写明「中文建议关闭」（用户已拍板） |
| 定时效果状态与分支/压缩交互 | 本轮分支各自独立（登记为后续项）；效果状态不进消息序列 → 与压缩水位线无交互 |
| UI 工作量（4 层级页面） | 全部复用 PageKit；参考图是规格真源，不重新发明视觉 |

---

## 7. 明确不做（本轮）

参考图未展示的能力（§0 末段全部）+ 设置页重设计（boards-v7，等本任务完成后回去走
「停轮等用户选方向」）+ P6 剩余验收项。
