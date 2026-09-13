# PLAN-v3.0 · 预设 / 世界书编辑（master plan P4-C 第 4.2 项）

> **定位**：`docs/PLAN-v3.0-compose.md` §P4-C「预设 / 世界书编辑（现在只读）」的拆解与执行文档。
> 上游语义全部以干净参考克隆 `rp-hub-reference/` 为准（行号即证据），我们的现状以工作树为准。
>
> **设计门**：本批**用户明示豁免三方向**（原话与理由落档 `docs/design/boards-v6/direction-approved-v6.md`）。
> 硬性规定 9 的其余四步照走：4 项 SKILL 主文档 **2026-09-13 完整重读**、设计真源仍为 `docs/DESIGN-compose.md`、
> 动效令牌沿用 `Motion`（200/140 / `cubic-bezier(0.23,1,0.32,1)`）、交付前对照 pro-rules 清单（本文 §4.5）。
> ui-ux-pro-max 检索已跑（`--stack jetpack-compose` 与 `--domain ux`，命中见 §4.3/§4.5）。

---

## 0 · 现状锚点

### 0.1 上游语义（`rp-hub-reference/`）

| 问题 | 结论 | 证据 |
|---|---|---|
| 预设是「多个预设集」吗 | **不是**。单一扁平数组，元素 `{name, role, content, enabled}`；无 `presetId`/`activePreset`/分组容器 | `app.js:974, 983-989`；全库 grep `activePreset\|presetId\|presetGroups` 零命中 |
| 预设怎么寻址 | **数组下标即身份**（`editPreset(index)`、写回 `presets.value[editingPreset.id]`）；拖拽直接改数组序 | `app.js:8530-8544, 1589-1608` |
| 预设怎么生效 | 启动时**强制同步内置**：`破限` 等 `BUILTIN_CORE_PRESETS` 的 content **每次启动重置为内置版本**（只保留 enabled）；COT 内容按记忆/面板动态重建；`managedPresets` 插入或挪位 | `app.js:8589-8660, 8663-8689`；`built-in-content.js:368-433, 709` |
| 请求组装顺序 | 破限 → 世界书 `system_top` → `global_note` → `[System Presets]` 其余 system → 用户信息 → 工具 → UI 模板 → COT 追加在 system 末尾；role=user/assistant 的预设作为**独立消息**紧跟首条 system 之后 | `app.js:4521-4628` |
| 世界书 `scope` 语义 | 只有 `'global'` \| `'character'` 两个字符串，**与角色 uuid 无关**；`systemWorldInfoNames=['自动生图']` 强制 global | `core-utils.js:613, 952`；UI 选项 `app.js:2430-2433` |
| 世界书存哪 | **两份独立数据**：`global_worldinfo`（库键）+ 每张角色卡的 `char.worldInfo`；**没有** per-character 独立键。组合视图 = 全局 + 当前角色（全局在前） | `app.js:1154, 1992-1995, 8177-8188` |
| 旧键怎么办 | `worldinfo` 若存在而 `global_worldinfo` **也存在 → 整份忽略**；只有 global 缺失时才把旧键当全局书 | `app.js:1906-1914` |
| 字段默认值 | `comment ''`、`content ''`、`enabled true`、`keys []`（字符串按 `,，` 拆）、`useRegex false`、`constant false`、`position 'at_depth'`、`order 0`、`depth 4`、`scanDepth null(=全局)`、`probability 100`、`useProbability true`；`extensions` 会被摊平进条目 | `core-utils.js:556-624` |
| position 合法值 | `system_top / global_note / before_char / after_char / at_depth / user_top / assistant_top`（含 SillyTavern 别名与 0-4 数字映射） | `core-utils.js:582-605` |
| 检索算法 | keys = **大小写不敏感子串**（非全词）；`useRegex` 走 `/pattern/flags`、**强制 i**；`constant` 跳过匹配 score=∞；`useProbability` 每条**每轮只掷一次**；扫描最近 N 条（`entry.scanDepth` 优先，否则全局 `settings.scanDepth`，再被 `maxDepth` 夹住） | `data-services.js:772-860` |
| 注入算法 | resolve（constant 优先、order 降序分组，组内 order 升序）→ `at_depth` 按 order 升序以 `role:'user'` 独立消息插到深度锚点；`user_top/assistant_top` 前插到最后一条 user/assistant 正文头部；`system_top/global_note` 进 system；`before_char/after_char` 进角色前置 user 消息 | `data-services.js:839-857, 978-1065`；`app.js:4558-4607` |
| 全局设置 | `{scanDepth:2, maxDepth:0}`（maxDepth=0 不限制）；藏在世界书面板的折叠区 | `app.js:1425-1428`；`index.html:2655-2687` |
| 上游编辑界面 | 预设面板 `index.html:1977-2042`（新建/编辑/删除/启停/拖拽排序/导入/导出）＋ 编辑弹窗只暴露 name/role/content（`ui-components.js:1117-1168`）；世界书面板 `index.html:2624-2742`（同套操作 + 全局设置滑杆），编辑弹窗暴露 comment/scope/keys/useRegex/constant/position/order/useProbability+probability/scanDepth/depth(仅 at_depth)/content（`ui-components.js:1604-1735`） | 同行 |
| 上游的特例 | 列表隐藏「第二/第三人称」条目；COT 在抗截断开启时禁改；「自动生图」条目的启停被打成**图像生成开关**；**没有**条目复制功能、**没有**「恢复默认」按钮 | `index.html:1997, 2022-2026`；`app.js:2055-2063` |

