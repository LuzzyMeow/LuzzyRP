# AGENTS.md · LuzzyRP 开发 Agent 指南

> 本文件是**后续开发 / 更新 / 维护 Agent 的强制工作指南**。
> 任何 Agent 接手本仓库任务前，必须完整阅读本文件与 [`HARD_REQUIREMENTS.md`](HARD_REQUIREMENTS.md)，并遵守其中的全部纪律。
> 违反硬性规定任何一条即为不合格交付。

---

## 0. 项目一句话

**LuzzyRP = 原生 Kotlin 壳（Jetpack Compose UI，v3.0 起）**；上游 RP-Hub（Vue 3 Web 前端）的资产已于 P6 从工作树删除。**遵循上游开源协议、长期同步上游更新，但本项目有自己的功能路线**——会按需修改前端或后端 / 原生侧代码（上游改动登记纪律见 §4）。

> 与上游的关系是「同协议二创，不是镜像」：同步是手段（上游修复/新能力并入），
> 做出 LuzzyRP 自己的功能才是目的；同步时以「二创改动可重放」为硬约束，而非「逐字节跟随上游」。
> **唯一不可触碰的上游内容是 `built-in-content.js` 内的 `nsfw_rules`（硬性规定 1）**。

---

## 1. 文件地图

### 1.1 仓库根

| 路径 | 作用 | 维护者注意 |
|------|------|-----------|
| `README.md` | 项目门面（含二创署名声明） | Status 徽章与「当前版本」行由 gen-changelog 自动同步；版本说明收敛 CHANGELOG（硬性规定 5） |
| `CHANGELOG.md` | 更新日志 | 格式：`### vX.Y.Z — 标题` + 「新增/优化/修复/注意事项」分类 + 构建结果与 versionCode；每条注明上游基线版本 |
| `HARD_REQUIREMENTS.md` | 10 条硬性规定（最高约束） | 修改需在 CHANGELOG 声明 |
| `AGENTS.md` | 本文件 | 与 HARD_REQUIREMENTS 同步演进 |
| `DESIGN.md` | 设计真源（唯一设计契约，Claude token 体系） | 任何 UI 改动必须遵循；修改需按硬性规定 9 走设计流程 |
| `LICENSE` | CC BY-NC 4.0（上游资产适用） | **禁止删除/改写**（含上游 LICENSE 保留义务）；v3.0 起双许可并存：自有代码以 `LICENSE-AGPL-3.0`（AGPL-3.0）分发，并存结构与冲突记录见 `docs/LICENSING.md`；**从 rikkahub 复用的代码文件头必须保留原始版权声明与来源标注** |
| `keystore.properties` | 签名配置（storeFile/storePassword/keyAlias/keyPassword） | **不入库**（.gitignore）；签名密钥库 `keystore/luzzy-release.keystore` 同样不入库，须离线备份——**发布签名必须始终一致，换签 = 老用户无法覆盖升级**（§3.4） |
| `settings.gradle.kts` / `build.gradle.kts` / `gradle.properties` | 构建配置 | 仅 `:app` 单模块 |

### 1.2 壳工程（app/）

| 路径 | 作用 | 维护者注意 |
|------|------|-----------|
| `app/src/main/java/com/luzzymeow/luzzyrp/MainActivity.kt` | ~~单 Activity，WebView 宿主~~（**已删除**，P6 launcher 切 `ui.ComposeActivity`） | 若需恢复 WebView 路径请走 git 历史 |
| `app/src/main/java/com/luzzymeow/luzzyrp/ui/ComposeActivity.kt` | **launcher**（Compose UI 宿主） | v3.0 起唯一的用户入口 |
| `app/src/main/java/com/luzzymeow/luzzyrp/web/LuzzyBridge.kt` | JSBridge（**迁移通道专用**，运行时 UI 已不走 WebView） | 保留以支撑导出页对 `window.LuzzyBridge` 的调用 |
| `app/src/main/java/com/luzzymeow/luzzyrp/web/WebViewSetup.kt` · `FileChooserHandler.kt` · `DownloadHandler.kt` | WebView 侧配置与文件选择 | 同上，属迁移通道遗留 |

| `app/src/main/java/com/luzzymeow/luzzyrp/util/AssetExtractor.kt` | assets 解压到 filesDir | 首次启动幂等执行；版本升级时按版本号增量更新 |
| `app/src/main/res/` | 图标资源 | mipmap 全套 + `drawable-nodpi/luzzy_logo.png`。**2026-09-04 用户以 AI 生图新 LOGO 全面替换**（纯 1:1 满幅不透明，源图 `docs/design/brand-logo-v2-source.png`；adaptive=全图前景 68% 居中 + 同色纯背景 #EDD7BD；原透明贴纸方案与「禁止重新生成」约束由本次替换终止）——**后续更换图标一律按 §3.5 SOP 执行** |
| `app/build.gradle.kts` | 壳构建配置 | 签名（固定 luzzy 签名，见 §3.4）/ **单 APK 产出（ABI 拆分已关闭，2026-09-08 用户指示；2026-09-09 重申为长期纪律）** / versionCode 管理 |

> **v3.0 形态**：WebView 路径（`MainActivity` / `assets/rphub/**` / 旧 launcher）已于 P6 整体退役；
> 现行 UI 为 Jetpack Compose（`ui/` 包），数据层与迁移器沿用。

### 1.3 上游文件（~~app/src/main/assets/rphub/~~）

> **已于 P6 整体删除**（launcher 切 Compose，2026-09-13）。上游文件地图与 patch 登记见
> `tools/patches/README.md` 与 `rp-hub-reference/`；`nsfw_rules` 不可触碰约束永久有效（§4）。

### 1.4 扩展层（app/src/main/assets/ext/）

| 路径 | 作用 | 维护者注意 |
|------|------|-----------|
| `luzzy-bridge.js` | 桥接封装（存在性检测 + 降级） | 新增桥接方法必须同步此文件 |
| `luzzy-theme.css` | 主题变量 + 字体栈（DESIGN.md token 落地） | classic/亮/暗三套 `--tw-*` 变量为 **RGB 三元组**；暗色组件覆盖在此；token 改动须同步 DESIGN.md |
| `luzzy-ext.js` | 桥接自检 + 关于页品牌注入 | 主题/字体切换逻辑在 patch 010/011（上游 app.js 内），不在此文件 |
| `luzzy-changelog.js` | 关于页 CHANGELOG 数据（patch 014 挂载） | **生成文件勿手改**——构建期 genChangelog 任务自动重生成（v1.2.3），verify-markers R3 门禁拦截过期 |
| `luzzy-logo.png` | 关于页品牌图标 | 从 mipmap 启动图标复制的持久产物 |

### 1.5 工具与文档

