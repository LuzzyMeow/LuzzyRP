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
| `HARD_REQUIREMENTS.md` | 9 条硬性规定（最高约束） | 修改需在 CHANGELOG 声明 |
| `AGENTS.md` | 本文件 | 与 HARD_REQUIREMENTS 同步演进 |
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
| `app/src/main/res/` | 图标资源 | mipmap 全套 + `drawable-nodpi/luzzy_logo.png`，**禁止重新生成** |
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
| `fonts/` | Lora 本地字体 | 禁止运行时依赖 Google Fonts CDN（硬性规定 4） |
| `character/` / `novel/` | 子页面 | 同步时整体覆盖 |

### 1.4 扩展层（app/src/main/assets/ext/）

| 路径 | 作用 | 维护者注意 |
|------|------|-----------|
| `luzzy-bridge.js` | 桥接封装（存在性检测 + 降级） | 新增桥接方法必须同步此文件 |
| `luzzy-theme.css` | 字体/品牌覆盖 | 用 CSS 变量覆盖，**不改 styles.css** |
| `luzzy-ext.js` | 二创新功能 | 所有新功能 JS 落此文件（或按功能拆分新文件，登记到 index.html 挂载 patch） |

### 1.5 工具与文档

| 路径 | 作用 | 维护者注意 |
|------|------|-----------|
| `tools/sync-upstream.ps1` | 上游同步脚本 | fetch → 覆盖 → patch 重放 → 报告 |
| `tools/apply-patches.ps1` | patch 重放脚本 | git apply 全部登记 patch |
| `tools/patches/` | 登记 patch 文件 | 新 patch 必须编号登记（见 §4.2） |
| `tools/upstream-fingerprints.txt` | 上游文件 SHA-256 基线 | 同步后更新 |
| `docs/PLAN-v1.0.0.md` | v1.0.0 重建计划 | 实施期主文档 |
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

1. 读 `HARD_REQUIREMENTS.md`（9 条）与 `AGENTS.md`（本文件）；
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

---

## 4. 上游同步 SOP

### 4.1 同步流程（tools/sync-upstream.ps1 半自动化）

```
1. git fetch upstream
2. git diff upstream/main --stat          # 看改动范围
3. 读上游 built-in-content.js 底部更新公告  # 了解新功能
4. 覆盖上游文件（index.html + assets/，排除 vendor/ 与 fonts/）
5. 重放 patches（tools/apply-patches.ps1）
6. 检查 vendor/ 依赖版本（上游可能换 CDN 版本）
7. 实测：数据兼容（localStorage 结构）+ 核心功能回归
8. 更新 upstream-fingerprints.txt
9. 更新 CHANGELOG（同步记录 + 上游版本号）
10. 重新构建 APK
```

### 4.2 Patch 纪律（硬性规定 2 的展开）

**允许 patch 的点位**（当前登记）：

| patch | 点位 | 内容 |
|-------|------|------|
| 001-brand-title.patch | index.html `<title>` | RP Hub → LuzzyRP |
| 002-disable-update-check.patch | index.html `rphub-update-api` meta | 移除（禁用上游更新检查） |
| 003-entry-logo.patch | index.html 入口 logo | 品牌化 |
| 004-vendor-local.patch | index.html CDN script/link | 改为本地 vendor/ 引用 |
| 005-ext-mount.patch | index.html 尾部 | 挂载扩展层 3 文件 |

**新增 patch 的规则**：

1. 先评估能否用扩展层实现——**能就不用 patch**；
2. 必须动上游文件时，用 `git diff` 生成最小 patch，编号登记到 `tools/patches/`；
3. patch 文件头部写注释：目的 / 对应硬性规定 / 预期冲突点；
4. 同步时 patch 重放失败 → 手工合并 → 更新 patch 文件 → WORKLOG 登记；
5. **NSFW 相关点位（built-in-content.js 内 nsfw_rules）永远不在 patch 范围内**。

### 4.3 冲突处理

| 情况 | 处理 |
|------|------|
| patch 重放失败 | 手工合并该 patch，更新文件，WORKLOG 登记 |
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
| **Tailwind CDN 不接受 var() 颜色值** | RP-Hub 用 cdn.tailwindcss.com 运行时 JIT，config 色板写 `'var(--tw-gray-50)'` 可能被拒绝 → 工具类不生成 → 主题不生效（2026-09-01 真机实测，见 §9） |

---

## 9. 当前状态与已知问题（2026-09-01 移交快照）