### 0.2 我们的现状

| 面 | 现状 | 证据 |
|---|---|---|
| 存储 | `records(kind, owner, slot, payload)` 一张表兜住整表读写集合；`kind ∈ {worldinfo, global_worldinfo, regex, global_regex, presets, token_usage_history, profile}`；`kv` 存 `worldinfoSettings` | `LuzzyEntities.kt:101-115`；`MigrationWriter.kt:156-170` |
| 读写门面 | `records(kind, owner)` / `replaceRecords(kind, owner, payloads)`（整组重写，slot=下标） | `LuzzyStore.kt:143-161` |
| 预设页 | `PresetsPage`：**静态假数据 3 行**（破限预设 v4 / COT / 写作风格），`EntryRow` 的开关与图标**无点击行为** | `StaticPages.kt:321-343, 67-121` |
| 世界书页 | `WorldInfoPage`：**静态假数据 3 行** + 假的滑杆（`fillMaxWidth(0.4f)` 写死） | `StaticPages.kt:246-316` |
| 聊天的世界书面板 | `WorldBookSheet` 读 `VanioCard.worldBook`（**演示角色内置数据**），注释自认只读 | `InputIslandSheets.kt:250-327` |
| 聊天的预设入口 | 一个提示气泡：`pendingFeatureHint("预设", "P4 的数据层（预设是用户数据）")` | `ChatPage.kt:707` |
| 角色数据 | 存储里有真角色时用真角色（`characterUuid`），空库才回落演示角色 `VanioCard` | `ChatPage.kt:208-245` |

### 0.3 三项必须裁决的数据问题（本章是本计划的真正起点）

**裁决 1 · 遗留世界书桶是幽灵数据（实测证据）**
夹具里 `rp_hub_worldinfo`（旧键）与 `rp_hub_global_worldinfo` 的正文**逐字节相同**：

```
[legacy] rp_hub_worldinfo = 2      · 自动生图 md5=1716d4c2 | 全局：语言风格 md5=61466feb
[global] rp_hub_global_worldinfo=2 · 自动生图 md5=1716d4c2 | 全局：语言风格 md5=61466feb
```

上游在两键并存时**忽略旧键**（§0.1）。而我们的迁移把旧键单独落成了 `kind="worldinfo"` 桶
（`MigrationWriter.kt:156`）→ 编辑页若照实读，用户会看到**双份「自动生图 / 全局：语言风格」**，
迁移报告里的「世界书 4」也是虚数（真实生效面 = 全局 2 + 角色 1 = 3）。

**裁决 2 · 角色绑定条目住在角色卡里**
夹具里只有 Vanio 有 1 条绑定条目（`旧书店的规矩`，`scope=character`），它随 `characters` 一起落进
`CharacterEntity.payload.worldInfo`（`LegacyMigrator.kt:287`）。所以「世界书」一共**三处来源**，
编辑必须知道写回哪一处——这不是实现细节，是数据契约（§2.2）。

**裁决 3 · 上游的启动强制覆盖不能照搬**
上游每次启动把 `破限` 等内容重置为内置版本（§0.1）。若我们照搬，用户刚改完的预设下次启动被**静默还原**——
这正是最坏的 UX（不报错、不提示、看不出谁改的）。理由详见 §7.1，结论：**不复刻**。

