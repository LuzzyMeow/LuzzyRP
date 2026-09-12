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
迁移器必须复刻这个优先级：**同键时以 `RPHubDB` 的值为准**，旧库仅补新库没有的键。

对象仓库固定为单库单仓：库名见上表，仓库名恒为 `'store'`，键为字符串。

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
| 3 | 双库：`RPHubDB` 优先，`SillyTavernDB` 补缺 | 先读新库建索引，旧库逐键只在**新库缺失**时采用 | 有 |
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

### 6.1 页面：`app/src/main/assets/ext/luzzy-migrate.html`

- **纯 JS，不启动 Vue、不加载上游脚本**；只做一件事：遍历两库 → 归一 → 分块回传。
- **只读**：除「写入探针」健康检查外不改动任何记录（G5）。
- 回传**分块**（每块 ≤256 KB）经桥方法 `migrateChunk(seq, payload)`，结束时 `migrateDone(summary)`；
  规避 Binder 事务上限（大头像 + 4096 字符向量很容易把单个 bundle 撑爆）。

### 6.2 桥方法（`LuzzyBridge.kt` + `luzzy-bridge.js` 封装，硬性规定 3/§5.4）

| 方法 | 作用 |
|---|---|
| `migrateStart()` | 清空上次残留、落盘聚合文件，返回会话 id |
| `migrateChunk(seq, payload)` | 追加一块（顺序校验，缺块即失败） |
| `migrateDone(summary)` | 收尾：校验块数/字节数一致 → 交给 Kotlin 迁移器 |
| `migrateError(message)` | 页面侧异常出口（不吞错） |

### 6.3 落盘位置

`filesDir/migration/incoming/` 下分块 + `manifest.json`（块数、每块 sha256、源库版本）。
**迁移成功后**只删 `incoming/`；旧 IndexedDB **保留**（G5）。

---

## 7. 迁移器（2.4，Kotlin）

### 7.1 结构

```
MigrationExporter (WebView 侧，JS)  →  MigrationDecoder (Kotlin, 纯函数)
        ↓                                       ↓
   manifest + chunks                    LegacySnapshot（内存模型，可测）
                                                ↓
                                    LegacyMigrator（纯 Kotlin，无 Android 依赖）
                                                ↓
                                       MigrationReport（成功/跳过/失败 + 原因）
                                                ↓
                                    目标存储（P4-B 定；见 §8）
```

**关键**：`LegacySnapshot` → 目标模型这一段是**纯 Kotlin 纯函数**，夹具 JSON 直接喂进去就能断言
（G1 的计数、G2 的幂等），**不需要模拟器**。

### 7.2 幂等（G2）

以「源键 + 记录 id/turn」为幂等键：重复迁移时同键**覆盖为相同值**而非追加；
迁移完成标记写入后，启动时若标记存在则不再迁移。单测：同一夹具跑两次 → 结果逐字段相等。

### 7.3 报告（G3）

`MigrationReport { migrated: {characters, branches, messages, vectorMemories, classicMemories,
worldEntries, presets, regexes, usageRecords, profiles}, skipped: [{key, reason}], failed: [...] }`。
**跳过与失败都进报告**，UI 只提示不阻断（用户已拍板：**不设回退**）。

---

## 8. P4-B 存储选型（**待 spike 定**，此处只记判据）

先试 **Room + KSP + KGP**：15 分钟内能构建通过 → 走 Room（照 rikkahub 的
「node 一行 + messages 用 JSON 列」模型，避免为消息建表）。
**否则回落 JSON 文件存储**：`kotlinx.serialization`（已是依赖，零新增）
+ 分文件 + **原子写**（临时文件 + rename）+ 内存索引。
若走回落，**必须**把「不选 Room 是因为 AGP 9 内置 Kotlin 下 KSP/KGP 构建约束」写进本文件 §8，
以免日后被当成随手决定。

---

## 9. 门禁

| 门 | 判据 | 现状 |
|---|---|---|
| 迁移器单测 | 用夹具跑 §5 的 12 条 + G1 计数 + G2 幂等 | **待建**（2.4） |
| 通道健康检查 | 迁移 WebView 打开后键集非空（§2.2 的配置前提） | **待建**（2.3） |
| 真机覆盖安装 | v2.x release → v3.0 release 覆盖安装，数据完好 | **待做**（阶段 4） |
