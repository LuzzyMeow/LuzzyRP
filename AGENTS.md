# AGENTS.md · LuzzyRP 开发 Agent 指南

> 本文件是**后续开发 / 更新 / 维护 Agent 的强制工作指南**。
> 任何 Agent 接手本仓库任务前，必须完整阅读本文件与 [`HARD_REQUIREMENTS.md`](HARD_REQUIREMENTS.md)，并遵守其中的全部纪律。
> 违反硬性规定任何一条即为不合格交付。

---

## 0. 项目一句话

LuzzyRP = **RP-Hub（上游，纯前端）** + **原生 WebView 壳（Kotlin）** + **独立扩展层（JS/CSS）**。二创只优化前端、不碰后端，可长期同步上游。

---

## 1. 文件地图

### 1.1 仓库根

| 路径 | 作用 | 维护者注意 |
|------|------|-----------|
| `README.md` | 项目门面（含二创署名声明） | 版本更新时同步（硬性规定 5） |
| `CHANGELOG.md` | 更新日志 | 格式：`### vX.Y.Z — 标题` + 「新增/优化/修复/注意事项」分类 + 构建结果与 versionCode；每条注明上游基线版本 |
| `HARD_REQUIREMENTS.md` | 10 条硬性规定（最高约束） | 修改需在 CHANGELOG 声明 |
| `AGENTS.md` | 本文件 | 与 HARD_REQUIREMENTS 同步演进 |
| `DESIGN.md` | 设计真源（唯一设计契约，Claude token 体系） | 任何 UI 改动必须遵循；修改需按硬性规定 9 走设计流程 |
| `LICENSE` | CC BY-NC 4.0 | **禁止删除/改写**（含上游 LICENSE 保留义务） |
| `keystore.properties` | 签名配置 | 不入库（.gitignore） |
| `settings.gradle.kts` / `build.gradle.kts` / `gradle.properties` | 构建配置 | 仅 `:app` 单模块 |

### 1.2 壳工程（app/）

| 路径 | 作用 | 维护者注意 |
|------|------|-----------|
| `app/src/main/java/com/luzzymeow/luzzyrp/MainActivity.kt` | 单 Activity，WebView 宿主 | 加载 `filesDir/rphub/index.html`（**不是** android_asset，localStorage 依赖可写路径） |
| `app/src/main/java/com/luzzymeow/luzzyrp/web/WebViewSetup.kt` | WebView 配置 | JS 开关 / DOM storage / 缓存策略 / 混合内容 |
| `app/src/main/java/com/luzzymeow/luzzyrp/web/LuzzyBridge.kt` | JSBridge 原生实现 | 所有 `@JavascriptInterface` 方法集中于此；新增桥接方法必须同步 `assets/ext/luzzy-bridge.js` 封装 |
| `app/src/main/java/com/luzzymeow/luzzyrp/web/FileChooserHandler.kt` | 文件选择（角色卡导入） | `onShowFileChooser` + SAF |
| `app/src/main/java/com/luzzymeow/luzzyrp/web/DownloadHandler.kt` | 文件导出 | `DownloadListener` + SAF 保存 |
| `app/src/main/java/com/luzzymeow/luzzyrp/util/AssetExtractor.kt` | assets 解压到 filesDir | 首次启动幂等执行；版本升级时按版本号增量更新 |
| `app/src/main/res/` | 图标资源 | mipmap 全套 + `drawable-nodpi/luzzy_logo.png`。**2026-09-04 用户以 AI 生图新 LOGO 全面替换**（纯 1:1 满幅不透明，源图 `docs/design/brand-logo-v2-source.png`；adaptive=全图前景 68% 居中 + 同色纯背景 #EDD7BD；原透明贴纸方案与「禁止重新生成」约束由本次替换终止）——**后续更换图标一律按 §3.5 SOP 执行** |
| `app/build.gradle.kts` | 壳构建配置 | 签名 / ABI 拆分 / versionCode 管理 |

### 1.3 上游文件（app/src/main/assets/rphub/）