---

## 1 · 目标与非目标

**目标（本批）**：把两块用户数据从「只读假列表」变成**可管理、可编辑、真落盘**的界面——
新建 / 编辑 / 启停 / 排序 / 删除，全部字段可改且**未知字段零丢失**，聊天侧面板读到真数据。

**非目标（本批）**：**生效**（把预设与世界书接进请求组装）不在本批——那是 master plan 的 P5 对账项
（`docs/PLAN-v3.0-compose.md:100, 174`）。本批结束时编辑的是**真数据**，但请求体**尚未**消费它们；
聊天侧面板与页面都必须把这件事**如实写在界面上**，不许让用户以为已经在生效（§3.6 状态 6）。

---

## 2 · 数据契约

### 2.1 预设

- **真源**：`records(kind=RECORD_PRESETS, owner="")`，一个数组，**顺序即语义**（注入顺序就是数组顺序）。
- **元素字段**：`name`(string) / `role`(`system`|`user`|`assistant`) / `content`(string) / `enabled`(bool)。
- **无** id / 分组 / 「当前预设集」概念——与上游一致（§0.1）。因此新增条目**追加到末尾**，
  删除 = 从数组移除，排序 = 交换相邻项；三者都是一次整数组重写（`replaceRecords`），单事务。
- **不复刻上游特例**：不隐藏任何条目、不给 COT 特殊开关、不因角色人称自动启停（那些是消费侧行为，
  且我们尚无抗截断功能）。编辑器对全部条目一视同仁，行为可预期。

### 2.2 世界书（三来源裁决）

| 来源 | 存放 | 本批角色 |
|---|---|---|
| 全局条目 | `records(kind=RECORD_GLOBAL_WORLDINFO, owner="")` | **唯一全局真源** |
| 角色绑定条目 | `CharacterEntity.payload` 内的 `worldInfo` 数组（按角色 uuid） | **唯一角色绑定真源**（保持在角色卡里） |
| 旧键遗留桶 | `records(kind=RECORD_WORLDINFO, owner="")` | **按上游语义消灭**（下） |

**为什么角色绑定条目留在角色卡里**：① 它本来就是角色卡的一部分，导出/导入角色卡时随卡走（与上游一致）；
② 迁移已经这么落了，改动成本为零、返工风险为零；③ 若抽成独立表，角色卡里就会留下**第二份副本**，
立刻产生双真源。代价是编辑时要做一次「角色行 payload 的读-改-写」，集中在一个仓库函数里，可接受。

**迁移期修正（W0）**——按上游 `app.js:1906-1914` 的口径裁决旧键：

```
读 oldWorld = records/旧键 rp_hub_worldinfo，newGlobal = rp_hub_global_worldinfo
① newGlobal 非空 → 旧键整份丢弃，不写 records；迁移报告加一行「跳过遗留世界书 N 条（与全局重复）」
② newGlobal 为空 → 旧键全部标 scope='global' 写入 global_worldinfo，旧桶清空
③ 两种情形都保证 RECORD_WORLDINFO 桶为空（旧桶自此不再被读）
```

判定「重复」不靠内容比对，只按上游口径（存在性裁决）——内容相同只是佐证，不是判据。

### 2.3 字段表（编辑面）

**世界书条目**（`records` payload / `char.worldInfo` 元素同构）：

| 字段 | 默认 | UI | 说明 |
|---|---|---|---|
| `comment` | `''` | 文本（名称；空则显示「未命名条目」） | 列表主文本 |
| `content` | `''` | 二级全屏正文编辑器 | 注入内容 |
| `keys` | `[]` | 逗号分隔文本（`，`与`,`都认） | `constant=true` 时置灰并提示「常驻条目无需关键词」 |
| `useRegex` | `false` | 开关 | 开启时 keys 按正则（上游强制 `i`） |
| `constant` | `false` | 开关 | 常驻 |
| `enabled` | `true` | 列表开关 | |
| `scope` | — | 三分段（全局 / 绑定当前角色） | **与 owner 联动**，改动 = 跨桶移动（§2.4） |
| `position` | `at_depth` | 单选（7 项，中文标签见附录 B） | |
| `order` | `0` | 整数输入 | 同组内升序 |
| `depth` | `4` | 整数输入，**仅 `position=at_depth` 时显示** | |
| `scanDepth` | `null`=继承 | 整数输入，空 = 继承全局 | |
| `probability` | `100` | 0-100 滑杆 + 数值 | |
| `useProbability` | `true` | 开关 | 关闭时概率控件置灰 |
| 其它未知键 | — | **不显示但原样保留** | §2.4 patch-merge |

