# DESIGN-migration · v3.0 P4 数据层与迁移（WebView → Compose）

> 本文件是 **P4（数据层与迁移）的设计真源**：迁移通道怎么走、旧数据长什么样、哪些坑必须处理、
> 验收怎么判。UI 视觉设计见 [`DESIGN-compose.md`](DESIGN-compose.md)（本文件不涉及视觉）。
>
> **硬性规定 9（设计 SKILL 门）不适用本链路**：规定原文把「**数据迁移**」与修 bug、纯文字改动、
> 构建配置并列为**明确豁免**的机械操作。本文件不含任何视觉/动效/布局产出，故不触发 4 项 SKILL 阅读门。
> 一旦本链路出现 UI 界面（如迁移进度/报告页），那一步**单独**按硬性规定 9 走设计流程。

---

## 0. 一句话

v3.0 的 Compose 应用要读**老用户 WebView 版留在 IndexedDB 里的全部数据**；迁移做不完，
覆盖安装就等于清空用户多年的角色卡与会话——这是全项目**最高风险**的一环，
因此先做原型、用**真实数据**验证，而不是先写纸面设计。

---

## 1. 目标与验收

| # | 目标 | 验收判据 |
|---|---|---|
| G1 | 老数据**一条不丢**地落到新存储 | 迁移后 角色数 / 会话(分支)数 / 消息数 / 记忆数（向量+经典）/ 世界书条目数 / 预设数 / 正则数 / 用量记录数 **与源一致** |
| G2 | **幂等** | 同一份数据连迁两次，第二次结果与第一次逐字段一致（不重复、不翻倍） |
| G3 | 坏数据**只跳过不中断** | 畸形记录被记录进迁移报告并跳过，其余照常迁完 |
| G4 | 中断可续 | 写「迁移完成」标记；未完成则下次启动重跑（幂等前提下安全） |
| G5 | 迁移前**不破坏源** | 迁移只读旧库；旧库原样保留（用户可回退到 v2.x 版本） |

---

## 2. 关键分叉（2.2 探针）：**已定 GO**

### 2.1 问题

旧数据活在 **WebView 的 IndexedDB**（`/data/data/<pkg>/app_webview/Default/IndexedDB/...`，
LevelDB 形态）。Kotlin 侧直接解析不现实 → **迁移必须在一段跑在 WebView 里的 JS 中完成**。

于是通道有两条路：

- **(a) 隐藏 WebView 打开 `files/ext/luzzy-migrate.html`**（轻量页，不启动 Vue）——
  前提假设：**不同 `file://` 文件共享同一 origin 的 IndexedDB**。
- **(b) 隐藏 WebView 打开 `files/rphub/index.html` 后用 `evaluateJavascript` 注入**——
  必然同 origin（写数据的页面就是它），但要**启动整个上游应用**（慢、副作用多）。
  (b) 是**保底路径，不需要验证**。

### 2.2 实测结论（2026-09-12，模拟器 `emulator-5554`，release 包 `com.luzzymeow.luzzyrp`）

把探针页推入设备 `files/ext/luzzy-origin-probe.html`，用 CDP 把 WebView 从
`files/rphub/index.html` **导航到**该页，读到的原始结果：

```json
{
  "stage": "done",
  "href": "file:///data/user/0/com.luzzymeow.luzzyrp/files/ext/luzzy-origin-probe.html",
  "dbNames": ["RPHubDB@v1", "SillyTavernDB@v1"],
  "openOk": true,
  "keyCount": 30,
  "sampleKeys": ["rp_hub_active_profile_id", "rp_hub_active_tools",
                 "rp_hub_branches_cf39170a-…", "rp_hub_characters", "rp_hub_chat_…"],
  "charCount": 3,
  "charNames": ["Vanio", "谢昭", "夏梧"],
  "putRequestOk": true,
  "readWrote": "ok"
}
```

即：**读得到**（30 个键、3 张角色卡按名字读回）、**写得进**（写入的探针键事后能在主页面读到）。
→ **假设成立，(a) 路径可用**。

**成因（不是巧合，是配置决定的）**：`WebViewSetup.configure` 里
`setAllowFileAccessFromFileURLs(true)` + `setAllowUniversalAccessFromFileURLs(true)`
使 `file://` 页面互相视为同源。
**推论（也是纪律）**：v3.0 的迁移 WebView **必须复用同一套 `WebViewSetup.configure`**；
哪一天有人收紧这两个开关，迁移通道会**静默失效**（表现为「读不到数据」而不是报错）——
所以 §7 的门禁里必须有一条断言「迁移 WebView 能看到非空键集」。