| 路径 | 作用 | 维护者注意 |
|------|------|-----------|
| `tools/sync-upstream.ps1` | 上游同步脚本 | fetch → 覆盖 → patch 重放 → 报告 |
| `tools/apply-patches.ps1` | patch 重放脚本 | **两段重放，顺序关键**：① 实体段（`patches/entities/`，前像 = 上游纯净基线，须先落盘）→ ② 字符串块段（001-011，实体已覆盖同名改动多为 SKIP）；前像判定用实体头 `index pre` 的 LF 归一 blob id（见 §7 坑表） |
| `tools/verify-markers.ps1` | 标记校验门（硬性规定 10） | 同步/重放后必跑；按 README 登记逐项校验标记与敏感文件指纹，全绿才算同步完成 |
| `tools/patches/` | 登记 patch 文件 | 新 patch 必须编号登记（见 §4）；`entities/` 存实体 diff |
| `tools/gen-changelog.mjs` | 关于页 CHANGELOG 生成脚本 | 更新 CHANGELOG.md 后运行 `node tools/gen-changelog.mjs`（发布流程 §3.4 步骤 3 前执行）；**同时自动同步 README Status 徽章与「当前版本」行**（README 版本说明已收敛至 CHANGELOG，逐版表格移除） |
| `tools/page-handoff-test.cjs` | **页面交接（转场）回归门禁**（2026-09-11 立） | 桌面 Chromium 同引擎族 + 手机视口：连切 10 次页断言「可见页面数 == 1 / 无残留交接类 / 恒可见 chrome 不被误隐藏」，并采样时间线断言**各要素同时结束**（≤2 帧）与时长落在 200ms 令牌 ±60ms。用法：先起 `chrome --headless=new --remote-debugging-port=9347`，再 `node tools/page-handoff-test.cjs`（退出码 0=全过）。**改 `ext/luzzy-ext.js` 的交接控制器或 `luzzy-theme.css` 的`.lsp-view-*` 规则后必跑** |
| `tools/stream-render-test.cjs` | **流式增量渲染回归门禁**（2026-09-11 立，patch 042 的守卫） | 桌面 Chromium 里加载真实前端 + 注入合成历史，逐 tick 比对「增量渲染的 DOM」与「应用全量渲染的 DOM」是否**逐节点等价**（含围栏内空行 / 松散列表 / 引用跨空行等硬形态），并断言前缀确实推进、后段成本 ≤ 基线 0.75×。**自带负控 A8**：把「提交前等价证明」拿掉的朴素增量必须被判红（实测 118 tick 中 46 tick 不等价）。用法同 page-handoff-test（`node tools/stream-render-test.cjs`，退出码 0=全过）。**改 `ext/luzzy-stream.js`、流式分支模板或 `renderMarkdown` 语义后必跑** |
| `tools/model-list-test.cjs` | **模型列表合并回归门禁**（2026-09-11 立，patch 043 的守卫） | 桌面 Chromium 里注入「带手动模型的供应商」+ 桩掉 `/models`：断言①冷启动（重载）且**断网**时手动模型仍可见、②显示名（label）/上下文保留、③**同 id 时手动条目优先**（不被检测结果顶掉）、④用户没配过的检测结果仍可选、⑤无 JS 异常。用法同上。**改 `fetchModelsForProvider` / `providerModels` / `availableModels` / 供应商保存路径后必跑** |
| `tools/upstream-fingerprints.txt` | 上游文件 SHA-256 基线 | 同步后更新 |
| `docs/PLAN-v1.4.0.md` | 最近版本（v1.4.0）实施计划 | 最新版本主文档；历史 PLAN（v1.0.0~v1.2.1）并存备查 |
| `docs/PLAN-v1.5.0-assistant.md` | 历史存档（助手已移除） | 仅 §18（上游同步 1.9.3 调查）仍有效；§2-§17 已作废 |
| `docs/RESEARCH-assistant-native-agent.md` · `docs/design/AUDIT-assistant-ui-parity.md` | 历史存档（助手已移除） | **不指导新工作**；重启同类功能须重走调研 + 硬性规定 9 设计门 |
| `docs/HANDOFF-v1.5.0-execution.md` | 给执行 Agent 的交接提示词 | 派发任务时整段复制；含必读清单、红线、UI 设计 SKILL 门、验收标准 |
| `docs/RELEASE-KEY.md` | 签名密钥说明（口令/别名/有效期） | **已 gitignore，仅本地**；密钥库与口令严禁入库（§3.4） |
| `docs/design/` | 设计存档（spec-v2 合同 / boards-v2 三方向板 / direction-approved-v2 / 验证截图） | 设计演进按硬性规定 9 流程 |
| `docs/WORKLOG.md` | 工作日志 | 每次会话追加「日期 / 完成 / 决策 / 遗留 / 下一步」 |
| `docs/archive/` | 归档（旧工程备份等） | gitignore，仅本地 |
| `rp-hub-reference/` | 上游参考克隆 | 保留 upstream remote；**只读参考，不直接改** |

---

## 2. 硬性规定速览（完整版见 HARD_REQUIREMENTS.md）

| # | 规定 | 一句话 |
|---|------|--------|
| 1 | NSFW 协议不可触碰 | `built-in-content.js` 内 nsfw_rules 原样保留；禁止审查/过滤/改写逻辑 |
| 2 | 上游文件最小改动 | 上游文件仅允许登记 patch 修改，禁止裸改 |
| 3 | 扩展层隔离 | 二创新功能必须落 `assets/ext/` 独立文件 |
| 4 | 字体锁定 | RP-Hub 规定字体栈；Lora 本地打包，禁 CDN |
| 5 | CHANGELOG 同步 | 版本更新必须同步 CHANGELOG 与 README |
| 6 | 上游同步纪律 | 同步后必须实测数据兼容与核心功能回归 |
| 7 | 工作区整洁 | 清理冗余，docs 分类归档 |
| 8 | 发布流程 | 编译 → 推送 → Release（仅稳定版附 APK）；**只出一个 APK，且每次发布必须保持同一应用签名** |
| 9 | **设计 SKILL 强制条款** | 凡涉及 UI 设计 / 前端设计 / 主题 / 视觉 / 动效 / 交互 / 转场 / 页面设计等内容，**必须先完整阅读并应用以下 4 项设计 SKILL 才可继续讨论、计划、工作**（见 §2.1） |
| 10 | **改动标记与同步适配** | 上游文件内二创改动必须带 `[LuzzyRP patch NNN]` 标记注释；重放通道唯一（apply-patches.ps1）；同步后 verify-markers.ps1 全绿才算完成（见 §4） |

### 2.1 设计 SKILL 强制条款（硬性规定 9 的展开）

**触发条件**：任何涉及 UI 设计 / 前端设计 / 主题方案 / 视觉风格 / 交互动画 / 转场动画 / 页面布局 / 组件样式 / 字体排版 / 色彩体系的工作——**包括讨论、计划、实施三个阶段**。触发后，**必须先完整阅读以下 4 项 SKILL 的本地存档，才可继续任何设计相关工作**。

**4 项 SKILL 本地存档**（`docs/skills/`，随仓库分发）：