**预设条目**：`name`（文本）/ `role`（三分段：系统 / User / AI）/ `content`（二级全屏正文编辑器）/ `enabled`（列表开关）。

**全局世界书设置**（`kv[KEY_WORLDINFO_SETTINGS]`）：`scanDepth`（0-20，默认 2）、`maxDepth`（0-50，默认 0=不限制）。

### 2.4 写入纪律（四条，测试逐条钉死）

1. **patch-merge，不重建对象**：读入原 `JsonObject` → 只覆盖 UI 暴露的键 → 写回。
   未暴露的键（含 `extensions` 摊平出来的字段、上游将来新增的字段）**永不丢失**。
2. **顺序即身份**：排序/新增/删除都表现为「重写整个数组」，`slot` 随之重排；
   不做「按名字匹配」这类靠不住的迁移。
3. **跨桶移动是一次事务**：改 `scope` 时从源数组移除、**追加到目标数组末尾**，
   两个数组在同一次 `db.withTransaction` 内写完；失败则两边都不动。
4. **时间戳**：写入时 `RecordEntity.updatedAt` 填真实毫秒（老数据 0，不参与任何判定，仅为可观测性）。

---

## 3 · 交互规格

### 3.1 世界书页（`WorldInfoPage` 真化）

```
PageHeader「世界书」  动作：[＋ 新建]
├ 全局设置卡（SettingCard）
│   SettingRow「全局扫描深度」  滑杆 0-20 + 数值     ← 真写 kv
│   SettingRow「最大扫描深度」  滑杆 0-50 + 数值（0 显示「不限制」）
├ SectionTitle「全局条目 · N」
│   EntryRow × N： [名称] [全局] [常驻?] [概率 80%?]
│                 支撑行「关键词：苹果 / 钟楼」或「常驻：不匹配关键词」
│                 [开关] [⋯ 菜单：编辑 / 上移 / 下移 / 删除]
├ SectionTitle「〈角色名〉绑定 · M」
│   同上（徽标「绑定」）
└ 空态：EmptyState「还没有世界书条目」+ 「新建条目」主按钮
```

- 新建默认 `scope='global'`（编辑器内可改），追加到对应数组末尾，**保存时才落盘**。
- 无角色（空库演示态）时：只显示全局组，并在页顶给一行说明「尚未选择角色，角色绑定条目在此不可见」。
- **不做**导入/导出按钮（P5 与角色卡工坊一起做）——不留假按钮（pro-rules：看起来能点却没反应 = 反模式）。

### 3.2 世界书编辑器（全屏 `ModalBottomSheet`，`skipPartiallyExpanded = true`）

唯一滚动容器（避免嵌套滚动，jetpack-compose 规则 #29）；字段分组卡片：

```
[名称]
── 触发 ────────────────
 关键词（逗号分隔）        [常驻 ⇄]   [正则 ⇄]
── 注入 ────────────────
 范围：全局 | 绑定当前角色（三分段）
 位置：7 项单选           （order）序号
 深度 depth（仅 at_depth 可见）
── 概率 ────────────────
 [按概率触发 ⇄]   概率 0-100（滑杆 + 数值）
── 正文 ────────────────
 摘要（前 6 行，灰）  [编辑正文 →]（二级全屏）
底部：[取消]  [保存]（保存=唯一写盘点）
```

- **正文走二级全屏编辑器**：理由见 §4.2（一条 3.7KB 的提示词在 240dp 的框里编辑是折磨）。
  二级编辑器：顶栏「〈名称〉正文」+ [完成]，单一滚动容器 = 文本框本身，底部字数统计。
- **未保存离开要确认**（`AlertDialog`：「放弃修改？」），草稿用 `rememberSaveable` 存，
  旋转/进程重建不丢（jetpack-compose 规则 #7）。
- **角色绑定条目编辑**：保存时读-改-写 `CharacterEntity.payload`（保留其余所有键），
  在同一个事务里 upsert 角色行。

### 3.3 预设页（`PresetsPage` 真化）