### 2.3 选型

**走 (a)**：隐藏 WebView 加载 `files/ext/luzzy-migrate.html`。
理由：不启动 Vue（省 2–3 秒与大量副作用）、页面小到可审计、迁移逻辑与业务前端彻底隔离。
(b) 作为已实现的降级：若 (a) 的健康检查失败（键集为空），改加载 `rphub/index.html` 再注入。

---

## 3. 旧数据的真实形态（**以夹具实测为准**，不是推测）

### 3.1 两个数据库 × 两套前缀

| 库 | 前缀 | 说明 |
|---|---|---|
| `RPHubDB`（新库） | `rp_hub_` | 现行写入位置 |
| `SillyTavernDB`（旧库） | `silly_tavern_` | 更早版本；`indexedDB.databases()` 里存在才打开 |
| `RPHubDB`（兼容读） | `silly_tavern_` | **旧前缀也可能写在新库里**（旧版升上来） |

读取语义（上游 `data-services.js`）：**新键优先，缺失才回落旧键并回写新键**。
对象仓库固定为单库单仓：库名见上表，仓库名恒为 `'store'`，键为字符串。

**迁移器对这一条做了加强（实现时的决定，理由在下面）**：上游的「新键优先」是**整键替换**——
新库里只要存在 `characters`，旧库（以及同库里旧前缀）的那一份就整体不看。
对数组型记录那样做**会丢掉整张角色卡**，而角色卡带 `uuid`、身份明确，合并是安全的。所以定为：

- **带稳定身份的记录**（`characters` / `user_profiles`，身份字段 `uuid`）→ **按身份逐条合并**，
  同 uuid 采用**新库版本**，旧库独有的照常保留；
- **其余键** → 仍走整键优先（数组元素没有稳定身份，无法安全合并），
  并在报告里留下「该键被更高优先级来源遮蔽」的可见记录，不做静默覆盖。

为什么这条加强是必要的：夹具里同一份数据同时存在三种来源
（`rp_hub_characters` 3 张 / 主库 `silly_tavern_characters` 1 张 / 旧库 `silly_tavern_characters` 1 张），
按整键优先只能留下 3 张、丢 2 张——而它们都是**用户的真实角色卡**。
`LegacyMigratorTest` 里 `坑10 旧库独有的角色没被丢掉` 就是钉这一条的。

### 3.2 键命名空间（夹具实测 29 + 3 个键）

| 键 | 类型 | 备注 |
|---|---|---|
| `rp_hub_characters` | array | 角色卡；`avatar` 是 **base64 dataURL**；内含 `worldInfo`/`regexScripts`/`uiTemplates`/`recentGenerationTimes` |
| `rp_hub_chat_<charUuid>` | array | 主线会话消息 |
| `rp_hub_chat_<charUuid>__branch__<branchId>` | array | 分支会话消息 |
| `rp_hub_branches_<charUuid>` | object | `{version, activeBranchId, branches[]}` |
| `rp_hub_memories_<scope>` | array | 向量记忆（`scope` 同上规则） |
| `rp_hub_classic_memories_<scope>` | array | 经典总结记忆 |
| `rp_hub_memory_settings` | object | 含 **`emptyTurns`：键为 `<scope>:<mode>`** ← 作用域键内嵌在全局记录里 |
| `rp_hub_worldinfo` / `rp_hub_global_worldinfo` | array | 角色域 / 全局域世界书 |
| `rp_hub_regex` / `rp_hub_global_regex` | array | 角色域 / 全局域正则 |
| `rp_hub_presets` | array | 含 18 条内置 + 用户新增 |
| `rp_hub_settings` | object | 44 个字段；**内含 `apiKey` / `apiProviderKeys`（敏感）** |
| `rp_hub_user` / `rp_hub_user_profiles` / `rp_hub_active_profile_id` | object/array/string | `active_profile_id` 是**人设 uuid** |
| `rp_hub_last_active_char` | **number** | 是 `characters[]` 的**下标**，不是 uuid |
| `rp_hub_token_usage_history` | array | 18 字段用量记录 |
| `rp_hub_active_tools` / `rp_hub_worldinfo_settings` / `rp_hub_global_ui_templates` | array/object | 工具开关、世界书扫描深度、全局 UI 模板 |