| 路径 | 作用 | 维护者注意 |
|------|------|-----------|
| `index.html` | 主界面与脚本加载入口 | **仅允许登记 patch 修改**（见 §4.2） |
| `assets/js/app.js`（512KB） | 主业务入口 | **禁止裸改**；上游同步时整体覆盖 |
| `assets/js/ui-components.js`（198KB） | 选择器/侧边栏/弹窗组件 | 同上 |
| `assets/js/data-services.js` | 存储/记忆/上下文/分支 | 同上 |
| `assets/js/runtime-services.js` | API 请求/消息渲染 | 同上 |
| `assets/js/core-utils.js` | 通用工具/角色卡处理 | 同上 |
| `assets/js/built-in-content.js` | 默认预设/模式提示词/画师串/更新公告 | **NSFW 预设在此文件，禁止任何 patch 触碰**（硬性规定 1） |
| `assets/js/api-utils.js` / `presence.js` / `update-check.js` | 辅助脚本 | 同步时整体覆盖 |
| `assets/css/styles.css` | 全局样式 | 仅允许登记 patch 修改 |
| `vendor/` | 离线化 CDN 依赖 | 上游可能升级依赖版本，同步时检查 |
| `assets/fonts/`（二创新增，上游同步排除项） | 本地字体（Lora + Alibaba PuHuiTi 3 + AlibabaSans） | @font-face 在 `rphub/assets/css/local-fonts.css`；禁止运行时依赖字体 CDN（硬性规定 4） |
| `character/` / `novel/` | 子页面 | 同步时整体覆盖 |

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
| `tools/apply-patches.ps1` | patch 重放脚本 | 001-011 字符串重放 + 007/009/012-020 实体 patch（`patches/entities/`，指纹基线判定） |
| `tools/verify-markers.ps1` | 标记校验门（硬性规定 10） | 同步/重放后必跑；按 README 登记逐项校验标记与敏感文件指纹，全绿才算同步完成 |
| `tools/patches/` | 登记 patch 文件 | 新 patch 必须编号登记（见 §4.2）；`entities/` 存实体 diff |
| `tools/gen-changelog.mjs` | 关于页 CHANGELOG 生成脚本 | 更新 CHANGELOG.md 后运行 `node tools/gen-changelog.mjs`（发布流程 §3.4 步骤 3 前执行） |
| `tools/upstream-fingerprints.txt` | 上游文件 SHA-256 基线 | 同步后更新 |
| `docs/PLAN-v1.2.1.md` | 最近版本（v1.2.1）实施计划 | 最新版本主文档；历史 PLAN（v1.0.0~v1.2.0）并存备查 |
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
| 8 | 发布流程 | 编译 → 推送 → Release（仅稳定版附 APK） |
| 9 | **设计 SKILL 强制条款** | 凡涉及 UI 设计 / 前端设计 / 主题 / 视觉 / 动效 / 交互 / 转场 / 页面设计等内容，**必须先完整阅读并应用以下 4 项设计 SKILL 才可继续讨论、计划、工作**（见 §2.1） |
| 10 | **改动标记与同步适配** | 上游文件内二创改动必须带 `[LuzzyRP patch NNN]` 标记注释；重放通道唯一（apply-patches.ps1）；同步后 verify-markers.ps1 全绿才算完成（见 §4.2/§4.3） |

### 2.1 设计 SKILL 强制条款（硬性规定 9 的展开）

**触发条件**：任何涉及 UI 设计 / 前端设计 / 主题方案 / 视觉风格 / 交互动画 / 转场动画 / 页面布局 / 组件样式 / 字体排版 / 色彩体系的工作——**包括讨论、计划、实施三个阶段**。触发后，**必须先完整阅读以下 4 项 SKILL 的本地存档，才可继续任何设计相关工作**。

**4 项 SKILL 本地存档**（`docs/skills/`，随仓库分发）：