```
PageHeader「预设」  动作：[＋ 新建]
├（说明行）「提示词按此顺序注入：系统 / User / AI 三类各按本列表顺序」
├ SectionTitle「提示词预设 · N」
│   EntryRow × N： [名称] [系统|User|AI] [已停用?]
│                 支撑行「首行摘要（截断）」
│                 [开关] [⋯ 菜单：编辑 / 上移 / 下移 / 删除]
└ 空态：「还没有预设条目」
```

### 3.4 预设编辑器（全屏 Sheet，同 3.2 的骨架）

`[名称]` → `注入角色：系统 | User | AI`（三分段）→ `正文`（摘要 + 二级全屏）→ 底部 [取消][保存]。

### 3.5 聊天侧两个面板（只读，接真数据）

| 面板 | 改动 | 界面必须写清 |
|---|---|---|
| `WorldBookSheet` | 数据源从 `VanioCard.worldBook` 换成「全局 + 当前角色」真条目；条数取真值 | 页脚一行：「本版仅展示与管理；检索注入在 P5 接入」+ 顶部「管理」按钮 → 世界书页 |
| 新增 `PresetsSheet`（替换 `ChatPage.kt:707` 的提示气泡） | 列出 `enabled=true` 的预设（名称 + role 徽标 + 摘要），底部「管理」→ 预设页 | 同上 |

接线：`ChatPage` 增加 `onOpenWorldInfo` / `onOpenPresets` 两个回调，由 `ComposeActivity` 持有路由
（与现有 `onOpenSessions` 同法）。

### 3.6 状态清单（每条都要有实现与断言）

| # | 状态 | 期望 |
|---|---|---|
| 1 | 无条目 | EmptyState + 新建按钮，不显示空分组标题 |
| 2 | 只有全局 / 只有绑定 | 只渲染非空分组 |
| 3 | 超长正文（>3KB） | 列表只显示摘要；编辑走二级全屏；保存后列表摘要更新 |
| 4 | keys 为空且 `constant=false` | 行内提示「无关键词且非常驻：这条不会触发」（如实说明，不阻止保存） |
| 5 | 概率 < 100 | 行内徽标「概率 60%」 |
| 6 | 尚未消费（本批固有） | 两个聊天面板与两个页面各有一行如实说明（§1 非目标） |
| 7 | 删除 | 必须先 `AlertDialog` 确认（ux 库：Confirmation Dialogs / Severity High） |
| 8 | 保存失败（DB 异常） | 顶部 `Snackbar` 报错 + 编辑器不关闭（不许静默丢） |

---

## 4 · 视觉规格（单方向，本批豁免三方向）

### 4.1 方向：**「纸页清单 + 抽页编辑」**

**一句话**：列表是**目录**——一行一页纸，密度服从扫读；编辑时把这一页**抽出来铺满全屏**，
纸上的字就是唯一主角。

**理由（为什么是这个方向，而不是别的）**：
1. **内容驱动**：这两块数据都是「很多条长文本」，界面要回答的问题只有两个——「有哪几条、哪条开着」
   与「这一条写了什么」。目录 + 抽页正好对上这两个问题；卡片墙/双栏/仪表盘都在回答没人问的问题。
2. **一致性**：会话总览页（boards-v5 方向 B）已确立「分组行 + 粘性组头」的清单语言，
   本页是同一族（同样是「多条目、需要扫读、需要启停」），复用同一套行规格与组件，
   而不是给同一种问题发明第二套长相。
3. **反 slop**：不引入新色相、不引入渐变大底、不用 emoji、不编造统计数字；
   一个细节做到 120% 的地方是**正文编辑器**（字数统计 + 未保存确认 + 全屏呼吸感），其余保持克制。

**未走三方向的落档**：用户明示豁免（原话进 gate 文件）；按 huashu-design「唯一豁免」第 1 条处理。

### 4.2 form 五问（每屏开工前都要能答）

| 问 | 世界书页 / 预设页（列表） | 编辑器（全屏） |
|---|---|---|
| 叙事角色 | 目录页（过渡） | 工作页（hero 是正文本身） |
| 观众距离 | 30-40cm 手持，扫读 | 同距离，长时间阅读/输入 |
| 视觉温度 | 安静、工具感、纸面 | 更静，无装饰，专注 |
| 容量估算 | 19-40 行（预设 19 条实测）；分组最多 3 组 | 字段 8-12 项 + 正文 0-8KB；**正文必须独占一屏**（故二级） |
| 视觉母题 | 「条目即纸页」——行 = 目录项 | 同一条目被「抽出来」铺满 → 全屏编辑器就是这张纸 |