**作用域拼接规则**（`buildStoryBranchScopeId`）：
`branchId === 'main'` → **裸 `<charUuid>`**；否则 `<charUuid>__branch__<branchId>`（分隔符 `__branch__`）。

### 3.3 消息形状（实测）

```
0 assistant  ["role","name","content","isSelf","shouldAnimate"]
1 user       ["role","name","content","shouldAnimate","isSelf","avatar","imageAttachments","id"]
2 assistant  ["role","name","content","reasoning","id","shouldAnimate","isCotOpen",
              "isReasoningOpen","isReasoningUserToggled","isReasoningAutoCollapsed","isSelf","isSummaryOpen"]
```

- **没有任何时间戳字段** → 顺序只能靠数组下标（迁移不得打乱、不得重排）。
- 带 `id` 的消息是走过 `ensureConversationMessageIds()` 的；`first_mes` 那条通常**没有 `id`**。
- `imageAttachments` 是 `[{dataUrl, description}]`——**dataUrl 就是 base64**（图片附件坑）。
- `shouldAnimate` / `skipReveal` / `isCotOpen` / `isReasoningOpen` / `isReasoningUserToggled` /
  `isReasoningAutoCollapsed` / `isSummaryOpen` 都是**界面临时态**，迁移应丢弃（见 §5 坑 5）。
- 用户消息有 `avatar`（用户人设头像快照），助手消息没有。

### 3.4 记忆形状（实测）

经典记忆：`id, timestamp, turn, summary, enabled, classicMemory, summaryModel, sourceUserIds,
sourceAssistantIds, sourceUserText, sourceAssistantText`

向量记忆：`id, timestamp, turn, summary, enabled, vectorMemory, chunkMode, vectorChunkId,
sourceRole, sourceName, paragraph, paragraphIndex, paragraphEndIndex, sequence,
contentFingerprint, sourceUserIds, sourceAssistantIds, embeddingModel, embeddingProvider,
sourceText, embeddingQ, embeddingScale, embeddingDims, embeddingEncoding`

其中向量组是**量化后**的：`embeddingQ` 是 int8 数组的 base64（本例 3072 维 → 4096 字符），
`embeddingScale = maxAbs/127`，`embeddingEncoding = 'int8:maxabs:v1'`。
**迁移必须原样搬运这 4 个字段**（重算需要重新调用嵌入接口，等于让用户为迁移再付一次钱）。

---

## 4. 测试夹具：真实旧数据

**文件**：`app/src/test/resources/legacy/webview-db-fixture.json`（139 KB，测试资源）

### 4.1 怎么来的（**不是手写样本**）

模拟器装 release 包 → CDP 驱动**前端自己的函数**造数据
（生成脚本与复现说明随仓库入库：`tools/mig-fixture/`）：

| 步骤 | 用的真实入口 |
|---|---|
| 用户资料 / 供应商 / 模型槽位 | `user` 与 `settings` 的 reactive 赋值（触发 app.js 的深度 watcher → `saveData()`）+ `editUserApiProvider` / `saveProviderEditor` / `updateProviderKey` |
| 角色 | `createNewCharacter()` → 填 `editingCharacter.data` → `saveCharacter()`；头像走 `handleAvatarUpload`（真实 canvas 生成的 PNG → 真实 `compressImage`） |
| 会话（2 轮/分支 1 轮） | `sendMessage()` **真实调用 DeepSeek**（SSE 流），消息由真实收尾逻辑落盘 |
| 剧情分支 | `createStoryBranch(2)` + `selectStoryBranchNode` / `saveStoryBranchName` |
| 世界书 / 正则 / 预设 | `createWorldInfo`/`saveWorldInfo`、`createRegex`/`saveRegex`、`createPreset`/`savePreset` |
| 经典记忆 | `startBatchMemoryExtraction()`（mode=classic，真实 LLM 总结） |
| 向量记忆 | `startBatchMemoryExtraction()`（mode=vector，真实嵌入 3072 维 → 应用自身 `int8:maxabs:v1` 量化落盘） |
| 人设 / 多角色 | `createNewProfile()`、再建两张角色卡、`selectCharacter(1)` |