| # | SKILL | 本地路径 | 核心方法论 | 本项目应用方式 |
|---|-------|---------|-----------|---------------|
| 1 | huashu-design | `docs/skills/huashu-design/`（主文档 `SKILL.md`） | 工作室多角色设计方法论（艺术总监→视觉→动效→工程）；**三方向硬门**（任何新视觉设计必须先出 3 个差异化方向给用户选）；反 AI slop 清单；动效=物理学（缓动表达重量与摩擦）；`references/animation-pitfalls.md` 动效避坑 | 主题方案必须先出 3 个方向给用户选；动效设计对照 pitfalls 清单 |
| 2 | awesome-design-md | `docs/skills/awesome-design-md-main/`（73 份真实站点 DESIGN.md 范本库，`design-md/` 目录） | DESIGN.md 是设计真源文档格式（Google Stitch 概念）：Colors / Typography / Layout / Elevation / Shapes / Components / Motion 结构 | 撰写/演进本项目 DESIGN.md 时参照其结构 |
| 3 | open-design | `docs/skills/open-design/`（主文档 `AGENTS.md` + `CLAUDE.md`） | DESIGN.md 作为品牌契约（仓库根 `DESIGN.md` 为唯一设计真源，所有 UI 改动必须遵循）；工件优先；交付前五维 critique 门控；UI 动画哲学（ease-out `cubic-bezier(0.23,1,0.32,1)`、进入 200ms/退出 140ms、禁 scale(0)） | 仓库根 DESIGN.md 作为唯一设计真源；UI 改动必须遵循；交付前五维 critique |
| 4 | ui-ux-pro-max-skill | `docs/skills/ui-ux-pro-max-skill/`（主文档 `CLAUDE.md` + `.claude/skills/ui-ux-pro-max/SKILL.md`） | 可检索设计智能（styles/palettes/UX 规则/图标/字体配对）；`search.py` 检索命令；`data/stacks/jetpack-compose.csv` 等栈规约 | 设计检索用 `python docs/skills/ui-ux-pro-max-skill/.claude/skills/ui-ux-pro-max/scripts/search.py "<query>" --domain <domain>`；交付前对照 pro-rules 清单 |

**强制流程**（触发后按序执行，缺一步不得进入设计工作）：

1. **阅读**：完整阅读上述 4 项 SKILL 的主文档（huashu-design 的 `SKILL.md`、open-design 的 `AGENTS.md`、ui-ux-pro-max 的 `CLAUDE.md` + `SKILL.md`、awesome-design-md 的 `README.md`）；
2. **三方向硬门**（huashu-design 强制）：任何新视觉设计（主题方案、页面设计等）必须先产出 **3 个差异化方向**（含真实视觉初稿）给用户选择，用户选定后才进入执行；用户指定风格也不豁免（风格词收窄解释空间，不转移选择权）；
3. **设计真源**（open-design 强制）：设计决策写入仓库根 `DESIGN.md`（唯一设计真源），所有 UI 改动必须遵循；
4. **动效纪律**（huashu-design + open-design）：动效=物理学；进入 200ms / 退出 140ms / ease-out `cubic-bezier(0.23,1,0.32,1)`；禁 `scale(0)` 起步；对照 `animation-pitfalls.md`；
5. **交付门控**（open-design 五维 critique + ui-ux-pro-max pro-rules）：交付前执行五维 critique（方向/品牌/层级/动效/工程）与 pro-rules 清单逐项对照。

**豁免**：非设计的机械操作（修 bug、纯文字改动、数据迁移、构建配置）不触发；但任何视觉产出（哪怕一行 CSS 颜色改动）都触发。

**与硬性规定 4（字体锁定）的关系**：字体锁定是上游合规约束（RP-Hub 字体栈 + Lora 本地化），设计 SKILL 条款是设计质量约束；冲突时以更严格者为准（即：字体选择必须同时满足上游合规与设计质量）。

---

## 3. 工作流程

### 3.1 新任务接手（每次会话必做）

1. 读 `HARD_REQUIREMENTS.md`（10 条）与 `AGENTS.md`（本文件）；
2. 读 `docs/WORKLOG.md` 末尾，了解上次会话状态与遗留项；
3. 读 `CHANGELOG.md` 顶部，确认当前版本与上游基线；
4. 检查 `tools/upstream-fingerprints.txt` 与当前 `assets/rphub/` 是否一致（确认无未登记改动）；
5. 任务开始前在 WORKLOG 追加「开始」记录。

### 3.2 开发新功能（扩展层）

```
1. 判断功能归属：
   ├─ 前端逻辑 → assets/ext/luzzy-ext.js（或新独立文件）
   ├─ 样式覆盖 → assets/ext/luzzy-theme.css
   ├─ 原生能力 → app/.../web/LuzzyBridge.kt + luzzy-bridge.js 封装
   └─ 需要动上游文件 → 先评估：能否用扩展层实现？不能才走 patch（§4）
2. 实现 + 自测（真机或模拟器）
3. 更新 CHANGELOG（新增/优化分类）
4. 更新 WORKLOG
```

**禁止**：把新功能写进上游文件；复制上游代码到扩展层后修改（应走 patch 或 fork 决策）。

### 3.3 修复缺陷

1. 定位缺陷归属：上游 bug（同步上游修复 / 登记 patch）还是壳/扩展层 bug（直接修）；
2. 上游 bug 且上游已修复 → 走同步流程（§4）；
3. 上游 bug 且上游未修复 → 评估：登记 patch 临时修复（同步时可能冲突，需登记）或接受；
4. 壳/扩展层 bug → 直接修，补 CHANGELOG「修复」分类。

### 3.4 发布新版本

> **两条长期纪律（2026-09-08 用户指示，2026-09-09 重申为长期纪律，后续版本一律遵循）**：
> ① **只构建/发布一个 APK**；② **每次发布必须保持同一个应用签名**（自 LuzzyRP 已发布的
> 最新 Release 起沿用同一密钥库，永不更换）。

```
1. 更新 build.gradle.kts versionCode/versionName
2. 更新 CHANGELOG.md（格式见 §1.1）
3. 运行 `node tools/gen-changelog.mjs`（自动同步 README Status 徽章与「当前版本」行；版本说明一律写 CHANGELOG，README 不维护逐版表格）
4. ./gradlew assembleRelease（**只产出一个 APK**：app/build/outputs/apk/release/app-release.apk；
   ABI 拆分保持关闭——splits.abi 块已注释，禁止恢复多包产出）
5. 签名一致性自检（**硬性门**）：apksigner verify --print-certs 该 APK，确认
   CN=LuzzyRP 且证书 SHA-256 指纹与上一版一致；不一致 = 停止发布，排查是否误用了
   debug 签名 / 新建密钥库（keystore.properties 缺失时会回退 debug 签名）
6. 真机回归（核心功能 + 本次变更点）
7. git push
8. GitHub Release（按旧版排版；仅稳定版附 APK，且**只附步骤 4 那一个文件**）
```