### 4.3 组件与 token 复用（不新造）

- 复用 `PageKit`：`PageHeader` / `SectionTitle` / `SettingCard` / `SettingRow` / `BadgeChip` / `LuzzySwitch` / `EmptyState`。
- `LuzzySwitch` 目前是**静态占位**（`PageKit.kt:182-202` 没有点击参数）→ 本批给它加
  `onCheckedChange: ((Boolean) -> Unit)?` 与涟漪反馈（**签名向后兼容**，静态调用点不受影响）。
- 行规格对齐 `SessionsPage`（22dp 徽标位 / 68dp 行高 / 单行预览）；
  每行**触控目标 ≥48dp**，图标按钮一律带 `contentDescription`，拖拽把手图标**删除**
  （本批不做拖拽，不留假把手）。
- 色：只用语义 token（`primary`/`tertiary`/`secondary`/`outline`/`error`）；徽标「全局」=primary、
  「绑定」=secondary、「常驻」=tertiary、「概率」=outline。**颜色不作为唯一指示**（徽标一律带文字）。
- 字号：正文 ≥14sp、次要文本 ≥12sp（**新页面不允许再出现 11/11.5sp**；现有 mock 里的 11.5sp 支撑文本一并上调）。

### 4.4 动效

- 令牌沿用 `Motion`（`LuzzyThemeEntry.kt:15-24`）：进入 200ms / 退出 140ms / `cubic-bezier(0.23,1,0.32,1)`。
- Sheet 进出用 M3 默认（`ModalBottomSheet` 自带），**不自造时长**；列表增删不做 `AnimatedVisibility`
  的花活，只保留系统涟漪——「一次 well-orchestrated 的动作胜过散落的微交互」。
- **禁** `scale(0)` 起步；无 `filter:blur` 类重活；不因动效引入布局位移。
- 尊重系统「动画关闭」（`ANIMATOR_DURATION_SCALE == 0` 时所有自绘时长归零）。

### 4.5 平台与 a11y 清单（对照 pro-rules 预交付检查表 + jetpack-compose 规则）

| 项 | 本批落点 |
|---|---|
| 触控目标 ≥48dp Android | 行内开关/菜单图标外扩热区；`IconButton` 默认 48dp |
| 按压反馈 80-150ms | 全部可点元素用 `Modifier.clickable`（自带涟漪）或 M3 组件 |
| 对比度 ≥4.5:1（亮暗各自测） | 只用 M3 语义 token；交付前亮/暗各截一张对照 |
| 分隔线/交互态在两种模式下都可见 | 沿用 `outlineVariant` + 既有卡描边 |
| 表单有标签、提示、错误就近 | 每个字段都有 label；必填校验就地提示；错误不只在顶部汇总 |
| 颜色不作为唯一指示 | 徽标带文字（全局/绑定/常驻/概率） |
| 拖拽必须有非拖拽替代 | **本批不做拖拽**，排序列为「上移/下移」菜单项（天然可达） |
| reduced motion + 大字号不破版 | 时长可归零；文本不写死高度（用 `IntrinsicSize`/`heightIn`） |
| 遮罩与模态可读性 | 沿用 `ModalBottomSheet` 默认 scrim，不自定义透明度 |
| 4/8dp 间距节奏 | 卡片内 12/16dp、卡间 8/10dp（沿用既有页面） |
| 单一滚动容器 | sheet 内只有一个滚动区；正文编辑独立全屏（规则 #29） |
| Lazy 列表带 `key` | `items(list, key = { it.slot })`（规则 #19） |
| 状态提升 / 单一真源 | 页面状态来自仓库 `Flow`，编辑草稿是 UI 局部态（规则 #3/#4） |

### 4.6 反 slop 对账（逐条对 huashu §6.2）

不新增色相 ✓ · 无紫渐变 ✓ · 无 emoji 图标 ✓ · 无「圆角卡 + 左彩条」组合 ✓ ·
无编造统计数字（不显示「共节省 X tokens」这类假 insight）✓ · 无装饰性图标堆砌 ✓ ·
不手画 SVG 插图 ✓ · 诚实 placeholder（空态写「还没有条目」而不是假条目）✓

---

## 5 · 实施步骤（W0-W7，每步独立可验证、独立提交）