> 因此夹具里的**字段名、字段组合、缺省值都是真前端写出来的**，不是我照着代码猜的。
> 这一点很重要：首版设计里「手写样本」的冲动会直接漏掉 `isSummaryOpen`、`emptyTurns` 这类字段。

### 4.2 夹具内容（源计数，迁移验收就照这张表比）

| 项 | 值 |
|---|---|
| 角色 | 3（Vanio / 谢昭 / 夏梧），Vanio 带真实 7,871 字符 base64 头像 |
| 会话 | Vanio 主线 5 条 + Vanio 分支 5 条；谢昭 1 条（仅 first_mes） |
| 剧情分支 | Vanio：`main` + 1 条（`forkFloor=3`） |
| 向量记忆 | 主线 2 + 分支 2（3072 维、`embeddingQ` 4096 字符） |
| 经典记忆 | 主线 2 + 分支 2 |
| 世界书 | 角色域 2 + 全域 2 |
| 正则 | 角色域 1 + 全域 1 |
| 预设 | 19 |
| 用量记录 | 9 |
| 人设 | 2（`active_profile_id` 指向第二个） |
| `last_active_char` | **1**（非零下标——故意留的判据） |
| legacy 键 | 主库 4 + 旧库 3（见下） |

### 4.3 两处**明确标注为构造**的内容（如实登记，不混同）

1. **legacy 记录**：`silly_tavern_*` 前缀与独立 `SillyTavernDB` 的记录**无法由现行代码再生**
   （旧版本才会写）。这部分是**手工构造**的，形状照现行字段、只保留旧版存在的字段。
   用途：验证「两库合并 + 新库优先 + 旧前缀回落」。
2. **`provenance.redaction` 里的密钥**：夹具来自真机态页面，`rp_hub_settings` 里带着开发用
   API Key。**已脱敏**（`<REDACTED:name:lenN>` 占位符，**保留字段与长度**，迁移器照旧能断言
   字段被搬运）。脱敏清单记在夹具的 `provenance.redaction.fields`；全树兜底扫描残留 0 条。

> **纪律**：夹具进仓库前**必须**跑脱敏（`tools/mig-fixture/scrub.mjs`），且提交前人工复核
> `provenance.redaction.suspiciousRemaining` 为空。仓库是公开的。

---

## 5. 十二条坑 × 处置（全部来自上游 `data-services.js` / `app.js` 的实现细节）

| # | 坑 | 处置 | 单测 |
|---|---|---|---|
| 1 | 分支作用域拼接：**main = 裸 uuid** | 统一用「解析 scope」函数：带 `__branch__` 才拆，不带即 main | 有 |
| 2 | 旧数字索引键 `chat_<n>`（courtesy v1.x） | 落到 `chat_<uuid>` 时**并入**对应角色，冲突不覆盖新库值 | 有 |
| 3 | 双库 + 同库里新旧前缀并存 | **带身份字段的数组按身份合并**（同 uuid 取新库版本，旧库独有照常保留）；其余键整键优先并留「被遮蔽」记录（理由见 §3.1） | 有 |
| 4 | 消息**没有时间戳** | 以数组序为准；新模型给 `sortIndex`，**不允许**按任何时间字段重排 | 有 |
| 5 | 瞬态字段白名单 | 迁移**丢弃**：`shouldAnimate` `skipReveal` `isCotOpen` `isReasoningOpen` `isReasoningUserToggled` `isReasoningAutoCollapsed` `isSummaryOpen` | 有 |
| 6 | **base64 附件**（角色/人设头像、消息 `imageAttachments[].dataUrl`） | 超阈值解码成文件存路径，DB 内只留路径；**保留旧字段**供兼容读 | 有 |
| 7 | 向量记忆 `embeddingQ/Scale/Dims/Encoding` | **原样搬运**，绝不重算 | 有 |
| 8 | 内嵌作用域键：`memory_settings.emptyTurns` 的键是 `<scope>:<mode>` | 键**重写**为新作用域标识，语义不变 | 有 |
| 9 | `last_active_char` 是**下标**不是 uuid | 迁移时按下标取 uuid 再存 uuid；下标越界则丢弃该项（只跳过） | 有 |
| 10 | 孤儿键合并 | `chat_*` / `memories_*` / `branches_*` 找不到宿主角色 → 归并到报告并**保留**为孤立角色（不静默删） | 有 |
| 11 | `cloneForStorage` 的 `Date → ISO 字符串` | 迁移器**只接受已序列化形态**（ISO 字符串/数字），遇 Date 实例按形态非法跳过并报告 | 有 |
| 12 | 迁移前必须 **flush 写入队列** | `chat` 有 300/1500ms 防抖、其余 1000ms。迁移 WebView 读到的是**已落盘**内容，故导出前先让主页面退出/停止写入；若主页面仍在运行，导出前先 `flushChatInput` 等价操作由桥方法保证 | 有（集成） |