**签名纪律（为什么不能换签名）**：安卓只允许「同包名 + 同签名」的 APK 覆盖安装；
换签名后老用户必须卸载重装，应用内数据（localStorage / IndexedDB）随之清空。
因此 `keystore/luzzy-release.keystore` 与 `keystore.properties` 必须**离线备份**
（两者均已 gitignore、不入库），口令见本地 `docs/RELEASE-KEY.md`（同样不入库）。
密钥库丢失 = 该应用 ID 永远无法再发升级包。

### 3.5 更换应用图标 SOP（2026-09-04 定稿，用户指示：以后换图标都按此执行）

> 首次执行样本：会话 19（v1.2.2，源图 `docs/design/brand-logo-v2-source.png`，
> 设计方向落档 `docs/design/icon-v2-directions.md`）。触发词：「换图标/换 icon/
> 换 LOGO」——**画作资源必须由用户提供新图并明确指示**，Agent 不得自行生成替代。

```
1. 源图入库
   - 用户提供新图（AI 生图等），复制入 docs/design/brand-logo-vN-source.png
     （N=序号；用户原件只读，绝不改动/移动）；
   - 源图约束（给用户的生图要求，提示词模板见 icon-v2-directions.md）：
     纯 1:1 满幅不透明、主体头肩居中占 55-65%、四周 ~15% 安全边距
     （圆形裁切下耳朵/配饰完整）、无文字、≥1024px。

2. 设计合规（硬性规定 9）
   - 新视觉设计 → 复读 4 项 SKILL 主文档；新方向出 3 方向差异化提示词
     （落档 docs/design/icon-vN-directions.md），用户选定/自备生图后继续；

3. 验收门（落地前必须过）
   - 圆形蒙版预览 + 96/48px 阶梯缩略图自检（合成一张
     docs/design/brand-logo-vN-preview.png 供肉眼确认）；
   - 耳朵/配饰在圆内有余量、48px 下可辨；不过 → 回用户重生成；

4. Pillow 产出全套（脚本模式参考：4x 超采样蒙版、LANCZOS、PNG optimize）
   - mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher.png：48/72/96/144/192（满幅）；
   - 同目录 ic_launcher_round.png：同尺寸圆形 alpha 裁切；
   - 同目录 ic_launcher_foreground.png：108dp 网格 108/162/216/324/432，
     全图 68% 居中 + 画布填边缘均值色；
   - drawable-nodpi/luzzy_logo.png 与 app/src/main/assets/ext/luzzy-logo.png
     （各 192，关于页/README 头图引用）；
   - 边缘均值取色（边框 4px 环采样）→ 写入 values/colors.xml 的
     ic_launcher_background；

5. adaptive 方案约定：全图前景 68% 居中 + 同色纯背景（不透明图标下不再使用
   透明贴纸方案）；mipmap-anydpi-v26/*.xml 结构不变；

6. 资产变更由 assetSignature 自动触发重解压（v1.2.3 起，无需手动 bump；见 §7 坑表）；

7. 文档同步（硬性规定 5）：AGENTS §1.2 图标条目（替换日期/源图路径/背景色）、
   CHANGELOG（当前开发版本加「全新品牌图标」条目）、README（头图自动跟随；
   随发版时同步徽章/规划表）、WORKLOG 登记；随发版时 gen-changelog.mjs 重跑；

8. 若随版本发布：按 §3.4 全流程；若独立热替换：仅 6+7 + debug 安装自检。
```

**红线**：①画作资源替换必须由用户提供新图并明确指示（2026-09-04 决策）；
②styles.css/rphub 上游文件与本流程无关，永不触碰；③旧图标经 git 历史可回滚，
替换前确认用户不再需要旧版。

---

## 4. 上游同步 SOP（**已退役**）

> **⚠️ 已于 2026-09-12 退役（用户拍板）**：上游 RP-Hub 同步**不再执行**，基线定格
> **1.9.3**（commit `4aef0bb`）。任何新任务**不再发起**同步流程。本节正文（同步流程 /
> patch 001-052 表 / 冲突处理）已于 2026-09-16 删除，不再指导任何工作。

**保留不删、需要时再翻的**（历史追溯与回滚依据）：

| 内容 | 位置 |
|---|---|
| 每个 patch 的目的 / 硬性规定对应项 / 预期冲突点（001-052 **权威登记**） | `tools/patches/README.md` |
| 实体段前像 blob id 与当前基线 commit | 同上「末节」 |
| 三脚本 `sync-upstream.ps1` / `apply-patches.ps1` / `verify-markers.ps1` | `tools/` |
| 上游参考克隆 | `rp-hub-reference/` |

**仍然有效的两条**（与同步是否退役无关）：

1. **硬性规定 1**：`built-in-content.js` 内 `nsfw_rules` 永远不可触碰；
2. **上游改动登记纪律**：二创改动必须落在 `tools/patches/`，禁止裸改上游文件。
   v3.0 起 `assets/rphub/**` 已整体删除，**本约束当前无适用对象**。

> **门禁现状**：`verify-markers.ps1` 现存 142 条 FAIL（全部 `文件不存在`）——校验对象
> `assets/rphub/**` 已随 P6 退役删除，属历史遗留；与 CHANGELOG 相关的
> `R3-changelog-sync` 一条**仍为绿**。取证见 `docs/WORKLOG.md` 会话 88 §六。

---

## 5. 扩展开发规范

### 5.1 luzzy-bridge.js 封装模式

```js
// 所有桥接调用必须走存在性检测 + 降级
const Luzzy = window.Luzzy || {};
Luzzy.copyToClipboard = function (text) {
    if (window.LuzzyBridge && window.LuzzyBridge.copyToClipboard) {
        window.LuzzyBridge.copyToClipboard(text);
        return true;
    }
    // 降级：navigator.clipboard
    return false;
};
```

### 5.2 luzzy-theme.css 覆盖模式

```css
/* 只覆盖 CSS 变量与追加规则，禁止修改上游 styles.css */
:root {
    --app-font-family: var(--app-font-modern); /* 保持上游语义 */
}
```

### 5.3 luzzy-ext.js 挂载时机

- 在 index.html 尾部（patch 005 挂载点）加载，此时上游全局对象（Vue app、RPHub 等）已就绪；
- 访问上游内部对象时先做存在性检测，上游重构导致 API 变化时降级为「功能不可用」而非报错；
- 扩展功能必须自带降级路径，**不允许**因扩展层报错导致整个应用白屏。

### 5.4 新增桥接方法流程

```
1. LuzzyBridge.kt 添加 @JavascriptInterface 方法
2. luzzy-bridge.js 添加封装（含降级）
3. luzzy-ext.js 或上游调用点使用
4. CHANGELOG 记录
```

---

## 6. 测试要求

### 6.1 每次变更后必测