| # | 步骤 | 产出 | 验收 |
|---|---|---|---|
| **W0** | **旧键裁决修正**（§2.2） | `LegacyMigrator`/`MigrationWriter` 按新口径处理 `rp_hub_worldinfo`；迁移报告加「跳过遗留世界书 N 条」；JVM 测试期望改为 `global 2 / legacy 0` | 单测全绿；夹具迁移后再跑一次**幂等**（同输入两遍 → 逐字段相等）；模拟器重跑迁移，报告数字与单测一致 |
| **W1** | **仓库层**：`WorldBookRepository` / `PresetRepository`（读 + 写；patch-merge；跨桶移动；整表写；updatedAt） | 两个仓库 + 纯函数单测 | 单测覆盖：未知键保留 / 默认值补全 / keys 拆分（`,` 与 `，`）/ 跨桶移动（两桶条数 + 目标末尾）/ 概率与位置原样保留 / 空数组不报错 |
| **W2** | **世界书页真化** | `WorldInfoPage` 接真数据（含全局设置滑杆真写） | 仪器化：列表条数=库内条数；开关写库；删除需确认且写库；空态出现 |
| **W3** | **世界书编辑器** | 全屏 sheet + 二级正文编辑器 | 仪器化：改字段保存后**重开仍在**；`constant=true` 时 keys 置灰；`at_depth` 才显示 depth；未保存离开弹确认 |
| **W4** | **预设页 + 编辑器** | 同上，字段换成 name/role/content | 仪器化：新建追加到末尾；排序改变数组序并落盘；role 三分段写回 |
| **W5** | **聊天侧接线** | `WorldBookSheet` 接真数据；新增 `PresetsSheet`；移除 `ChatPage.kt:707` 假提示；`onOpenWorldInfo`/`onOpenPresets` 路由 | 仪器化：面板条数=库内条数；「管理」跳转到位（路由断言） |
| **W6** | **回归 + 目测** | `checkChat` 全绿；模拟器亮/暗截图；DESIGN-compose 新增 §23 回填实现状态 | 截图存 `docs/design/verify-p4-*.png`；pro-rules 检查表逐项打勾 |
| **W7** | **文档** | WORKLOG 会话记录 + 节点更新；master plan 勾掉 4.2 | — |

**门禁稳定性（顺带修）**：`SessionsPageTest.tappingARowRemembersThatSession` 在**模拟器冷启动后的首跑**
出现过一次红，二次跑 3/3 绿（2026-09-13 实测）。按 open-design「概率性判据不用」纪律，
W6 要把它定性（疑为 `waitUntil` 超时对冷启动过紧）并消除——**概率性门禁比没有门禁更糟**。

---

## 6 · 验收标准

### 6.1 自动化

- `ANDROID_SERIAL=emulator-5554 ./gradlew checkChat` 全绿（含 W1 单测与 W2-W5 仪器化）。
- W1 的 patch-merge 与跨桶移动有**独立单测**（不依赖 UI）。
- W2-W5 每条关键行为**一条断言**：写盘后用 `runBlocking { store.records(...) }` 复读比对
  （沿用 P4-B 已确立的 `compose.waitUntil { runBlocking { … } }` 手法，勿用 `Thread.sleep`）。

### 6.2 人工（模拟器，亮/暗各一遍）

1. 世界书：新建 → 填名称/关键词/正文 → 保存 → 杀进程重启 → 条目仍在且内容一致；
2. 把一条全局条目改成「绑定当前角色」→ 重启 → 它出现在绑定组、全局组少一条；
3. 预设置：新增一条 role=User → 上移两次 → 重启 → 顺序保持；
4. 删除任一条目 → 有确认框 → 取消不删、确认才删；
5. 大字号（系统字体最大）下不破版；开启「关闭动画」后无残留动画。

### 6.3 证据留档

`docs/design/verify-p4-worldinfo-list.png`、`-worldinfo-editor.png`、`-presets-list.png`、
`-presets-editor.png`（各亮/暗两版）。

---

## 7 · 风险与决策记录

### 7.1 为什么**不**复制上游的「启动强制覆盖内置预设」