---

## 6. 迁移通道协议（2.3）

### 6.1 页面：`app/src/main/assets/ext/luzzy-migrate.html`（**已实现**）

- **纯 JS，不启动 Vue、不加载上游脚本**；只做一件事：遍历两库 → 序列化 → 分块回传。
  这样迁移逻辑与业务前端彻底隔离，上游前端重构也不会影响迁移。
- **只读**：不写、不删任何旧记录（G5 → 用户可随时回退到 v2.x 版本）。
- **不做语义解释**：字段怎么理解是 Kotlin 迁移器的事。页面只负责「搬运 + 分块 + 校验」。
- **健康检查放在最前**：两库都读不到键就**明确失败**，而不是导出一个空文件。
  这一步是 §2.2 那条纪律的落地——`setAllowFileAccessFromFileURLs` 一旦被收紧，
  症状是「读不到数据但不报错」，必须有断言把它变成可见的失败。
- 序列化后整串按字符切片，每片 ≤ `CHUNK_CHARS = 180_000`（Binder 事务上限 1 MB；
  中文按 UTF-8 占 3 字节 → 最坏约 540 KB，留足余量。`MigrationInboxTest` 有一条断言锁住这个量级）。
- **测试钩子**：URL 可带 `?chunk=8000` 覆盖分块大小。小样本导出天然只有一块，
  而「块顺序校验 + 拼接完整性」恰恰只有多块才走到——没有这个开关就只能靠真实用户的
  MB 级数据去撞运气。

### 6.2 桥方法（`LuzzyBridge.kt` + `luzzy-bridge.js` 封装，硬性规定 3/§5.4）

| 方法 | 作用 |
|---|---|
| `migrateStart(sessionId)` | 清空上次残留，返回会话 id |
| `migrateChunk(seq, payload)` | 追加一块；**序号必须连续**，否则返回 false（缺块拼出的 JSON 可能恰好能解析 → 静默少数据，宁可失败） |
| `migrateDone(summaryJson)` | 收尾：校验非空 → 算 sha256 → 写 manifest → 返回报告（失败返回空串） |
| `migrateError(message)` | 页面侧异常出口（不吞错）+ 清掉半成品 |

### 6.3 落盘位置

`filesDir/migration/incoming/`：`legacy-export.json`（拼接结果，迁移器的输入）
+ `manifest.json`（块数 / 字符数 / sha256 / `completed` 标记）。
**迁移成功后**只删 `incoming/`；旧 IndexedDB **保留**（G5）。

### 6.4 设备端实测（2026-09-12，模拟器 `emulator-5554`，release 包）

把 WebView 从 `rphub/index.html` 导航到 `files/ext/luzzy-migrate.html`（真实流程、真实桥），
原生侧落到文件后拉回本机比对：

| 项 | 结果 |
|---|---|
| 迁移页自述 | `完成`：字符 82,992 · 块 1；`migrateDone` 返回 sha256 |
| manifest | `chunks:1, chars:82992, mainKeys:29, legacyKeys:3, completed:true, elapsedMs:45` |
| 与测试夹具比对 | **31/32 键逐字节相同**；唯一不同的 `rp_hub_settings` 差异**只有**两个被脱敏的密钥字段（长度保留） |
| 多块路径（`?chunk=8000`） | 11 块；拼接结果与单块版**内容完全一致**（仅 `capturedAt` 时间戳不同） |

> 意义：夹具（`dump.js` 产出）与**生产导出器**（`luzzy-migrate.html`）是两条独立实现，
> 它们对同一份真实数据给出相同结果 → 单测用的夹具确实代表生产输入，不是自说自话。

---

## 7. 迁移器（2.4，Kotlin，**已实现**）

### 7.1 结构（与最初设想略有出入，以实现为准）