> **设备辨别（2026-09-04 用户指示记录）**：真机=**小米，adb 序号 `df97f3c4`**
> （型号 25098PN5AC / product pandora / Android 16）；多设备并存时一律
> `adb -s df97f3c4` 显式指定。MIUI 注意：adb 安装可能弹「USB 安装」确认框需用户在手机上点确认；
> 锁屏状态下 `am start` 可能不置前，先解锁（上滑）再操作。
>
> **真机体验包纪律（2026-09-10 用户指示，取代原「日常包 = debug」记录）**：
> ① 该机日常使用包 = **release 签名包 `com.luzzymeow.luzzyrp`**（与最终分发件**同物**）；
> ② **不再安装 debug 包**（原 debug 包已卸载、数据随之清空）；真机装新版 = `./gradlew
> :app:assembleRelease` → `adb -s df97f3c4 install -r app/build/outputs/apk/release/app-release.apk`
> （同签名同包名覆盖安装，**数据保留**）；
> ③ **每个版本发布前，由用户先体验该 release APK 作最后一道人工真机测试**，通过后才 push + Release；
> ④ release 构建已**显式开启 WebView 内容调试**（`WebViewSetup` 内 `setWebContentsDebuggingEnabled(true)`），
> 以便真机用 CDP 做帧率/布局/脚本耗时的定量排查——代价是连 adb 的电脑可检查页面内容，本应用仅侧载分发，接受该代价；
> ⑤ 发布前仍须 `apksigner verify --print-certs` 核对指纹（§3.4 步骤 5）。
>
> **真机 CDP 快速上手法**：`adb -s df97f3c4 shell "cat /proc/net/unix | grep webview_devtools"`
> 取 socket 名（内含 pid）→ `adb -s df97f3c4 forward tcp:9222 localabstract:<socket>` →
> `http://127.0.0.1:9222/json` 取 `webSocketDebuggerUrl`。
> 帧率量化：`adb shell dumpsys gfxinfo com.luzzymeow.luzzyrp reset` → 触发交互 →
> `dumpsys gfxinfo com.luzzymeow.luzzyrp`（看 Janky frames / 分位 / GPU 直方图）。
> **注意**：① 本仓库脚本里的 `MSYS_NO_PATHCONV=1 <cmd>` 是 Git Bash 写法，**PowerShell 下不生效**
> （会被当成命令名报错）；② `$PID` 是 PowerShell 只读自动变量，勿用作变量名。


- 冷启动 / 热启动 / 后台恢复；
- 对话全流程（配置 API Key → 发送 → 渲染 → 分支）；
- 数据持久化（杀进程重启数据保留）。

### 6.2 上游同步后必测（~~硬性规定 6~~ **同步已退役**）

> 本节随上游同步一并退役（§4），保留仅为历史。若将来恢复同步，按此清单回归。

- 数据兼容：老 localStorage 数据可读；
- 核心功能：对话 / 角色卡导入导出 / 世界书 / 正则 / 记忆 / 生图；
- 断网可用性（飞行模式走查）；
- 扩展层功能回归（luzzy-ext.js 全部功能）。

### 6.3 发版前必测

- **单包与签名**：`app/build/outputs/apk/release/` 下**只有一个** `app-release.apk`；
  `apksigner verify --print-certs` 指纹与上一版一致（§3.4 步骤 4-5）；
- 真机矩阵（至少 2 台不同厂商设备，覆盖 WebView 差异）；
- 大文件 / 长会话性能走查；
- 角色卡 PNG/JSON 导入导出全流程（SAF）。

---

## 7. 常见坑与红线