| # | SKILL | 本地路径 | 核心方法论 | 本项目应用方式 |
|---|-------|---------|-----------|---------------|
| 1 | huashu-design | `docs/skills/huashu-design/`（主文档 `SKILL.md`） | 工作室多角色设计方法论（艺术总监→视觉→动效→工程）；**三方向硬门**（任何新视觉设计必须先出 3 个差异化方向给用户选）；反 AI slop 清单；动效=物理学（缓动表达重量与摩擦）；`references/animation-pitfalls.md` 动效避坑 | 主题方案必须先出 3 个方向给用户选；动效设计对照 pitfalls 清单 |
| 2 | awesome-design-md | `docs/skills/awesome-design-md-main/`（73 份真实站点 DESIGN.md 范本库，`design-md/` 目录） | DESIGN.md 是设计真源文档格式（Google Stitch 概念）：Colors / Typography / Layout / Elevation / Shapes / Components / Motion 结构 | 撰写/演进本项目 DESIGN.md 时参照其结构 |
| 3 | open-design | `docs/skills/open-design/`（主文档 `AGENTS.md` + `CLAUDE.md`） | DESIGN.md 作为品牌契约（仓库根 `DESIGN.md` 为唯一设计真源，所有 UI 改动必须遵循）；工件优先；交付前五维 critique 门控；UI 动画哲学（ease-out `cubic-bezier(0.23,1,0.32,1)`、进入 200ms/退出 140ms、禁 scale(0)） | 仓库根 DESIGN.md 作为唯一设计真源；UI 改动必须遵循；交付前五维 critique |
| 4 | ui-ux-pro-max-skill | `docs/skills/ui-ux-pro-max-skill/`（主文档 `CLAUDE.md` + `SKILL.md`） | 可检索设计智能（styles/palettes/UX 规则/图标/字体配对）；`search.py` 检索命令；`data/stacks/jetpack-compose.csv` 等栈规约 | 设计检索用 `python src/ui-ux-pro-max/scripts/search.py "<query>" --domain <domain>`；交付前对照 pro-rules 清单 |

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
   └─ 需要动上游文件 → 先评估：能否用扩展层实现？不能才走 patch（§4.2）