```
ext/luzzy-migrate.html (JS)  →  MigrationInbox (Kotlin, 分块拼装 + sha256)
                                        ↓
                                  LegacyDb.parse（线格式解析，纯函数）
                                        ↓
                                  LegacyIndex（多来源合并，纯函数）
                                        ↓
                                  LegacyMigrator（纯 Kotlin，无 Android 依赖）
                                        ↓
                                  MigratedData（存储无关的迁移产物）+ 跳过/说明清单
                                        ↓
                                  目标存储（P4-B 定；见 §8）
```

**关键**：`LegacyDb → MigratedData` 这一段是**纯函数**，夹具 JSON 直接喂进去就能断言
（`LegacyMigratorTest`，22 例），**不需要模拟器**。

`MigratedData` 里强类型的只有新界面马上要用的部分（角色标识 / 分支 / 消息 / 作用域）；
记忆 / 世界书 / 预设 / 正则 / 用量等**整条原样搬运**（字段名与旧结构逐字一致），
因为我方消费方的字段需求要等 P4-B/P4-C 才定，现在定型等于先猜一遍再改一遍。

（G1 的计数、G2 的幂等），**不需要模拟器**。

### 7.2 幂等（G2）

### 7.2 幂等（G2）

实现口径比「同键覆盖」更严：**同一份输入跑两次，`MigratedData` 逐字段相等**。
为此 `MigratedData` 里不出现时间戳、随机 id 或不确定顺序：

- 角色缺 `uuid` 时用**内容哈希**补（`UUID.nameUUIDFromBytes`），而不是随机 UUID
  —— 随机会让第二次迁移多出一张角色卡；
- 合并/遍历顺序全部确定（新库在前 + 数组内保持原序）；
- `ExtractedAsset.equals` 按**字节内容**比（默认的 `ByteArray` 比较是引用比较，会让幂等判据假红）。

「迁移完成标记」在存储层（P4-B）落，启动时若标记存在则不再迁移。

### 7.3 报告与「不丢」口径（G3）

`MigratedData` 带三样可读的东西：
`skipped: [{key, reason}]`（坏记录只跳过不中断）、
`notes: [...]`（不丢数据但需要人看一眼的情况：从旧库补回、补了 uuid、用了数字索引回落、
孤立作用域补了占位角色…）、以及 `assets: [ExtractedAsset]`（待落文件的 base64）。

`counts()` 给出各表条数，**就是 G1 的比对面**。UI 只提示不阻断（用户已拍板：**不设回退**）。

---

## 8. P4-B 存储：**已定为 Room**（spike 结论 + 库设计）

### 8.1 spike 结论：**原判据的前提被证伪**

计划里的判据是「先试 Room + KSP + KGP：15 分钟内能构建过 → 走 Room；否则回落 JSON 文件存储」，
并附带一个悲观假设：**我们没有 KGP（AGP 9 内置 Kotlin），所以 KSP/Room 未必装得上**。

2026-09-12 实跑结果：

| 步骤 | 结果 |
|---|---|
| 版本 | KSP `2.3.9`、Room `2.8.5` |
| `:app:kspDebugKotlin` | **执行成功**（注解处理真的跑了，不是跳过） |
| `:app:compileDebugKotlin` | BUILD SUCCESSFUL |
| 运行时（`app` 装到模拟器跑仪器化测试） | **9 例全绿**：开库、事务、索引查询、关库重开 |

→ **「装不上」不成立。** 于是回落 JSON 的理由（构建约束）**不适用**，
决策必须自己站得住，不能借「反正装不上」下台。

### 8.2 决策与理由（**按写模式，不按偏好**）

**走 Room。** 唯一决定性理由是**写模式**：

- **消息是唯一会被高频增量写的数据**：流式生成时一条回复在不断变长，连续对话持续追加。
  若把一整段会话存成一列 JSON，每次落盘都要**重写整段历史**（重度用户可达数 MB）——
  那是 IO 风暴，而且随会话增长而恶化。
  一消息一行 + `scopeId` 索引 → 追加/更新只碰一行。**这就是 Room 挣到的那份工钱。**
- 其余记录（世界书 / 正则 / 预设 / 用量 / 人设）在旧实现里本来就是整表读写、
  元素也没有稳定 id —— 两种方案都够用，不构成选型依据。

**明确记录的是：这一条决策的前提是「消息走行级存储」**。
哪一天把消息也塞进 JSON 列，Room 就只剩下「多一个注解处理器」的成本，
那时应当重新评估（而不是默默沿用）。