| 坑 | 说明 |
|----|------|
| 加载 android_asset 路径 | localStorage 不可靠，**必须**解压到 filesDir 后加载 |
| 裸改上游大文件 | app.js 512KB 单文件，同步时产生无法手工解决的巨型冲突 |
| 扩展层报错白屏 | 扩展功能必须降级，禁止影响上游主流程 |
| 忘记 patch 登记 | 未登记的裸改 = 同步噩梦，视为违规 |
| 触碰 nsfw_rules | 硬性规定 1，任何审查/过滤/改写逻辑都是不合格交付 |
| 运行时依赖 Google Fonts | 硬性规定 4，Lora 必须本地打包 |
| 上架应用商店 | 12 岁条款合规风险，**仅侧载分发** |
| 删除上游 LICENSE | 二创署名义务，禁止删除/改写 |
| **换签名 = 老用户装不上**（2026-09-09 立为纪律） | 同包名升级要求签名一致；换密钥库 / `keystore.properties` 缺失回退 debug 签名，都会让老用户 `INSTALL_FAILED_UPDATE_INCOMPATIBLE`（只能卸载重装、数据清空）。发布前必跑 `apksigner verify --print-certs`（§3.4 步骤 5） |
| **多包产出**（2026-09-09 立为纪律） | ABI 拆分已关闭且禁止恢复；release 只出一个 `app-release.apk`。误开 `splits.abi` 会产出三件套，导致「发哪个包」混乱与 Release 资产冗余 |
| **混用 debug / release 包**（2026-09-10 口径变更） | 两包名不同、数据不互通。**现行纪律：真机只用 release 签名包**（与分发件同物），debug 包不再安装——见 §6.1「真机体验包纪律」。切勿再构建/安装 debug 包作日常使用：会生成第二个空数据 LuzzyRP 造成数据分裂 |
| **PowerShell 里照抄 Git Bash 的 `MSYS_NO_PATHCONV=1 <cmd>`**（会话 48 实证） | 该前缀是 Git Bash 语法，PowerShell 会把它当命令名报 `CommandNotFoundException`；PowerShell 本就无路径转换问题，直接写 `adb shell ...` 即可 |
| **小样本掉帧对比不可信**（会话 48 实证） | 真机帧率受热/后台影响极大：同一变体三轮测出 14 / 5 / 10 掉帧。**必须成对交替测量（A/B/A/B 紧邻交替）**才算数——曾因此差点把 `contain:paint` 的噪声（14→5）当成 −64% 收益采纳，配对复测反向（无 17 / 有 26） |
| **模拟器卡到 250ms/帧时，先查 AVD 的 `hw.ramSize`，别先怀疑应用**（会话 71 实证） | AVD `LuzzyRP_Test` 原配 **1536M** RAM，在 1080×2400/420dpi 下长期换页，表现是「滚动巨卡」——**连系统设置页也是 200ms 中位帧 / 72% 掉帧**（拿系统应用当基线即可判定不是应用的问题）。提到 **4096M** 后同一输入下：系统设置 17ms/2.2%、聊天页 17ms/2.6%，**整套仪器化测试耗时也从 2m40s~3m28s 降到 54s**，之前几次「偶发红」也随之消失。教训两条：① **卡顿先做「系统应用基线对照」**再决定查谁；② 仪器化测试莫名超时/偶发失败时，先看模拟器资源，别急着改代码。原值已备份在 `config.ini.bak-perf` |
| **这台机器的模拟器用不了 GPU 加速**（会话 71 实证） | AVD 配置 `hw.gpu.enabled = no` / `mode = auto` 是**唯一能启动**的组合：`-gpu host` 会刷屏 `Failed to find ColorBuffer` 且永远起不来；`-gpu angle_indirect` 报 `Failed to load opengl32sw` 后崩溃。所以帧率数字要当**相对值**看（同一个模拟器内的 A/B 有意义，绝对值不代表真机）。**真机帧率才是用户感知的那一档**（AGENTS §6.1 的真机 CDP + `dumpsys gfxinfo` 流程） |
| **Git Bash 里 `cmd //c start` 起 GUI 后台进程会吞掉 title 参数**（会话 72 实证） | `cmd //c start "标题" "C:\path\app.exe" -args` 被拼坏成「找不到文件 <标题>」（模拟器起不来）。起**独立进程**（不随 shell 退出而死）的正解是 PowerShell：`powershell -NoProfile -Command "Start-Process -FilePath '…\emulator.exe' -ArgumentList '-avd','X','-no-snapshot-load' -WindowStyle Normal"`；改用 Bash 工具的 `run_in_background` 也能跑，但 detached 更稳。另：AVD 锁文件（`multiinstance.lock` / `hardware-qemu.ini.lock`）留在原地会让下次启动报「Running multiple emulators with the same AVD」，起前先删 |
| **Tailwind CDN 不接受 var() 颜色值** | ~~已证伪~~：JIT 接受纯 var()，但见下一行真正的坑 |
| **主题色板必须用 RGB 三元组 + `<alpha-value>`** | 纯 `var()` 色值下基本工具类正常，但带透明度修饰符的类（`bg-gray-50/60` 等）会**静默回退纯白**（暗色白块根因，不报错难排查）。正确写法：config 用 `rgb(var(--tw-gray-50) / <alpha-value>)` + 变量存三元组如 `250 249 245`（2026-09-01 jsdom+CDP 双实证，见 §9） |
| **~~改 assets 不 bump EXTRACT_VERSION = 白改~~（v1.2.3 已根治）** | 构建期 assetSignature（文件数+大小+mtime）注入 BuildConfig.ASSET_SIGNATURE，AssetExtractor 启动比对签名自动重解压——改资产零手动操作；若签名粒度漏检（同 mtime/size 改写）仍可手动 bump 兜底 |
| **`git apply` 在仓库内按「仓库根」解析 patch 路径**（会话 25 实证） | 从嵌套目录执行时路径不匹配会**静默跳过**（返回 0 且不改文件）——「返回 0 但行未插入」的假象来源。验证/重放脚本一律在**仓库外**目录执行，或从仓库根配 `--directory=<仓库相对路径>` |
| **PowerShell `$ErrorActionPreference='Stop'` 下原生命令 stderr 会中断脚本**（会话 25 实证） | `git apply` 的 trailing-whitespace 告警写 stderr 即触发终止错误（表现为脚本跑到第 N 条莫名中断）。调用原生工具前临时置 `'Continue'` 并把 stderr 落盘，仅在退出码非 0 时读取 |
| **接手「被中断的在途改动」时，文档描述的状态不可信**（会话 51 实证） | 上一手在 21:16–21:22 被中断，工作区留下 8 文件改动 + 3 未跟踪文件，其中 **① 删组件时误删仍在使用的 import（`ChatTopBar` 的 `Column`）② 规格测试仍断言已删除的符号（`Ledger.CollapseDurationMs`）**——两处当时都不可编译，而文档只说「未跑单测/未提交」。**接手第一件事是编译 + 单测判定断点**（`git status` 看改动面、`grep` 查被删符号的悬空引用），别照文档猜进度 |
| **PowerShell `Out-File -Encoding utf8` 给文本加 BOM**（会话 51 实证） | 用它写 `git commit -F` 的消息文件时，**BOM 会混进 commit subject**（`git log` 里显示为 `fix(v1.5.0)`）。改用编辑工具写消息文件（无 BOM），已提交的用 `git commit --amend -F`（**未 push 才可 amend**） |
| **扩展层认「当前页」必须用集合差分，不要用启发式猜**（会话 53 实证，真机出过 bug） | `.app-main` 里不止有页面：扩展层自己注入的 `.lsp-fab-row`（关于页置顶 FAB）是**恒可见**的兄弟节点。用「第一个可见子元素」判定 → 收尾时把 chrome 当成当前页 → 旧页的行内 `display:none` 没还原 → **聊天页永久盖在管理页上**（用户真机「切了几次就出bug」）。正解：点击前记 `before`、下一帧取 `after`，**新页 = after − before / 旧页 = before − after / 恒可见 chrome = before ∩ after**（天然排除）——不依赖类名与高度，上游以后再加常驻元素也不受影响。回归门禁 `tools/page-handoff-test.cjs` |
| **headless Chrome 默认 `prefers-reduced-motion: reduce`**（会话 53 实证） | 桌面 CDP 测试里所有 CSS 动画/过渡都被压成 0.01ms——**测时间线等于测空气**（本测试第一版曾出现「50ms 内全部到位」的假绿）。必须先 `Emulation.setEmulatedMedia({features:[{name:'prefers-reduced-motion', value:'no-preference'}]})` 关掉该模拟 |
| **「共终止」要用「最后一次变化」判定，不要用阈值** | ease-out 曲线下各属性到达某阈值（如位移 90%、透明度 98%）的时刻天然不同，用阈值判「同时结束」会误判。改为采样时间线后取**每个信号最后一次变化的时刻**再互比（阈值给 2 帧 + 8ms） |
| **交接动画必须与 DOM 切换同帧应用（微任务），不能等 rAF**（会话 55 真机实证） | Vue 状态更新是微任务、渲染在其后。若在点击后等 `requestAnimationFrame` 才应用过渡层，就会出现一帧「已换页、无快照」的硬切（用户原话「怎么是先切换才有交叉淡化」；真机逐帧实测 dt=35 帧即此）。正解：捕获阶段排**两个嵌套微任务**（第一个排在 Vue flush 之前、第二个在其后），都在同帧 paint 之前生效；rAF 只作异步导航兜底 |
| **被 `v-if` 摘除的元素仍是可用快照**（会话 55） | 旧页被 Vue 摘除后元素对象仍在内存（子树完整、滚动位置还在）→ 可**直接搬进自建覆盖层**当过渡快照（不克隆、不重建）。覆盖层层级要低于侧栏（上游 `.app-sidebar` 是 `z-50` → 用 10） |
| **门禁判据必须确定性；概率性判据不用**（会话 55 实证） | 首版「帧末点击是否漏硬切帧」跑两次一红一绿（取决于浏览器是否恰好在两帧之间合成）——**概率门禁比没有更糟**。改为确定性判据：点击后只推进微任务、不给浏览器出帧机会，此时过渡类就该已生效（微任务版 true / rAF 版 false，红绿可复现）。同理「时长/曲线」**不要用 rAF 采样测**（会被机器负载饿死，与 gradle 并行时偶发红）→ 改读**声明值**：`effect.getTiming().duration` + 关键帧缓动 + `computedStyle.transitionDuration/TimingFunction`；采样时间线只作信息字段 |
| **CSS 动画的缓动在关键帧上**（会话 55 实证） | `KeyframeEffect.getTiming().easing` 对 **CSS 动画**恒为 `'linear'`（**不是实现没生效**）；真实曲线在 `effect.getKeyframes().map(k => k.easing)` 里。断言动画曲线必须读关键帧，否则得到「曲线是 linear」的假告警 |
| **模板字符串里的注释不能含反引号**（会话 55 实证） | 与「Kotlin 块注释可嵌套」同类：在 `evalJs(\`…\`)` 的模板串内写注释时，注释里出现反引号会**直接截断模板串**（报 `missing ) after argument list`，且报的行号往往不是真凶）。写进模板串的代码注释用「」或纯文字 |
| **契约文档写了、实现没接**（会话 54 静态审查实证） | `ApprovalGate` 的 KDoc 与 PLAN §12.1 都写「T2/T3 默认关闭，**需用户在设置里逐项开启**」，而实现侧**三处全缺**：内存快照无 hydration、setter 零调用点、设置页无开关 UI → **8 类工具（日历/终端/截屏/短信/通讯录/发到 RP 会话…）成为交互死路**，且不报错、不崩溃，看日志永远正常。**审查纪律**：顺着「文档承诺」逐条做**三段对账**（契约 → 调用点 → UI 入口），缺一段就是死路；只跑测试/只看有没有异常查不出这类问题 |
| **WebView 被壳暂停过后，动画时间线是冻的**（会话 57 真机实证；原样本＝已移除的助手覆盖层停绘+`pauseTimers`） | 壳若 `visibility=INVISIBLE` + `onPause()` + `pauseTimers()`，恢复后在**同一个任务里**改类/加类，CSS 过渡与动画会**直接跳到终态**（不播）。**正解：把这个「翻类」推迟到渲染帧内**（`requestAnimationFrame` 回调里再点），时间线已恢复，过渡才会真跑。**推论**：任何「壳暂停过 WebView 之后立刻触发的视觉变化」都要按这条检查 |
| **上游侧栏开合是模块状态，不是响应式绑定**（会话 57 真机实证） | `app.js` 用 `let isMobileSidebarOpen` + `setMobileSidebarOpen()` 里 `classList.toggle('mobile-sidebar-open')`。扩展层**直接摘这个类**只改 DOM、不改状态 → 下次 `toggleMobileMenu()` 把状态翻成 `false`、toggle 一个「已不存在的类」→ **第一次点汉堡没反应、第二次才开**。**正解：永远点上游自己的按钮**（`toggleMobileMenu` 的 DOM 入口），让状态机同时管类与状态 |
| **实体前像 = 上游纯净基线**（会话 25 修正） | 实体段必须先于字符串块重放（否则前像失配）；前像判定用实体头 `index <pre>` 的 LF 归一 blob id，勿用指纹表（CRLF 工作树哈希）比对覆盖态 |
| **Kotlin 块注释可嵌套**（会话 31 实踩） | KDoc 里写路径 `skills/*.md` 时，`/*` 会**开启嵌套注释**，导致后续代码被吞、报 `Unclosed comment`。写注释时避免裸 `/*`（改用 `skills/…md` 或转义） |
| **Android 无 `Process.toHandle()/ProcessHandle`**（会话 29 实证） | 无法枚举孙进程；`sh -c "sleep 30"` 只杀 shell 时，若用阻塞 `readText()` 排空会一直等到孙进程结束（超时形同失效）。改用**非阻塞 `available()` 轮询 + 有界排空** |
| **AGP 会解压 `.gz` 资产并去掉后缀**（会话 37 实证） | 源码树 `assets/**/rootfs.tar.gz` 在 APK 内变成 `rootfs.tar`（未压缩 tar，扩展名被去掉）。运行时读资产要**两种名字都试 + 按 magic bytes 判断**，否则真机「资产缺失」 |
| **「DOM 里有文本」不等于「用户看得见」**（会话 59 真机实证） | 选择器行改成「label 为主文本」后，数据层断言全绿、DOM 里 `textContent` 也有 label，**用户却仍然看不见** —— 行内固定件（徽标 42 + 裸 ID 131 + chip 121 + 间距 24 = 318px）已超过行宽 292px，label 作为唯一可收缩项被 flex 压成 **`clientWidth = 0`**（不报错、不告警）。**凡新增「要显示的字段」，断言必须量 `getBoundingClientRect().width > 0` 且 `scrollWidth ≤ clientWidth`（未被省略号截断），不能只看 textContent**；回归门禁 `tools/model-list-test.cjs` A7/A8 即此判据 |
| **采样 profiler 的「自耗时」排名在真机上会失真**（会话 59 实证） | 真机把 88% 自耗时归给 Vue 的 `setStyle`（每次状态变更仅 37 次调用），而微基准显示单次样式写入 0.006ms —— 两者差 800 倍。**结论：不要只信 profiler 排名**，要用「微基准 + 三臂对拍（含空实现对照）+ 调用计数 + 分段计时」交叉验证；`dumpsys gfxinfo` 对 WebView 只能作旁证（内容在渲染进程绘制），有区分力的是**页面内 rAF 间隔 + 一次根状态变更的主线程耗时** |
| **CDP 测试脚本崩溃会把状态留在真机上**（会话 59 实证） | 一次探针在 teardown 前抛错，真机上留下一条合成消息且 `isGenerating` 卡在 `true`（表现是一直「生成中」）。**任何真机脚本都要把「收尾」写成独立的一次调用**（先清理再断言），跑完再**显式核对**会话条数 / 生成标志 / 指令注册表等被改动过的状态 |
| **真机上写数据要极度克制；看到数据变化先问用户再断言因果**（会话 59 记录，含一次**错误归因**） | 会话 59 真机性能测量时用 CDP 往 `chatHistory` push/pop 合成消息、反复置位 `isGenerating`；其中一次探针在 teardown 前崩溃，把「合成消息 + `isGenerating=true`」留在真机数分钟（**这一条属实**）。随后发现该会话被重置，**当时被我写成「探针造成的血证」，后经用户澄清是他自己删的 —— 归因错了**。教训：① **真机上观察到数据变化，先向用户确认，再写结论**，不要把「时间相邻」当成「因果」；② 真机探针尽量**只读**，需要写入时先在应用内建一次性角色/会话，或在桌面桩数据上测；③ 收尾必须**独立成一次调用**（先复原再断言），每步之后核对被改动的状态；④ 不要用 `indexedDB.open(不存在的库名)` 去「探测」——那会**创建**空库（本轮误建 5 个，已删） |
| **`compileDebugAndroidTestKotlin` 通过 ≠ 仪器化用例可用**（会话 77 实证，代价两轮） | 会话 76 写了 16 条仪器化用例，只验证「源码编译通过」就记为完成；会话 77 第一次真跑**立刻抓出 3 类问题**：① `PageDataUiTest` 的 `package` 写成 `chat` 而文件在 `ui/pages/`（Kotlin 按**声明**编译 → 运行器按包路径加载 → `ClassNotFoundException`）；② 3 处断言用 `onNodeWithText` 但页面上同文案有多处（它要求**唯一匹配**，抛 `Expected at most 1 node but found N`）→ 改用 `onAllNodes(...).onFirst()`；③ 一条**产品缺陷**（见下两行）。**纪律**：仪器化用例的验收必须是 `checkChat` 真的跑出 `OK (N tests)`，`compile*Kotlin` 通过不构成验收 |
| **不要用 `-PallowAllDevices=true` 绕开模拟器门禁把测试件装到真机**（会话 77 实证） | `app/build.gradle.kts` 的 `verifyEmulatorDevice` 硬门禁是**防呆**不是障碍：它存在正是因为历史上首次误跑就把测试件装到了真机。会话 77 绕开它 → MIUI 逐次弹安装确认框（约 12 秒无人点即自动判 `Install canceled by user`，表现为「主包能装、测试件反复失败」）、包被装成半残、测试在锁屏下永久挂起，几十分钟 `0/79`。**正解**：真机只装 release 包做人工目视；仪器化一律 `ANDROID_SERIAL=emulator-5554 ./gradlew checkChat`（模拟器 80 条 / 3 分 22 秒） |
| **仪器化测试在真机锁屏时会永久挂起**（会话 77 实证：挂了 32 分钟） | `mWakefulness=Dozing` + 锁屏时 Compose UI 测试**测量不到布局**，`waitUntil` 一直等；症状是 Gradle 停在 `Tests 0/N` 且 logcat 里**没有任何 TestRunner 记录**。处置：`input keyevent KEYCODE_WAKEUP` + 上滑解锁；跑测试前可临时 `settings put global stay_on_while_plugged_in 7`（**跑完记得还原原值**）。模拟器无此问题 |
| **PowerShell 跑 `adb` 的两个坑**（会话 77 实证） | ① **管道会缓冲 adb 输出** → 「测试像没动静」；要实时进度就看 Gradle 自己的 `Tests N/M completed` 行，或 `Tee-Object` 落盘再读。② **续行符会拆坏 `am instrument` 的参数**（报 `Argument expected after "-r"`、把 `-e` 当成命令）→ 必须用数组形式 `& adb @args` |
| **Gradle 结果目录会被上次崩溃的残留文件锁住**（会话 77 实证） | 上一次崩溃留下的 `androidTest-results/.../logcat-*-crash-report.txt` 被仍在运行的 JVM 持有 → 后续 `connectedDebugAndroidTest` 一启动就 `FileSystemException: 另一个程序正在使用此文件`。处置：**先 `./gradlew --stop`** 释放句柄，**再**删 `app/build/outputs/androidTest-results`（顺序反了删不掉） |
| **`pinToBottom()` 不能在测量帧里发滚动**（会话 77 实证，产品缺陷） | 该函数被 `runTurn` 的流式事件收集器调用（`ChatPage.kt:752`）；测试环境里这个收集器由 `ApplyingContinuationInterceptor` **在 `measureAndLayout` 内部**恢复 → `scrollToItem` 内部的 `forceRemeasure()` 重入 → `performMeasureAndLayout called during measure layout`。**症状会伪装成别的东西**：`ChatPersistenceTest.sendingAppendsARowToStorage` 表现为「8 秒超时」（超时只是表象，崩溃才是根因）。修法：滚动前 `withFrameNanos { }` 推迟到下一帧 |
| **`debug` 包点图标进的是 WebView 老界面，不是 Compose 界面**（会话 77 用户误判为「包是旧版」） | `AndroidManifest` 的 `launchable-activity` 至今是 `com.luzzymeow.luzzyrp.MainActivity`（WebView，加载上游 RP-Hub 网页界面）；新的 Compose 界面在 `ui.ComposeActivity`，**只能用 `am start` 进**。**这与包的新旧无关**（会话 77 已用 SHA-256 核对设备 APK 与本地新构建逐字节一致）。切 launcher 属 **P6**。**判断包新旧要看哈希，不要看界面长相** |
| **纯 `waitUntil` 自旋会饿死帧（被测内容是异步取数时）**（会话 78 实证，两条面板用例交替红） | 面板内容由 `LaunchedEffect` 读库 → setState，其协程续体恢复挂在**帧回调**上；`waitUntil` 自旋期间**不产出帧** → 续体永不恢复 → 条件永不成立（实测自旋 8s 仍停在「读取中…」，而同一份代码先推帧则 200ms 内绿）。**正解 = 「推帧 → 查 → 让出真实时间」轮询**：`compose.waitForIdle()` + 条件 + `Thread.sleep(50)`，三者缺一不可（只推帧则后台查询未回，只等待则续体不恢复）。同族于 `CHAT-REGRESSION.md` §4 的「`Thread.sleep` 饿死帧」。判据要确定性：**交替红的用例就是判据不稳，必须改成确定性写法** |
| **`onRoot()` 在 BottomSheet / Dialog 打开后会抛**（会话 78 实证） | 面板是**独立窗口**（第二个 root），`compose.onRoot()` 要求唯一匹配 → `Expected exactly '1' node but found '2' nodes that satisfy: (isRoot)`。**诊断 dump 必须逐 root**：`compose.onAllNodes(isRoot())[i].printToString(...)`，且整段用 `runCatching` 包住 —— 否则诊断代码自己变成新的失败源（本会话踩过一次：本想 dump 现场，结果报错盖掉了真正的断言失败） |
| **模拟器长跑会劣化，表现为随机的生命周期断言失败 + 漏跑用例**（会话 78 再次实证） | 多次装卸 APK 后出现 `Activity never becomes requested state "[DESTROYED]" (last lifecycle transition = "PAUSED")`、整套只跑 **51/80** 条、失败用例随机换。**处置：冷启动模拟器**（`adb emu kill` → 清 `*.lock` → `Start-Process emulator`），同一份代码即 **80/80 全绿**。**别改代码去迎合劣化环境的红**。另：`adb install` 后务必确认装的是**新构建**的测试件（曾跑到上一版、拿到假红） |

---

## 9. 版本状态

> 本节不再维护逐版快照（历史上曾存 v1.5.0/v1.4.0/v1.3.0/v1.2.2 四份，2026-09-16 移除）。
> **版本与各版说明以 `CHANGELOG.md` 为准**（README「当前版本」行自动同步，应用内「关于」页同源）；
> 过程记录见 `docs/WORKLOG.md`；长期纪律见 §3.4（单 APK + 固定签名）与 §6.1（真机用 release 包）。