2. 实现 + 自测（真机或模拟器）
3. 更新 CHANGELOG（新增/优化分类）
4. 更新 WORKLOG
```

**禁止**：把新功能写进上游文件；复制上游代码到扩展层后修改（应走 patch 或 fork 决策）。

### 3.3 修复缺陷

1. 定位缺陷归属：上游 bug（同步上游修复 / 登记 patch）还是壳/扩展层 bug（直接修）；
2. 上游 bug 且上游已修复 → 走同步流程（§4.1）；
3. 上游 bug 且上游未修复 → 评估：登记 patch 临时修复（同步时可能冲突，需登记）或接受；
4. 壳/扩展层 bug → 直接修，补 CHANGELOG「修复」分类。

### 3.4 发布新版本

```
1. 更新 build.gradle.kts versionCode/versionName
2. 更新 CHANGELOG.md（格式见 §1.1）
3. 更新 README.md（版本规划表 + 如有重大变更）
4. ./gradlew assembleRelease（签名 + ABI 拆分）
5. 真机回归（核心功能 + 本次变更点）
6. git push
7. GitHub Release（按旧版排版；仅稳定版附 APK）
```

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

## 4. 上游同步 SOP

### 4.1 同步流程（tools/sync-upstream.ps1 半自动化）

```
1. git fetch upstream
2. git diff upstream/main --stat          # 看改动范围
3. 读上游 built-in-content.js 底部更新公告  # 了解新功能
4. 覆盖上游文件（index.html + assets/，排除 vendor/ 与 fonts/）
5. 重放 patches（tools/apply-patches.ps1，001-011 字符串段 + 实体段）；
6. 运行 tools/verify-markers.ps1 校验标记完整性（硬性规定 10，全绿才算同步完成）；
7. 检查 vendor/ 依赖版本（上游可能换 CDN 版本）
8. 实测：数据兼容（localStorage 结构）+ 核心功能回归
9. 更新 upstream-fingerprints.txt
10. 更新 CHANGELOG（同步记录 + 上游版本号）
11. 重新构建 APK
```

### 4.2 Patch 纪律（硬性规定 2 的展开）

**允许 patch 的点位**（当前登记 001-020，详见 `tools/patches/README.md`）：

| patch | 点位 | 内容 |
|-------|------|------|
| 001 | index.html `<title>` | RP Hub → LuzzyRP |
| 002 | index.html `rphub-update-api` meta | 移除（禁用上游更新检查） |
| 003 | index.html 入口 logo | 品牌化（LUZZY/RP） |
| 004 | index.html CDN script/link | 本地 vendor/ 引用 |
| 005 | index.html 尾部 | 挂载扩展层 3 文件（014 另行挂载 luzzy-changelog.js） |
| 006 | index.html head 字体 | Google Fonts Lora → 本地 local-fonts.css |
| 007 | character/novel 子页面 | CDN 本地化 |
| 008 | index.html tailwind.config | 色板 → `rgb(var(--tw-*) / <alpha-value>)`（主题底座；v3=gray/primary，v4=+blue/indigo 收编） |
| 009 | core-utils.js fontFamilies | 内置改「经典」系命名 + 新增 luzzy 默认 |
| 010 | app.js 默认值/白名单 | 默认 fontFamily 'luzzy' + normalize 白名单 |
| 011 | index.html + app.js | 设置页主题卡（主题/模式/字体）+ theme 字段 + watch + 老用户迁移 |
| 012 | app.js + ui-components.js + index.html | 多模型商混用：`providerId::bareId` 引用体系 / 供应商管理器 / 跨商合并模型列表 / 请求点与记忆分桶接入 / `[商名]` 徽标 |
| 013 | ui-components.js + index.html + app.js | v1.1.0 外观面板（模态弹层+侧栏按钮）——**模态部分已被 014 取代**，登记保留追溯 |
| 014 | ui-components.js + index.html + app.js | 外观独立页（全应用唯一入口）+ 关于页（应用内 CHANGELOG）+ 侧栏底部簇重排（外观→关于→设置置底） |
| 015 | app.js + runtime-services.js + ui-components.js + index.html | 供应商三协议（OpenAI/Anthropic/Gemini 适配）+ 编辑器二级弹窗（模型增删改/热检测预设/引用重映射）+ max_tokens 注入 + 自定义生图（luzzy-image:// 分流） |
| 016 | data-services.js | 向量召回块 `_preventContextMerge: true`（防合并吞掉查看器「角色记忆（向量召回）」标注，v1.2.1） |
| 017 | app.js + index.html | 记忆内容管理器（角色/分支选择器跨角色查看分片与总结；编辑=强制重嵌成功才落盘/启停/删除/清空；v1.2.1） |
| 018 | index.html head + ext/luzzy-ext.js | 开屏主题防闪蓝（head 内联脚本按 localStorage 快照写 data-theme/data-mode + luzzy-theme.css 由尾部 link 移入 head 首帧注入；快照由 luzzy-ext.js MutationObserver 维护；v1.2.1） |
| 019 | ui-components.js + index.html + ext/luzzy-theme.css | 侧栏品牌字样 LuzzyRP + 底部簇外观→设置→关于（关于置底）+ 外观页主题预览交互化（色板随 data-theme 取色 --luzzy-prev-*，luzzy 下点击卡片直接切亮/暗模式；v1.2.1） |
| 020 | app.js | 向量分桶检索失败 toast 外化（注入检索/手动检索两处 catch，30s 全局节流，showToast 降级兜底；v1.2.2） |

**新增 patch 的规则**：

1. 先评估能否用扩展层实现——**能就不用 patch**；
2. 必须动上游文件时，用 `git diff` 生成最小 patch，编号登记到 `tools/patches/`；
3. patch 文件头部写注释：目的 / 对应硬性规定 / 预期冲突点；
4. **标记强制（硬性规定 10）**：patch 触及区域必须携带 `[LuzzyRP patch NNN]` 标记注释（JS `//`、HTML `<!-- -->`、CSS `/* */`），并同步实现为 apply-patches.ps1 重放能力——001-011 走字符串块，012 起必须生成/更新 `patches/entities/` 实体 diff（以 rp-hub-reference 对应基线为源）；
5. 同步时 patch 重放失败 → 手工合并 → 更新文件与重放块/实体 → 复跑 verify-markers.ps1 全绿 → WORKLOG 登记；
6. **NSFW 相关点位（built-in-content.js 内 nsfw_rules）永远不在 patch 范围内**。

### 4.3 冲突处理

| 情况 | 处理 |
|------|------|
| patch 重放失败 | 手工合并该 patch，更新文件，WORKLOG 登记 |
| 实体重放失败（上游已发新版） | 手工合并该文件全部二创改动 → 以新版基线重新生成 entities 实体 → verify-markers.ps1 全绿 → WORKLOG 登记 |
| 上游新增文件 | 纳入（先审计外链/推广） |
| 上游删除文件 | 确认无扩展层引用后删除 |
| 上游改 localStorage 数据结构 | 实测老数据兼容；不兼容则扩展层写迁移脚本 |
| 上游更新公告/推广链接 | 按需登记清理 patch（R5 风险） |

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
> `adb -s df97f3c4` 显式指定。**重要**：该机日常使用包 = `com.luzzymeow.luzzyrp.debug`
> （debug 包内有用户真实数据）——真机装新版用 **debug 包 install -r**（数据保留），
> 勿另装 release 包（会生成第二个空数据 LuzzyRP 造成数据分裂）；release APK 仅作
> GitHub 分发。MIUI 注意：adb 安装可能弹「USB 安装」确认框需用户在手机上点确认；
> 锁屏状态下 `am start` 可能不置前，先解锁（上滑）再操作。

