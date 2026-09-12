# tools/mig-fixture · 迁移夹具的生成与复现

本目录是 **v3.0 P4 迁移夹具的生成工具链**，唯一产出物是
[`app/src/test/resources/legacy/webview-db-fixture.json`](../../app/src/test/resources/legacy/webview-db-fixture.json)。
设计真源见 [`docs/DESIGN-migration.md`](../../docs/DESIGN-migration.md)。

**为什么要留下这些脚本**：夹具是「迁移器写对了没有」的唯一判据。如果夹具的来历只剩一句
「某天在模拟器上造的」，日后就没人能重造它、也没人能验证它没被改坏。所以生成过程与夹具一起入库。

---

## 0. 这套脚本在做什么

核心思路：**不用手写样本，而是用 CDP 驱动前端自身函数造数据**。
先看这段，就知道为什么非这样不可——手写的样本一定长这样：

```js
{ role: 'assistant', name: 'Vanio', content: '……' }   // 看起来对
```

而真前端写出来的是（夹具实测原样）：

```js
{ role:'assistant', name:'Vanio', content:'……', reasoning:'……', id:'…',
  shouldAnimate:true, isCotOpen:false, isReasoningOpen:false,
  isReasoningUserToggled:false, isReasoningAutoCollapsed:true, isSelf:false,
  isSummaryOpen:false }
```

后者才带得出「瞬态字段白名单」（DESIGN-migration §5 坑 5）这类要求。
**驱动真实代码 = 拿到真实字段组合**，这是本目录存在的全部理由。

---

## 1. 前置条件

| 项 | 要求 |
|---|---|
| 设备 | **模拟器**（`emulator-5554`）。真机只装 release 包、不得装测试件（AGENTS §6.1） |
| 应用 | release 包已安装并启动（launcher 是 `MainActivity`，WebView 壳） |
| CDP | `adb -s emulator-5554 forward tcp:9222 localabstract:webview_devtools_remote_<pid>` |
| 密钥 | 开发用 API Key 从 `~/.zcode/v2/config.json` **运行时注入**（见 §3），**不落盘、不入库、不打印** |

取 devtools socket 名（内含 pid）：

```bash
adb -s emulator-5554 shell "cat /proc/net/unix | grep webview_devtools"
adb -s emulator-5554 forward tcp:9222 localabstract:webview_devtools_remote_<pid>
curl -s http://127.0.0.1:9222/json/list     # 应看到 title=LuzzyRP
```

> Git Bash 下凡是给 adb 传设备绝对路径，必须加 `MSYS_NO_PATHCONV=1`，否则 `/data/...`
> 会被改写成 Windows 路径（本目录的坑表里记着，本轮实踩）。

---

## 2. 生成流程（按顺序跑，每步都会打印自己的结果 JSON）

```bash
D=.workbuddy/mig-out           # 产物目录（随便挑，勿入库）

node run.mjs gen1-identity.js      $D/g1.json   # 用户资料 + 供应商 override + 模型槽位
node run.mjs gen2-character.js     $D/g2.json   # 角色卡 + 世界书 + 正则 + 预设
node run.mjs gen3-chat.js          $D/g3.json   # 角色头像(真实 PNG) + 两轮真实生成
node run.mjs gen4-branch-memory.js $D/g4.json   # 剧情分支 + 分支上再生成一轮 + 经典记忆
node run.mjs gen6-vector-sta1n.js  $D/g6.json   # 真实向量记忆（3072 维 → 应用自身量化）
node run.mjs gen7-branch-vector.js $D/g7.json   # 分支 scope 的向量记忆
node run.mjs gen8-extras-legacy.js $D/g8.json   # 第二人设 + 两张补充角色 + 选择非零下标
node run.mjs gen8c-legacy.js       $D/g8c.json  # 旧库(v1, 带 store) + legacy 前缀记录
node run.mjs dump.js     app/src/test/resources/legacy/webview-db-fixture.json
node scrub.mjs           app/src/test/resources/legacy/webview-db-fixture.json
```

`gen*.js` 里的每一步都走**前端自己的函数**（`createNewCharacter`/`saveCharacter`/
`sendMessage`/`createStoryBranch`/`startBatchMemoryExtraction`…），脚本只负责「按真实 UI 的顺序调用它们」。

**必须最后跑 `scrub.mjs`**：夹具来自真机态页面，`rp_hub_settings` 里带着 API Key，
而夹具要进公开仓库。脱敏把密钥换成 `<REDACTED:name:lenN>`（**保留字段与长度**，
迁移器照旧能断言字段被搬运），并把清单写进 `provenance.redaction`。
**提交前人工确认 `suspiciousRemaining` 为空。**

---

## 3. 密钥注入机制

脚本里写占位符，`run.mjs` 在把脚本送进页面**之前**从 `~/.zcode/v2/config.json` 读出真值替换：
`__DEV_API_KEY__`（DeepSeek，跑真实生成）、`__STA1N_KEY__`（STA1N，跑真实嵌入）。
密钥只经内存进入页面，**不写文件、不进日志**（`run.mjs` 只打印「已注入」）。

## 4. 别的脚本

| 脚本 | 用途 |
|---|---|
| `reload.mjs` | 用 CDP 重载页面。**清掉页内未决的 IndexedDB 请求队列**——删除/重建数据库卡住时靠它 |
| `probe-origin.mjs` | DESIGN-migration §2 的通道探针：把 WebView 导航到 `luzzy-origin-probe.html`，验证「另一个 `file://` 文件能否读写主页面的 IndexedDB」 |
| `luzzy-origin-probe.html` | 上述探针页。推到 `files/ext/` 后用（`adb push` + `su 0 cp`） |
| `dump.js` | 全量导出两库记录 → 夹具 JSON |
| `scrub.mjs` | 夹具脱敏（§2 末尾） |

---

## 5. 本轮实踩的两个坑（写在这里，别重踩）

1. **用不存在的库名「探测」会创建空库**：`indexedDB.open('SillyTavernDB')` 不是查询，
   是**创建**——得到一个**对象仓库为 0** 的空库，后续 `transaction(['store'])` 直接抛
   `NotFoundError`。本项目坑表（AGENTS §7）早有此条，本轮仍踩了一次。
   正解：要么别探测，要么打开时在 `onupgradeneeded` 里 `createObjectStore('store')`（照上游 `openAppDB` 做）。
2. **`transaction().objectStore().put()` 返回的是 `IDBRequest`，不是事务**：
   给它挂 `oncomplete` **永远不会触发**（请求只有 `onsuccess`/`onerror`）。
   首版探针因此卡在「写完了但脚本不返回」，看起来像环境问题。
   正解：事务对象单独持有，`req.onsuccess` 与 `tx.oncomplete` 分开挂。

3. **另有两个「走过但没走通」的路子**，不要重试：
   - BigModel 嵌入：接口本身可用（`/api/paas/v4/embeddings` 返回 200），
     但应用侧 `buildApiEndpoint` 会强制补 `/v1`（`baseUrl` 不以 `/v1` 结尾时），
     拼出 `…/paas/v4/v1/embeddings` → 404。**用 baseUrl 已含 `/v1` 的供应商**（STA1N）即可绕开。
   - DeepSeek 嵌入：其开放平台**不提供** embeddings 端点（实测 404）。