### 8.3 库设计（`data/store/`）

| 表 | 主键 | 说明 |
|---|---|---|
| `characters` | `uuid` | 旧数据里就有稳定身份；`avatar` 抽成文件后 `avatarPath` 记路径 |
| `branches` | `(characterUuid, branchId)` | 主线恒为 `main`（迁移器已保证） |
| `branch_meta` | `characterUuid` | 旧的 `version` / `activeBranchId` |
| `messages` | **`(scopeId, sortIndex)`** + `scopeId` 索引 | 主键用**位置**而非消息 `id`：`first_mes` 那条通常没有 id，而顺序才是旧数据的真实身份 |
| `memories` | `(scopeId, kind, id)` | 向量四字段原样躺在 payload 里 |
| `records` | `(kind, owner, slot)` | 整表读写集合统一落点；`slot` 是元素在旧数组里的下标（= 身份） |
| `kv` | `key` | 值一律 JSON 文本（字符串带引号），免得「这个键存的是啥」靠记忆 |
| `attachments` | `path` | 抽出来的二进制附件索引（相对 `filesDir`） |

**`payload` 列的纪律**：只把**确实要查询/展示**的字段提成列，其余字段的整段 JSON
按**旧结构键名逐字**背着走。这是「迁移零丢失」的前提，也让日后要用新字段**不需要改表**
（扫 payload 即可）。即 rikkahub 的「一行 + JSON 列」模型。

### 8.4 导入与幂等（`MigrationWriter`）

- 语义是「**这次导入就是当前状态**」：一个事务里**先清内容表、再全量写入** →
  同一份数据导入两次逐行一致（`kv` 不参与清表，所以设置与迁移标记不会被带走）。
- **文件先于事务**：附件（base64 头像/图片）先落盘（同路径同内容不重写），再进事务。
  反过来会出现「事务回滚了但文件已在」或「事务提交了但文件没写成功」两种坏状态。
- 迁移标记由**上层在成功之后**写（本类只管放数据），避免「导入失败但标记已写」。

### 8.5 未决

- `presets` / `worldinfo` 元素没有稳定 id，`slot` 用下标。**在支持用户增删排序之前必须重做**
  （插入一条会让后面所有 slot 位移 → 变更被当成多条改动）。当前阶段（只读展示）无影响。
- 迁移入口接线（谁在什么时候启动迁移 WebView、进度与报告怎么呈现）尚未做，见 §9。

### 8.6 一条工具链坑（会花掉一小时的那种）

**仪器化测试的方法名不能带空格**（`fun \`中文 用例名\`()` 这种反引号风格在 JVM 单测里没问题，
但 androidTest 要过 D8 脱糖）：Kotlin 会为方法里的 lambda 生成
`LuzzyStoreTest$迁移导入落到 Room，行数与迁移结果一致$1` 这样的内部类名，
而 **DEX 版本 040 之前不允许类名含空格** → `dexBuilderDebugAndroidTest` 直接失败，
报的是 R8 内部类名（`com.android.tools.r8.internal.sy`），**看不出跟测试名有关**。
androidTest 里一律用 ASCII camelCase（`dataSurvivesDatabaseReopen`）。

---

## 9. 门禁

| 门 | 判据 | 现状 |
|---|---|---|
| 迁移器单测 | 用夹具跑 §5 的 12 条 + G1 计数 + G2 幂等 | **已建**：`LegacyMigratorTest` 22 例（含夹具计数量级断言、幂等逐字段相等、坏数据只跳过、孤立作用域不丢） |
| 分块协议单测 | 序号连续性 + 拼接完整性 + sha256 + 半成品清理 | **已建**：`MigrationInboxTest` 9 例 |
| 通道健康检查 | 迁移页读到的键集非空（§2.2 的配置前提） | **已建**：导出器第一步即断言，读空明确失败（§6.1） |
| 导出器端到端 | 设备上真实跑通、与夹具一致 | **已验证**（§6.4）：31/32 键逐字节相同，多块路径内容一致 |
| 数据层**运行时** | Room 在真实设备上开库/事务/重开 | **已建**：`LuzzyStoreTest`（仪器化）9 例全绿（§8.1） |
| 真机覆盖安装 | v2.x release → v3.0 release 覆盖安装，数据完好 | **待做**（阶段 4） |
| 迁移入口接线 | 谁在什么时候启动迁移 WebView、进度与报告怎么呈现 | **待做**（P4-C） |