| 理由 | 说明 |
|---|---|
| 动机不成立 | 上游那套是为了让它自己的内置文本**随应用版本更新**；我们的内置文本源是上游文件 `built-in-content.js`，受硬性规定 2 保护、且 P6 移除 WebView 路径后运行时不再存在 |
| 会让「编辑」变成假的 | 用户改完 `破限`，下次启动被静默还原 → 不报错、不提示、无从得知。这正是本项目最忌讳的一类缺陷（契约写了、行为不符） |
| 数据已经是正确的 | 迁移来的行**就是**用户的当前状态（含它历史上被上游同步过的内置文本），不需要我们再插手 |

**如实登记一个缺口**：全新安装（无迁移数据）时预设为空 → 将来接入消费后，请求里不会有破限等系统提示词。
**触发条件**：接入预设消费（P5）之前，必须先定「内置预设种子从哪来」——两个候选：
① 迁移期把内置文本快照进新库（只对迁移用户有效）；② 自带一份"最小破限"种子文本（需过设计/合规审视，且**不得**触碰 nsfw_rules）。

### 7.2 旧键修正要重跑迁移

`records(kind="worldinfo")` 会变空。模拟器可幂等重跑；真机尚未迁移（§20.4 未执行）→ **时机上无成本**。
若真机已迁移过再改口径，需要一次「清理遗留桶」的一次性动作——本批不给真机做特殊处理，因为还来得及。

### 7.3 「编辑 ≠ 生效」是**边界**，不是漏洞

本批结束时：数据可编辑、可持久化、聊天面板可见；**请求组装仍是 P5**。
上游的完整消费链（system 拼装顺序、at_depth 绝对锚、概率触发的每轮一次性掷骰）已梳理成**附录 A**，
P5 直接照它实现，不必再侦察一遍。

### 7.4 其它

- **不引入新依赖**：无拖拽库、无富文本库（正文是纯文本，上游也是）。
- **不改动已发布的 WebView 侧**：本批全在 Compose 代码与 P4 迁移代码内，上游文件零触碰（硬性规定 2 不触发）。

---

## 8 · 明确不做

导入/导出（P5 与角色卡工坊一起，走 SAF）· 长按拖拽排序（后续，需自绘 + 手势冲突排查；
本批已有可达的替代） · 条目复制（上游也没有）· 正则编辑页（同族但独立） · 「生效」（§7.3） ·
字号设置（需设计门） · 角色图解码与 Markdown 补齐（P5 其它项）。

---

## 9 · 待用户拍板

**阻塞项：0 项**。本计划按 master plan 的既有边界可直接执行。两项**告知**（若与你的预期不同，说一声即可改）：

1. **生效放到 P5**：本批只做管理面（与 `PLAN-v3.0-compose.md:100/174` 一致）。
   若你希望本批**顺带**把世界书检索注入做掉（让编辑立刻影响回复），我把附录 A 提上来当 W8，工期会明显变长。
2. **导入/导出放到 P5**：本批页头只有「新建」，不留假的导出按钮。

---

## 附录 A · 上游消费链（P5 直接引用，勿再侦察）

**预设**：`enabledPresets = presets.filter(enabled && content.trim())` → 分流
（`文风（抗八股）` / `COT` / 其余 system / user|assistant）→ system 拼装顺序
`破限 → 世界书 system_top → global_note → [System Presets] 其余 → 用户信息 → 工具提示 → UI 模板 → COT 末尾`；
user/assistant 预设作为**独立消息**紧随首条 system（`app.js:4534-4628`）。
**世界书**：匹配（子串/正则强制 i）→ 常驻直入 → 概率每轮一次掷骰 → resolve 排序（constant 优先、order 降序分组、组内升序）
→ 按 position 分 7 组注入（`at_depth` 独立 user 消息按 depth 锚定；`user_top/assistant_top` 前插末条正文头部；
`before_char/after_char` 进角色前置 user 消息）（`data-services.js:772-1065`）。

## 附录 B · position 中文标签（实现时照抄）

| 值 | 界面标签 |
|---|---|
| `system_top` | 系统提示词开头 |
| `global_note` | 全局注释（system） |
| `before_char` | 角色描述之前 |
| `after_char` | 角色描述之后 |
| `at_depth` | 按深度插入（默认） |
| `user_top` | 用户消息上方 |
| `assistant_top` | AI 消息上方 |

**keys 拆分**：`,` 与 `，` 都作分隔符，两侧空白裁掉，空项丢弃（上游 `core-utils.js:578-580`）。
**概率语义**：`useProbability=false` 或 `probability>=100` 必过；否则每轮每条只掷一次。