- 冷启动 / 热启动 / 后台恢复；
- 对话全流程（配置 API Key → 发送 → 渲染 → 分支）；
- 数据持久化（杀进程重启数据保留）。

### 6.2 同步上游后必测（硬性规定 6）

- 数据兼容：老 localStorage 数据可读；
- 核心功能：对话 / 角色卡导入导出 / 世界书 / 正则 / 记忆 / 生图；
- 断网可用性（飞行模式走查）；
- 扩展层功能回归（luzzy-ext.js 全部功能）。

### 6.3 发版前必测

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
| **Tailwind CDN 不接受 var() 颜色值** | ~~已证伪~~：JIT 接受纯 var()，但见下一行真正的坑 |
| **主题色板必须用 RGB 三元组 + `<alpha-value>`** | 纯 `var()` 色值下基本工具类正常，但带透明度修饰符的类（`bg-gray-50/60` 等）会**静默回退纯白**（暗色白块根因，不报错难排查）。正确写法：config 用 `rgb(var(--tw-gray-50) / <alpha-value>)` + 变量存三元组如 `250 249 245`（2026-09-01 jsdom+CDP 双实证，见 §9） |
| **~~改 assets 不 bump EXTRACT_VERSION = 白改~~（v1.2.3 已根治）** | 构建期 assetSignature（文件数+大小+mtime）注入 BuildConfig.ASSET_SIGNATURE，AssetExtractor 启动比对签名自动重解压——改资产零手动操作；若签名粒度漏检（同 mtime/size 改写）仍可手动 bump 兜底 |

---

## 9. 当前状态与已知问题（2026-09-04 会话 20 快照 · v1.2.2 已发布 + 上游 1.9.0 已同步）

> 完整过程见 `docs/WORKLOG.md` 会话 15-20。上游基线 RP-Hub **1.9.0**（2026-09-04 会话 20 同步）。

### 版本状态（2026-09-04 · 会话 19）

- **v1.2.2（versionCode 9 / EXTRACT_VERSION 20）已发布**（2026-09-04，Release v1.2.2 附三件套 APK；commit b815589b）：
  ① patch 008 v4 blue/indigo 色板收编（toggle 蓝/叙事视角 luzzy 下珊瑚化，classic 原值）；
  ② patch 020 向量检索失败 toast 外化（30s 节流+降级）；
  ③ **全新品牌图标**（White Fox 头像版，纯 1:1 满幅，adaptive=全图前景 68%+同色背景
  #EDD7BD，源图 docs/design/brand-logo-v2-source.png，换 icon SOP 见 §3.5）；
  ④ styles.css 硬编码蓝 4 族收编（侧栏激活项/segmented 选中态/settings-toggle 家族/
  弹窗主按钮，均 luzzy-theme.css 组件级，约 66 处其余低频入遗留）；
  ⑤ verify-markers 43 PASS / 0 FAIL；entities/012-020-*。
- **真机（小米 df97f3c4 / Android 16）已复验**（2026-09-04，v1.2.2-debug 包
  install -r 覆盖日常包，数据保留）：MIUI 桌面新图标渲染 ✓、EXTRACT 20 重解压 ✓、
  关于页 v1.2.2 + 新品牌 LOGO ✓、真实会话完好 ✓。**注意**：该机日常包=debug 包
  （真实数据在内），见 §6.1 设备辨别。
- **上游基线 RP-Hub 1.9.0**（2026-09-04 会话 20 同步：上游仅更新 built-in-content.js，破限预设标记改名 rphub_default）；built-in-content.js / styles.css 与上游逐字节一致。
- **会话 20 同步执行**：EXTRACT 20→21；CHANGELOG 新增 v1.2.3「开发中」章节（发版时重跑
  gen-changelog）；指纹基线表更新至 94a0cd9；同步门 43 PASS / 0 FAIL；debug 包构建后待真机
  安装回归（会话 20 收尾时设备未连接）。