---

## 10. P4-B-3.4：UI 接入真实存储（已做）

### 10.1 接了什么

聊天页（`ChatPage`）的会话数据来源从「内存 + 内置演示」改为**真实存储**：

| 时机 | 行为 | 实现 |
|---|---|---|
| 启动 | 载入当前角色的**分支列表 + 该角色上次所在分支 + 各分支消息** | `ChatSessionRepository.load()`（分支按 id 读；`activeBranchId` 来自 `branch_meta`） |
| 发送 | 追加用户消息行 → 生成收尾再追加助手回复行 | `append()`（`sortIndex` 由调用方给，避免边追加边查计数导致跳号） |
| 编辑 | 只更新该行 `content` | `updateContent()`（**payload 列不动** → 旧结构的多余字段不丢） |
| 删除 | 单条 / 及其后 | `delete()` |
| 切分支 / 重命名 / 删除分支 | 写 `branches` + `branch_meta`（删分支连消息一起清） | `rememberActiveBranch()` / `renameBranch()` / `deleteBranch()` |

**思考内容也持久化**：`reasoning` 列在载入时还原成思考节点，否则重启后消息会「少一块」。

**界面不等 IO**：每个写操作是独立协程（`persist {}`），消息立刻上屏，落盘失败只影响持久化、
不回滚已呈现内容 —— 顺序是「先让人看到，再保证存住」。但**错误不吞**：失败会打日志
（吞掉的话「没存住」会表现为「重启后少一条」，那时再查就晚了）。

### 10.2 空库策略（明确决定）

存储为空（首次安装、尚未迁移）时**回落到内置演示角色**，并且此时
**`characterUuid == null` ⇒ 一律不落盘**：没有宿主角色的消息无处可存，
与其写半套数据（有消息没角色）不如明说这是演示。真实用户的数据由迁移通道灌入后，
持久化即自动生效。

### 10.3 测试接缝（两个，都是为可测性而加的）

- `ChatPage(sessionRepository = …)`：注入指向临时库的仓库；
- `ChatPage(engineFactory = …)`：注入假传输（**UI 测试不联网**）。

不注入的后果是「初始界面取决于设备上 `luzzy.db` 恰好有没有数据 + 真发网络请求」——
那是隐藏耦合，本机绿、换机红。同理**每个用例一个独立临时库**。

### 10.4 验收（`ChatPersistenceTest`，仪器化 5 例，全绿）

1. 启动展示的是**存储里的消息**而不是演示数据（并先自证种子确实入库）；
2. 发送 → 库里出现该条（按内容找，不数数）；
3. 编辑 → 库里 `content` 变了，而 **payload 一字未动**（旧结构字段不丢）；
4. **「杀进程重启」**：关连接、同一文件重开 → 仓库能读回刚写的消息、原历史、当前分支与思考节点；
5. 分支切换落盘（`activeBranchId` 从 b1 → main）。

> 顺带一条**测试自省**：第 1、5 例最初断言写死「启动在主线」，结果全红——因为样例数据里
> `activeBranchId` 是 **b1**，界面**正确地**打开了分支。**是测试的假设错了，不是应用错了**；
> 修好断言后，它反而变成了「分支按 id 读」这个要求的验收点。

### 10.5 测试写法上的四条坑

见 `docs/CHAT-REGRESSION.md` §4：`Thread.sleep` 轮询会饿死帧（协程不跑）、
`performTextInput` 是追加不是替换、`substring = true` 会假绿、一个回合会追加两条消息
（所以别用「条数 +1」断言）。

### 10.6 未做

- **设置持久化（3.3）**：主题 / 字号 / 供应商 / 模型 / 工具开关收敛到统一存储 +
  旧 `SharedPreferences`（`luzzy_transport`）自动迁移；apiKey 仍只存设备本地。
  目前这些仍由 `TransportStore`（SharedPreferences）承担，与旧版行为一致。
- **多候选持久化**：`‹ n/m ›` 只持久化当前展示的那一版（存储里一消息一行，候选集合是纯界面态）。
  要持久化需改表（一条消息多行候选），属 P5。
- **迁移入口接线**：谁在什么时候启动迁移 WebView、进度与报告怎么呈现（P4-C）。