> 本节约等于「接手必读」的现场速览。详细过程见 `docs/WORKLOG.md` 会话 8。

### 项目状态

- **v1.0.0 重建**：RP-Hub 1.8.9 二次开发（WebView 壳 + 扩展层），壳工程/离线化/品牌化/桥接/同步机制全部完成，`assembleDebug` 通过（40.8MB）。
- **主题系统已实施**（patch 008-011）：设置页「界面主题」（经典/暖纸书房）+ 亮暗双模式 + 字体设置（Luzzy 默认字体 = PuHuiTi + AlibabaSans）；新用户默认新主题新字体，老用户保留经典（迁移逻辑）。
- **设计真源**：`DESIGN.md`（暖纸书房定稿）+ `docs/design/`（spec/tech-plan/motion-plan/direction-approved）。

### 🔴 P0 已知问题：主题未生效（待修复）

**现象**：新用户（卸载重装）默认 theme='luzzy'，但界面颜色仍是原版灰色（聊天区 #BCBDBE、顶栏 #7C7D7D），非暖纸书房米纸色 #FAF9F5。

**已排除**：文件部署（patch 008 已生效、luzzy-theme.css 已挂载、ext 三件套齐全）；JS 语法（node --check 通过）；老用户迁移（按设计工作）。

**最可能根因**：**Tailwind CDN 运行时 JIT 不接受 `var(--tw-gray-50)` 作为 config 颜色值**（非合法颜色格式 → 工具类不生成 → 界面无主题样式）。

**修复方向**（按序尝试）：
1. **验证根因**：Playwright 加载 `app/src/main/assets/rphub/index.html`，检查生成的 `<style>` 中 `.bg-gray-50` 规则是否存在、值是什么；同时检查 `document.documentElement.dataset.theme/mode` 是否被设置。
2. **方案 A（推荐）**：回滚 patch 008（tailwind.config 恢复 hex），改在 `luzzy-theme.css` 用高优先级规则覆盖工具类（`:root[data-theme="luzzy"][data-mode="light"] .bg-gray-50 { background-color: #FAF9F5; }`）——需覆盖 RP-Hub 用到的全部 gray/primary 工具类组合（bg-/text-/border-/from-/to-/ring- 等）。
3. **方案 B**：config 色板改 `'rgb(var(--tw-gray-50) / <alpha-value>)'` 形式（Tailwind 官方 CSS 变量颜色模式），变量存 RGB 三元组。
4. 修复后真机复测：卸载重装 → 截图采样聊天区背景应为 #FAF9F5（亮色）。

### 待办（按优先级）

1. **P0**：修复主题未生效（见上）。
2. 真机补测：亮暗切换、字体切换（PuHuiTi 生效）、系统栏图标联动。
3. 文件桥 SAF 实机验证（角色卡 PNG 导入导出）。
4. 推广外链清理（cdn.sta1n.cn/keys、qianxun1688.com）。
5. 上游同步演练（sync-upstream.ps1 假发版模拟）。
6. 构建警告清理（onActivityResult / databaseEnabled deprecated）。
7. CHANGELOG v1.0.0 定稿 → 发布（仅稳定版附 APK）。

### 主题系统架构速览（修复时参考）

- 驱动：`data-theme`（classic/luzzy）+ `data-mode`（light/dark）双属性（app.js `applyTheme`/`applyThemeMode` watch，immediate 执行）。
- 变量：`luzzy-theme.css` 定义 `--tw-gray-*` / `--tw-primary-*`（classic=原版值，luzzy 亮/暗两套）。
- 引用：tailwind.config 色板 var() 化（patch 008，**疑似问题点**）。
- 存储：settings.theme/themeMode（上游体系，随 saveData 持久化）；老用户迁移（savedSettings 无 theme → classic）。
- 系统栏：applyThemeMode → LuzzyBridge.setSystemBarStyle（亮=深图标/暗=浅图标）。
- 字体：fontFamily 'luzzy' → data-app-font="luzzy" → luzzy-theme.css 字体栈（PuHuiTi + AlibabaSans，local-fonts.css @font-face）。

---

## 8. 交接清单（Agent 结束会话前）

- [ ] WORKLOG 追加本次会话「完成 / 决策 / 遗留 / 下一步」；
- [ ] CHANGELOG 已同步（如有版本/功能变更）；
- [ ] 未登记的上游文件改动 = 0（指纹比对）；
- [ ] 新增 patch 已编号登记；
- [ ] 遗留项明确记录，不留给下一个 Agent 猜。