- **仓库描述已纠正**（2026-09-03）：WebView 封装（Kotlin 薄壳），非「原生 Kotlin +
  Compose」；换图标 SOP 见 §3.5。

### 遗留待办（按优先级 · 会话 20 复核）

1. v1.2.3（上游 1.9.0 同步版）真机回归：debug 包 install -r 覆盖日常包 + §6.2 数据兼容/
   核心功能走查（会话 20 收尾时设备未连接，APK 已构建）；
2. patch 020 toast 罐装验证（模拟器）+ classic 主题目测复核（用户日常手动切换）；
3. classic 总结记忆在管理器中的显示（依赖提取管线完整结构，罐装提取补测）；
4. styles.css 其余 ~66 处低频硬编码蓝收编（v1.3.0 候选，清单见 DESIGN.md Do's & Don'ts）；
5. v1.3.0 候选顺延（图像模型生图流、「荧光笔落笔」动效等，见 README 规划表）。

### 高频坑速查（会话 14/15 实踩，勿再踩）

- **~~改 assets 不 bump EXTRACT_VERSION~~（v1.2.3 起资产签名自动比对，零手动）；会话 20 曾三踩此坑（EXTRACT 25 消费后连续改 assets 未 bump），促成了根治方案落地；**
- **Edit 工具整文件写回会翻转 index.html 混合行尾**（blob 为 CRLF 为主 + 14 个 LF 行，
  git 视作 -text 不做 eol 转换）——对该文件的编辑要么用字节级脚本按锚点插入，
  要么编辑后核对 `git diff --numstat` 是否出现整文件伪 diff；
- **真机 exec-out 管道损坏 PNG**：用设备侧 `screencap -p /sdcard/x.png` + `adb pull`
  （Git Bash 需 `MSYS_NO_PATHCONV=1` 防止 /sdcard 被改写）；
- **正则 div 计数不可作为 HTML 结构依据**：结构判定用 parse5/jsdom 真实解析器
  （/tmp/domdiff/analyze.js 思路可复刻：树级对比 management-view 链与 body 子级）；
- **Vue production 编译丢弃注释**：v-else-if 链后的注释不隔断链，普通元素紧跟会被
  吸收进条件链（管理卡教训）；transition 弹窗必须放在「视图区之外」的文档尾部；
- **CDP element.click() 对部分 Vue 合成事件无效**：关键导航用 UI Automator 真实触摸
  （模拟器截图为 900×2000、实机 1080×2400，tap 坐标按 ×1.2 换算或用 uiautomator dump 取 bounds）；
- entities 生成须 `--ignore-cr-at-eol`、应用须 `--ignore-whitespace`（CRLF/LF 混用坑）。

### 主题系统架构速览（改主题必读）

- 驱动：`data-theme`（classic/luzzy）+ `data-mode`（light/dark）双属性（app.js watch，immediate）；
- **patch 018 起 head 内联脚本先按 localStorage 快照（luzzy-ext.js MutationObserver 维护）
  设置双属性，luzzy-theme.css 已移入 head（document.write 注入，块内必须自带 `<script>`
  开标签）**——开屏首帧即正确主题色；
- 变量：luzzy-theme.css 定义 `--tw-gray-*` / `--tw-primary-*` / `--tw-blue-*` /
  `--tw-indigo-*` 为 RGB 三元组（classic=上游原值，luzzy 亮/暗两套，blue/indigo=primary
  同值即收编）；引用走 patch 008 v4 色板 `rgb(var(--tw-*) / <alpha-value>)`；
- 统一雾纸玻璃：`--luzzy-glass-alpha` 0.74 / `--luzzy-glass-blur` 18px 单点变量；
- 品牌色规则（DESIGN.md Colors 章）：禁新增裸 blue/indigo/violet 色相类；
  上游遗留蓝由 luzzy-theme.css 在 `:root[data-theme="luzzy"]` 内收编（classic 零影响）。

## 8. 交接清单（Agent 结束会话前）

- [ ] WORKLOG 追加本次会话「完成 / 决策 / 遗留 / 下一步」；
- [ ] CHANGELOG 已同步（如有版本/功能变更）；
- [ ] 未登记的上游文件改动 = 0（指纹比对）；
- [ ] 新增 patch 已编号登记；
- [ ] 遗留项明确记录，不留给下一个 Agent 猜。
