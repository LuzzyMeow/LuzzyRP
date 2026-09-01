# LuzzyRP v1.0.0 重建计划 —— RP-Hub 二次开发 · 原生 WebView 壳

> 项目宗旨：**"每次对话，都像一本有你的小说。"**
> 本文档是 LuzzyRP 全面重建（v0.3.0 → v1.0.0）的完整实施计划：摈弃现有 Kotlin/Compose 工程，转为对开源项目 [STA1N156/RP-Hub](https://github.com/STA1N156/RP-Hub) 的二次开发，以原生 WebView 壳形式发布为安卓应用。
> 本文档由项目负责 Agent 维护；任何偏离本计划的决策必须记录进 `docs/WORKLOG.md`。

---

## 0. 决策记录

| # | 决策 | 说明 |
|---|------|------|
| 1 | 全面重建 | 摈弃现有全部工程内容（app/ core/ 原生 Kotlin + Compose 代码、真流式/Agentic/KV 不变性体系、设计 SKILL 强制条款），版本线自 **v1.0.0 / versionCode 1** 重新起算。 |
| 2 | 上游项目 | 以 [STA1N156/RP-Hub](https://github.com/STA1N156/RP-Hub)（当前本地参考克隆 `rp-hub-reference/`，上游版本 1.8.9）为唯一代码基底。**后端代码（presence-server/）完全不动**，仅做前端优化与安卓壳封装。 |
| 3 | 壳技术选型 | **原生 Kotlin WebView 壳**（非 Capacitor、非 Compose 重写）。理由：零依赖链、最贴合「只优化前端」、图标/签名/keystore 全复用。 |
| 4 | 仓库 | `LuzzyMeow/LuzzyRP`（公开），许可证 CC BY-NC 4.0（与上游一致，二创合规）；git 操作走 SSH（`git@github.com:LuzzyMeow/LuzzyRP.git`）。 |
| 5 | 应用身份 | 保留：应用名 **LuzzyRP**、启动图标（现有 mipmap 全套 + `luzzy_logo.png`）、applicationId `com.luzzymeow.luzzyrp`、签名 keystore（`keystore/luzzy-release.keystore`）。 |
| 6 | 保留项 | ① NSFW 协议（RP-Hub `built-in-content.js` 内 nsfw_rules 原样保留，禁止任何审查/过滤/改写逻辑触碰）；② 字体改为 RP-Hub 规定字体栈（`--app-font-modern` / `--app-font-serif`，Lora 本地化）；③ CHANGELOG 记录习惯（`### vX.Y.Z — 标题` + 分类 + 构建结果格式）。 |
| 7 | 摈弃项 | 除上述保留项外的一切：真流式 6 不变性、Agentic 6 不变性、KV 缓存、Room 数据层、sqlite-vec、设计 SKILL 13 条、图标/字体硬性规定（规定 6）、占位符禁令（规定 7）等全部作废。 |
| 8 | 更新检查 | RP-Hub 的 `rphub-update-api` meta 指向原项目服务器（`rphub-presence.zeabur.app`）——二创后**默认禁用**（patch 移除），后续可选自建（见 §7.4）。 |
| 9 | 上游同步 | 保留 `upstream` remote 指向 RP-Hub 官方仓库；同步走「覆盖 + patch 重放」模式（见 §7），二创改动全部隔离在独立扩展文件与壳工程内。 |
| 10 | 版本基线 | LuzzyRP v1.0.0 基于 RP-Hub **1.8.9**（本地 `rp-hub-reference/` 当前版本）。 |

---

## 1. 背景与目标

### 1.1 现状

- 现有 LuzzyRP 为原生 Android Kotlin 2.4 + Jetpack Compose 应用，v0.3.0（versionCode 4），状态「开发中 · 不可正常游玩」。
- 核心玩法链路（真流式、Agentic、记忆/摘要、世界书召回）尚未完整实机验收，UI 仍在重制，存在已知遗留项。
- 开发成本高、验收周期长，距「可玩」仍有距离。

### 1.2 目标

- **v1.0.0 即开箱可玩**：RP-Hub 本身是成熟可用的纯前端应用（角色卡/世界书/正则/分支/记忆/生图全功能），套壳后直接获得完整可玩体验。
- **只优化前端**：不碰 RP-Hub 后端（presence-server 原样保留代码，不部署）。
- **可独立扩展**：壳 + 桥 + 独立扩展层三层结构，新功能开发与上游同步互不干扰。
- **可长期同步上游**：RP-Hub 更新勤（1.8.9 四天前发布），同步成本必须控制在「覆盖 + 重打 patch」级别。

### 1.3 非目标

- 不重写 RP-Hub 前端为 Compose / 原生 UI。
- 不引入 Capacitor / Cordova / TWA 等第三方壳框架。
- 不部署 presence-server，不维护上游更新检查服务（默认禁用）。
- 不上架 Google Play（nsfw_rules 含「12 岁即为成年」条款，仅侧载分发）。

---

## 2. 许可证与合规

### 2.1 上游许可证

RP-Hub 采用 **CC BY-NC 4.0**（知识共享-署名-非商业性使用 4.0 国际），与现有 LuzzyRP 许可证**完全一致**，二创无协议冲突。

### 2.2 二创义务

| 义务 | 落实方式 |
|------|----------|
| 署名（Attribution） | README 显著位置声明「基于 STA1N156/RP-Hub 二次开发」+ 指向原项目链接 + 标明修改；保留上游 LICENSE 文件 |
| 非商业（NonCommercial） | 项目保持 CC BY-NC 4.0，不商用、不内置广告盈利 |
| 修改声明 | README 与 CHANGELOG 中注明基于上游哪个版本（1.8.9）及修改范围 |

### 2.3 合规红线

- 上游 LICENSE 文件**原样保留**在仓库内，不得删除或改写。
- 上游 README 的免责与授权声明段落保留（可附加二创说明，不可删原文）。
- 分发仅限侧载（APK 直装），禁止任何应用商店上架。

---

## 3. 目标架构

```
┌─────────────────────────────────────────────────┐
│  LuzzyRP 安卓应用（Kotlin 壳工程）                │
│                                                 │
│  ┌─────────────────────────────────────────┐  │
│  │  WebView（加载 assets/rphub/index.html）   │  │
│  │  ┌───────────────────────────────────┐  │  │
│  │  │  RP-Hub 上游文件（零改动/仅 patch）  │  │  │
│  │  │  index.html · assets/js/*.js       │  │  │
│  │  │  assets/css/styles.css             │  │  │
│  │  └───────────────────────────────────┘  │  │
│  │  ┌───────────────────────────────────┐  │  │
│  │  │  二创扩展层（独立文件，不碰上游）     │  │  │
│  │  │  luzzy-ext.js · luzzy-theme.css    │  │  │
│  │  │  luzzy-bridge.js（桥接封装）         │  │  │
│  │  └───────────────────────────────────┘  │  │
│  └──────────────┬──────────────────────────┘  │
│                 │ addJavascriptInterface      │
│  ┌──────────────▼──────────────────────────┐  │
│  │  JSBridge 原生层（LuzzyBridge.kt）         │  │
│  │  文件选择 · 导出 · 剪贴板 · 通知 · 深链    │  │
│  └─────────────────────────────────────────┘  │
└─────────────────────────────────────────────────┘
```

### 3.1 三层职责

| 层 | 内容 | 与上游关系 |
|----|------|-----------|
| 上游层 | RP-Hub 6 个 JS + styles.css + index.html（仅品牌 patch） | 直接来自 upstream，覆盖式同步 |
| 扩展层 | `luzzy-ext.js`（新功能 JS）、`luzzy-theme.css`（字体/品牌覆盖）、`luzzy-bridge.js`（桥接封装） | 完全独立，同步时零冲突 |
| 原生层 | WebView 壳 + `LuzzyBridge.kt` + 系统能力 | 完全独立，与上游无关 |

### 3.2 加载顺序（index.html 内）

```
上游脚本（Vue → marked → dompurify → sortablejs → 上游 6 JS）
    ↓
luzzy-bridge.js（桥接封装，最先加载）
    ↓
luzzy-theme.css（字体/品牌覆盖）
    ↓
luzzy-ext.js（扩展功能，最后加载，可访问上游全局对象）
```

---

## 4. 工程结构

```
LuzzyRP/
├── app/                          # 安卓壳工程（Kotlin）
│   ├── src/main/
│   │   ├── java/com/luzzymeow/luzzyrp/
│   │   │   ├── MainActivity.kt           # 单 Activity，WebView 宿主
│   │   │   ├── web/
│   │   │   │   ├── WebViewSetup.kt       # WebView 配置（JS 开关/缓存/DOM storage）
│   │   │   │   ├── LuzzyBridge.kt        # JSBridge 原生实现（@JavascriptInterface）
│   │   │   │   ├── FileChooserHandler.kt # onShowFileChooser（角色卡导入）
│   │   │   │   └── DownloadHandler.kt    # DownloadListener（导出）
│   │   │   └── util/
│   │   │       └── AssetExtractor.kt     # assets 解压到 filesDir（首次启动）
│   │   ├── res/                          # 现有 mipmap 全套 + luzzy_logo.png 复用
│   │   └── assets/
│   │       ├── rphub/                    # RP-Hub 上游文件（同步目标）
│   │       │   ├── index.html
│   │       │   ├── character/ novel/
│   │       │   ├── assets/css/ assets/js/
│   │       │   └── vendor/               # 离线化 CDN 依赖（见 §6.2）
│   │       └── ext/                      # 二创扩展层（独立文件）
│   │           ├── luzzy-bridge.js
│   │           ├── luzzy-theme.css
│   │           └── luzzy-ext.js
│   ├── build.gradle.kts                  # 复用现有签名/ABI 拆分配置
│   └── proguard-rules.pro
├── rp-hub-reference/              # 上游参考克隆（保留 upstream remote）
├── tools/
│   ├── sync-upstream.ps1         # 上游同步脚本（fetch → 覆盖 → patch 重放）
│   ├── apply-patches.ps1         # patch 重放脚本
│   └── patches/                  # 二创 patch 文件（品牌/字体/meta）
│       ├── 001-brand-title.patch
│       ├── 002-disable-update-check.patch
│       └── 003-entry-logo.patch
├── docs/                         # 规划/日志/归档
├── keystore/                     # 现有签名（复用）
├── keystore.properties           # 现有签名配置（不入库）
├── CHANGELOG.md                  # 沿用现有格式习惯
├── README.md                     # 重写（含二创署名声明）
├── LICENSE                        # CC BY-NC 4.0（上游 LICENSE 保留 + 本项目声明）
├── HARD_REQUIREMENTS.md           # 重写（见 §5.3）
├── settings.gradle.kts            # 仅 :app 单模块
└── build.gradle.kts
```

---

## 5. 保留项与摈弃项清单

### 5.1 保留项（硬性）

| # | 保留项 | 落点 | 守护方式 |
|---|--------|------|----------|
| 1 | NSFW 协议 | RP-Hub `assets/js/built-in-content.js` 内 `nsfw: Object.freeze({...})` 预设（含 nsfw_rules 全文） | 同步时**原样覆盖**，禁止任何 patch 触碰；扩展层禁止添加内容过滤器/敏感词拦截/输出改写 |
| 2 | 字体（原项目规定） | RP-Hub 字体栈：`--app-font-modern`（Inter / HarmonyOS Sans SC / MiSans / OPPO Sans / Alibaba PuHuiTi 3.0 / Noto Sans SC / ...）+ `--app-font-serif`（Lora / Noto Serif SC / ...） | `luzzy-theme.css` 覆盖变量，**不改 styles.css**；Lora 本地化（§6.2） |
| 3 | CHANGELOG 习惯 | 仓库根 `CHANGELOG.md` | 格式：`### vX.Y.Z — 标题` + 「新增/优化/修复/注意事项」分类 + 构建结果与 versionCode |
| 4 | 应用图标 | `app/src/main/res/mipmap-*` 全套 + `drawable-nodpi/luzzy_logo.png` | 直接复用，不重新生成 |
| 5 | 应用名称 | `LuzzyRP`（strings.xml + README 标题） | 直接复用 |
| 6 | 签名与身份 | `keystore/luzzy-release.keystore` + `keystore.properties` + applicationId `com.luzzymeow.luzzyrp` | 直接复用 |

### 5.2 摈弃项（全部作废）

| 类别 | 内容 |
|------|------|
| 代码 | app/ core/ 全部 Kotlin 源码（ChatService、GenerationHandler、Room、DataStore、sqlite-vec、Provider 三协议、PNG tEXt 读写等） |
| 不变性 | 真流式 6 不变性（S1-S6）、Agentic 6 不变性（A1-A6）、KV 缓存命中、`[INVARIANT-*]` 守护注释体系 |
| 硬性规定 | 原 13 条中除保留项外的全部（流式/Agentic/图标字体限制/占位符禁令/工具同步/发布流程等） |
| 设计体系 | DESIGN.md（Aurora Dual 色板、MotionTokens、LuzzyMixedFontFamily）、4 项设计 SKILL 强制条款、docs/skills/ |
| 数据 | Room schema、DataStore 设置、旧版本数据（不兼容，不迁移） |
| 文档 | 原 PLAN-v0.1.0.md、AGENT-GUIDE.md、INVARIANTS-CHECKLIST.md（归档至 docs/archive/ 后移除） |

### 5.3 新 HARD_REQUIREMENTS.md（重写为精简版）

新硬性规定（v1.0.0 起生效）：

1. **NSFW 协议不可触碰**：RP-Hub `built-in-content.js` 内 nsfw_rules 原样保留；禁止任何审查、过滤、改写逻辑触碰该内容；扩展层不得添加内容过滤器、敏感词拦截、输出改写。
2. **上游文件最小改动**：RP-Hub 上游文件（index.html、assets/js/*.js、assets/css/styles.css）仅允许通过 `tools/patches/` 内登记的 patch 修改；任何新改动必须新增 patch 文件并登记，禁止裸改。
3. **扩展层隔离**：所有二创新功能必须落在 `assets/ext/` 独立文件（luzzy-ext.js / luzzy-theme.css / luzzy-bridge.js），禁止写入上游文件。
4. **字体锁定**：字体遵循 RP-Hub 规定字体栈（`--app-font-modern` / `--app-font-serif`），通过 `luzzy-theme.css` 覆盖；Lora 必须本地打包，禁止运行时依赖 Google Fonts CDN。
5. **CHANGELOG 同步**：每次版本更新必须同步 `CHANGELOG.md`（沿用 `### vX.Y.Z — 标题` + 分类格式）与 `README.md`。
6. **上游同步纪律**：上游发版后按 §7 SOP 同步；同步后必须实测数据兼容与核心功能回归。
7. **工作区整洁**：清理冗余文件，docs 内分类归档（plan / archive / 参考资料）。
8. **发布流程**：编译新版本 APK（复用现有签名与 ABI 拆分）→ 推送远程 → 按旧版 release 排版编写 release 内容（仅稳定版附 APK）。

---

## 6. 分阶段实施计划

### Phase 0 · 准备与备份（0.5 天）

**目标**：安全备份现状，建立新工程基线。

| # | 任务 | 产出 |
|---|------|------|
| 0.1 | 现有工程完整备份（git 历史保留，代码归档） | `docs/archive/legacy-v0.3.0/`（gitignore，仅本地） |
| 0.2 | 确认 `rp-hub-reference/` 与上游 1.8.9 一致（`git -C rp-hub-reference fetch upstream && git diff`） | 版本核对记录 |
| 0.3 | 登记上游文件 SHA-256 指纹（同步基线） | `tools/upstream-fingerprints.txt` |
| 0.4 | 确认 keystore 可用（`keytool -list`） | 签名可用性确认 |

**验收**：备份完整、上游版本锁定、签名可用。

### Phase 1 · 壳工程骨架（1 天）

**目标**：最小可运行 WebView 壳，加载本地 RP-Hub。

| # | 任务 | 产出 |
|---|------|------|
| 1.1 | 精简 Gradle 工程为单模块 `:app`（删除 core/ 三模块引用） | settings.gradle.kts / build.gradle.kts |
| 1.2 | 复制 RP-Hub 上游文件到 `app/src/main/assets/rphub/` | assets/rphub/ 完整目录 |
| 1.3 | `MainActivity.kt`：单 Activity + WebView 加载 `file:///android_asset/rphub/index.html` | 可运行壳 |
| 1.4 | `WebViewSetup.kt`：JS 启用、DOM storage 启用、缓存策略、`file://` 访问、混合内容策略 | WebView 配置 |
| 1.5 | 首次启动 `AssetExtractor.kt`：assets 解压到 `filesDir/rphub/`（localStorage 持久化需要可写路径） | 数据目录 |
| 1.6 | 网络权限 + `usesCleartextTraffic` 评估（API 端点多为 https，按需配置） | AndroidManifest.xml |

**关键决策**：加载路径选 `filesDir`（解压后加载）而非直接 `android_asset`——WebView 的 localStorage 在 `file:///android_asset` 下不可靠，解压到应用私有目录是标准做法。

**验收**：APK 安装后能打开 RP-Hub 主界面，能新建角色、发起对话（需用户填 API Key）。

### Phase 2 · 资源离线化（1 天）

**目标**：移除全部 CDN 运行时依赖，断网可用。

| # | 依赖 | 来源 | 本地化方式 |
|---|------|------|-----------|
| 2.1 | Vue 3 | unpkg.com | 下载 `vue.global.prod.js` → `assets/rphub/vendor/`，index.html 改本地引用（patch 004） |
| 2.2 | Tailwind CDN | cdn.tailwindcss.com | 下载 → vendor/；**注意**：Tailwind CDN 是运行时 JIT，本地化后功能不变但首屏编译耗时增加；评估是否改用构建时预编译（见风险 R3） |
| 2.3 | marked | cdn.jsdelivr.net | 下载 → vendor/ |
| 2.4 | DOMPurify | cdn.jsdelivr.net | 下载 `purify.min.js`（锁定 3.0.6 版本）→ vendor/ |
| 2.5 | SortableJS | cdn.jsdelivr.net | 下载 → vendor/ |
| 2.6 | Lora 字体 | fonts.googleapis.com | 下载 woff2（400/500/600/700 + italic）→ `assets/rphub/fonts/`，`luzzy-theme.css` 内 `@font-face` 本地引用 |
| 2.7 | 其他外链 | index.html 内检查 | 逐条审计：`cdn.sta1n.cn/keys`（原项目链接，保留或移除）、`qianxun1688.com`（推广链接，移除） |

**验收**：飞行模式下 APK 全功能可用（除真实 API 请求外）。

### Phase 3 · 品牌化（0.5 天）

**目标**：LuzzyRP 品牌落地，二创 patch 建立。

| # | 任务 | patch |
|---|------|-------|
| 3.1 | index.html `<title>RP Hub</title>` → `<title>LuzzyRP</title>` | 001-brand-title.patch |
| 3.2 | 入口 logo（`entry-logo-rp` / `entry-logo-hub` 文字）→ LuzzyRP 品牌样式 | 003-entry-logo.patch |
| 3.3 | `rphub-update-api` meta 移除（禁用上游更新检查） | 002-disable-update-check.patch |
| 3.4 | 应用名/图标：复用现有 mipmap + strings.xml | 壳工程，无 patch |
| 3.5 | `luzzy-theme.css`：字体栈覆盖（`--app-font-family` 指向本地 Lora + 系统字体栈） | 扩展层，无 patch |
| 3.6 | 侧边栏/关于页品牌信息（如有上游品牌字样） | 按审计结果补 patch |

**验收**：应用名 LuzzyRP、图标正确、界面字体为 RP-Hub 规定字体栈、无上游更新提示。

### Phase 4 · JSBridge 桥接层（1.5 天）

**目标**：打通前端与原生能力，覆盖 RP-Hub 的 I/O 需求。

| # | 桥接能力 | 原生实现 | 前端调用 |
|---|---------|---------|---------|
| 4.1 | 文件选择（角色卡 PNG/JSON 导入） | `FileChooserHandler`：`onShowFileChooser` + SAF | 上游 `input[type=file]` 自动走 WebView 回调，**无需改上游** |
| 4.2 | 文件导出（角色卡/世界书/分支导出） | `DownloadHandler`：`DownloadListener` + 系统下载/SAF 保存 | 上游 `a[download]` 自动走 WebView 回调，**无需改上游** |
| 4.3 | 剪贴板 | `LuzzyBridge.copyToClipboard(text)` | `window.LuzzyBridge.copyToClipboard()` |
| 4.4 | Toast/通知 | `LuzzyBridge.toast(msg)` | 扩展层调用 |
| 4.5 | 版本信息 | `LuzzyBridge.getAppVersion()` → `{versionName, versionCode, upstreamVersion}` | 关于页/扩展层显示 |
| 4.6 | 深链（后续扩展） | intent-filter + `LuzzyBridge.onDeepLink(callback)` | 预留接口 |
| 4.7 | 原生设置页（后续扩展） | 预留 `LuzzyBridge.openNativeSettings()` | 预留接口 |

**验收**：角色卡 PNG 导入导出全流程可用；剪贴板/Toast 桥接调用成功。

### Phase 5 · 独立扩展层（1 天，首版）

**目标**：建立扩展层骨架与首个独立功能，验证「不碰上游」开发模式。

| # | 任务 | 说明 |
|---|------|------|
| 5.1 | `luzzy-bridge.js`：封装 `window.LuzzyBridge` 调用（存在性检测 + 降级） | 扩展层基础 |
| 5.2 | `luzzy-ext.js`：首版功能——关于页品牌信息注入（版本号/上游版本/二创声明） | 验证扩展模式 |
| 5.3 | `luzzy-theme.css`：字体覆盖 + 品牌色微调（可选，克制） | 验证样式覆盖模式 |
| 5.4 | 扩展层加载机制：index.html 尾部注入 3 个 script/link（patch 005） | 唯一需要动 index.html 的扩展点 |

**验收**：扩展功能生效；上游文件除 5 个登记 patch 外零改动。

### Phase 6 · 上游同步机制（0.5 天）

**目标**：同步 SOP 脚本化，验证一次完整同步。

| # | 任务 | 产出 |
|---|------|------|
| 6.1 | `tools/sync-upstream.ps1`：fetch upstream → 对比指纹 → 覆盖上游文件 → 重放 patches → 报告 | 同步脚本 |
| 6.2 | `tools/apply-patches.ps1`：git apply 全部登记 patch，失败时逐条报告 | patch 脚本 |
| 6.3 | 同步演练：模拟上游假发版（本地改一个文件），跑通全流程 | 演练记录 |
| 6.4 | 同步后回归清单：数据兼容（localStorage 结构）、核心功能（对话/角色卡/世界书） | 回归清单 |

**验收**：脚本跑通；演练中扩展层文件零冲突。

### Phase 7 · 测试与验收（1 天）

| # | 测试项 | 方法 |
|---|--------|------|
| 7.1 | 冷启动/热启动/后台恢复 | 真机 + 模拟器 |
| 7.2 | 断网可用性（离线化验证） | 飞行模式全功能走查 |
| 7.3 | 角色卡 PNG/JSON 导入导出 | 真机 SAF 全流程 |
| 7.4 | 对话全流程（API Key 配置 → 发送 → 流式渲染 → 分支/回档） | 真机实测 |
| 7.5 | 世界书/正则/记忆/生图 | 真机实测 |
| 7.6 | 数据持久化（杀进程重启后数据保留） | 真机实测 |
| 7.7 | 大文件/长会话性能 | 长会话压力走查 |
| 7.8 | 上游同步回归 | 同步演练后核心功能复测 |

**验收**：全部通过，无 P0/P1 缺陷。

### Phase 8 · 发布（0.5 天）

| # | 任务 | 说明 |
|---|------|------|
| 8.1 | 版本号：v1.0.0 / versionCode 1 | build.gradle.kts |
| 8.2 | `assembleRelease`（复用现有签名 + ABI 拆分） | 产物：arm64-v8a / x86_64 / universal |
| 8.3 | CHANGELOG.md 新增 v1.0.0 条目（沿用格式） | 含「基于 RP-Hub 1.8.9」声明 |
| 8.4 | README.md 重写（二创署名 + 构建说明 + 侧载声明） | 含上游链接与修改声明 |
| 8.5 | 推送远程 + GitHub Release（仅稳定版附 APK） | 按旧版 release 排版 |

---

## 7. 上游同步 SOP

### 7.1 触发条件

- 上游发版（RP-Hub 更新公告更新 / GitHub release / 手动检查）。
- 建议每月例行检查一次，或关注上游仓库。

### 7.2 同步流程

```
1. git fetch upstream
2. git diff upstream/main --stat          # 看改动范围
3. 读上游 built-in-content.js 底部更新公告  # 了解新功能
4. 覆盖上游文件（index.html + assets/，排除 vendor/ 与 fonts/）
5. 重放 patches（tools/apply-patches.ps1）
6. 检查 vendor/ 依赖是否需要更新（上游可能换 CDN 版本）
7. 实测：数据兼容 + 核心功能回归（§6.4 清单）
8. 更新 CHANGELOG（同步记录 + 上游版本号）
9. 重新构建 APK
```

### 7.3 冲突处理

| 情况 | 处理 |
|------|------|
| patch 重放失败（上游改了同一处） | 手工合并该 patch，更新 patch 文件，登记到 WORKLOG |
| 上游新增文件 | 直接纳入（先审计是否含外链/推广） |
| 上游删除文件 | 确认无扩展层引用后删除 |
| 上游改数据结构（localStorage） | 实测老数据兼容；不兼容则记录迁移方案 |

### 7.4 更新检查（可选自建）

- 默认：patch 002 移除 `rphub-update-api` meta，应用内无更新提示。
- 可选：自建极简版本服务（返回 `{latestVersionId}`），复用上游 `update-check.js` 逻辑，meta 指向自建端点。**列入 v1.1.0 候选，不在 v1.0.0 范围。**

---

## 8. 风险登记册

| # | 风险 | 等级 | 影响 | 对策 |
|---|------|------|------|------|
| R1 | Tailwind CDN 本地化后首屏编译慢 | 中 | 首屏体验下降 | 评估构建时预编译（Tailwind CLI 产出静态 CSS）；或接受首屏延迟 + 启动预热 |
| R2 | 上游大文件（app.js 512KB）patch 冲突 | 高 | 同步困难 | 守住「不裸改上游」纪律；patch 只落在 index.html 与 styles.css 的少量点位；冲突时手工合并并登记 |
| R3 | localStorage 数据兼容（上游升级） | 中 | 用户数据异常 | 每次同步后实测；必要时写迁移脚本（扩展层内） |
| R4 | WebView 版本碎片化（系统 WebView 差异） | 中 | 渲染/JS 兼容问题 | minSdk 26 起系统 WebView 均较新；真机矩阵测试（至少 2 台不同厂商） |
| R5 | 上游更新公告/推广链接混入 | 低 | 品牌混淆 | 同步审计：built-in-content.js 底部公告按需 patch 清理；外链（qianxun1688.com 等）移除 |
| R6 | 文件导出在 WebView 的兼容性 | 中 | 导出失败 | DownloadListener + SAF 双路径；真机验证 |
| R7 | 上游停更/删库 | 低 | 失去同步源 | 本地 rp-hub-reference 已是完整副本；停更后转纯自维护 |
| R8 | 12 岁条款合规风险 | 高（仅上架场景） | 商店下架/法律风险 | **仅侧载分发，不上架**；README 明示 |

---

## 9. 版本规划

| 版本 | 内容 | 附 APK |
|------|------|--------|
| v1.0.0 | 重建首版：壳 + 离线化 + 品牌 + 桥接 + 扩展层骨架 + 同步机制 | ✓（稳定版） |
| v1.1.0 | 扩展功能第一批（候选：原生设置页、深链、自建更新检查、剪贴板增强） | ✓ |
| v1.2.0+ | 跟随上游节奏迭代 + 独立功能持续扩展 | 按稳定版 |

版本号独立于上游（LuzzyRP v1.0.0 基于 RP-Hub 1.8.9），CHANGELOG 每条记录注明上游基线版本。

---

## 10. 验收清单（发版前逐项）

- [ ] 许可证：上游 LICENSE 原样保留；README 含二创署名声明；项目保持 CC BY-NC 4.0
- [ ] NSFW：built-in-content.js 内 nsfw_rules 与上游逐字节一致（指纹比对）
- [ ] 字体：界面使用 RP-Hub 规定字体栈；Lora 本地打包；断网字体正常
- [ ] 离线化：飞行模式下除 API 请求外全功能可用
- [ ] 品牌：应用名 LuzzyRP、图标正确、无上游更新提示、无推广外链
- [ ] 桥接：角色卡 PNG/JSON 导入导出全流程可用
- [ ] 扩展层：luzzy-ext.js 功能生效；上游文件仅登记 patch 改动
- [ ] 同步机制：sync-upstream.ps1 演练通过
- [ ] 数据：杀进程重启数据保留；长会话性能可接受
- [ ] 文档：CHANGELOG v1.0.0 条目（含上游基线声明）；README 重写完成
- [ ] 构建：assembleRelease 成功（签名 + ABI 拆分），产物三件套

---

## 附：上游文件指纹基线（Phase 0.3 登记）

| 文件 | SHA-256 |
|------|---------|
| index.html | （Phase 0 登记） |
| assets/js/app.js | （Phase 0 登记） |
| assets/js/ui-components.js | （Phase 0 登记） |
| assets/js/data-services.js | （Phase 0 登记） |
| assets/js/runtime-services.js | （Phase 0 登记） |
| assets/js/core-utils.js | （Phase 0 登记） |
| assets/js/built-in-content.js | （Phase 0 登记） |
| assets/css/styles.css | （Phase 0 登记） |
