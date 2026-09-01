# LuzzyRP 硬性规定（HARD_REQUIREMENTS）

> **本文件是项目的最高约束**，位于仓库根目录，任何开发 Agent 接手任务前必须完整阅读并遵守。
> 违反任何一条即为不合格交付。发版前必须逐项通过 `docs/PLAN-v1.0.0.md` §10 验收清单自检。

---

## 背景

LuzzyRP v1.0.0 起为对开源项目 [STA1N156/RP-Hub](https://github.com/STA1N156/RP-Hub)（上游基线 1.8.9）的二次开发：原生 WebView 壳 + 独立扩展层。本文件取代旧版 13 条硬性规定（真流式/Agentic/KV 等不变性随旧工程一并作废）。

---

## 九条硬性规定

### 规定 1 · NSFW 协议不可触碰

RP-Hub `assets/js/built-in-content.js` 内的 NSFW 预设（`nsfw: Object.freeze({...})`，含 `<nsfw_rules>` 全文）**原样保留，禁止任何审查、过滤、改写逻辑触碰该内容**；不得添加内容过滤器、敏感词拦截、输出改写。
- 守护落点：`app/src/main/assets/rphub/assets/js/built-in-content.js`（同步时整体覆盖，**永远不在 patch 范围内**）+ `assets/ext/` 扩展层（禁止引入审查逻辑）。
- 同步验证：每次上游同步后与上游文件做指纹比对（`tools/upstream-fingerprints.txt`），确认逐字节一致。

### 规定 2 · 上游文件最小改动

RP-Hub 上游文件（`index.html`、`assets/js/*.js`、`assets/css/styles.css`、`character/`、`novel/`）**仅允许通过 `tools/patches/` 内登记的 patch 修改**；任何新改动必须新增 patch 文件并编号登记，**禁止裸改**。
- 守护落点：`tools/patches/`（当前登记 001-005，见 `AGENTS.md` §4.2）+ `tools/apply-patches.ps1`。
- 原因：上游大文件（app.js 512KB）裸改会导致同步时无法手工解决的巨型冲突。

### 规定 3 · 扩展层隔离

所有二创新功能必须落在 `app/src/main/assets/ext/` 独立文件（`luzzy-ext.js` / `luzzy-theme.css` / `luzzy-bridge.js` 或按功能拆分的新文件），**禁止写入上游文件**；扩展功能必须自带降级路径，不允许因扩展层报错导致应用白屏。
- 守护落点：`assets/ext/` 目录 + `AGENTS.md` §5 扩展开发规范。

### 规定 4 · 字体锁定

字体遵循 RP-Hub 规定字体栈（`--app-font-modern` / `--app-font-serif`，见上游 `styles.css`），通过 `luzzy-theme.css` 覆盖变量实现；**Lora 必须本地打包**（`assets/rphub/fonts/`），禁止运行时依赖 Google Fonts CDN。
- 守护落点：`assets/ext/luzzy-theme.css` + `assets/rphub/fonts/`。

### 规定 5 · CHANGELOG 同步

新版本内容必须同步更新 `CHANGELOG.md` 与 `README.md`。CHANGELOG 沿用既有格式：`### vX.Y.Z — 标题` + 「新增 / 优化 / 修复 / 注意事项」分类要点 + 构建结果与 versionCode；**每条记录注明上游基线版本**。

### 规定 6 · 上游同步纪律

上游发版后按 `AGENTS.md` §4 SOP 同步（fetch → 覆盖 → patch 重放 → 回归实测）；同步后必须实测：数据兼容（localStorage 结构）、核心功能回归（对话 / 角色卡导入导出 / 世界书 / 正则 / 记忆 / 生图）、断网可用性、扩展层功能回归。

### 规定 7 · 工作区整洁

整理项目工作区，清理冗余文件，做好文件分类（docs 内：plan / archive / 参考资料分区）；未登记的上游文件改动必须为零（指纹比对）。

### 规定 8 · 发布流程

编译新版本 APK（复用现有签名 `keystore/luzzy-release.keystore` 与 ABI 拆分）→ 推送远程仓库 → 按仓库旧版本 release 排版格式编写新的、美观且极其详细的 release 内容推送（**仅稳定版更新附 APK**）。

### 规定 9 · 设计 SKILL 强制条款（2026-09-01 用户新增）

凡涉及 UI 设计 / 前端设计 / 主题方案 / 视觉风格 / 交互动画 / 转场动画 / 页面布局 / 组件样式 / 字体排版 / 色彩体系的工作（**包括讨论、计划、实施三个阶段**），**必须先完整阅读并应用以下 4 项设计 SKILL 才可继续讨论、计划、工作**：

| # | SKILL | 仓库 | 本地存档 | 本项目应用方式 |
|---|-------|------|---------|---------------|
| 1 | huashu-design | https://github.com/alchaincyf/huashu-design | `docs/skills/huashu-design/` | 工作室多角色设计方法论；**三方向硬门**（任何新视觉设计必须先出 3 个差异化方向给用户选）；反 AI slop 清单；动效=物理学（缓动表达重量与摩擦）；`references/animation-pitfalls.md` 动效避坑 |
| 2 | awesome-design-md | https://github.com/VoltAgent/awesome-design-md | `docs/skills/awesome-design-md-main/` | 73 份真实站点 DESIGN.md 范本库（`design-md/` 目录）；撰写/演进本项目 DESIGN.md 时参照其结构（Colors/Typography/Layout/Elevation/Shapes/Components/Motion） |
| 3 | open-design | https://github.com/nexu-io/open-design | `docs/skills/open-design/` | DESIGN.md 作为品牌契约（仓库根 `DESIGN.md` 为唯一设计真源，所有 UI 改动必须遵循）；工件优先；交付前五维 critique 门控；UI 动画哲学（ease-out `cubic-bezier(0.23,1,0.32,1)`、进入 200ms/退出 140ms、禁 scale(0)） |
| 4 | ui-ux-pro-max-skill | https://github.com/nextlevelbuilder/ui-ux-pro-max-skill | `docs/skills/ui-ux-pro-max-skill/` | 可检索设计智能（styles/palettes/UX 规则/图标/字体配对）；`search.py` 检索命令；`data/stacks/jetpack-compose.csv` 等栈规约；交付前对照 pro-rules 清单 |

**强制流程**（触发后按序执行，缺一步不得进入设计工作）：

1. **阅读**：完整阅读 4 项 SKILL 主文档（huashu-design `SKILL.md`、open-design `AGENTS.md`、ui-ux-pro-max `CLAUDE.md` + `SKILL.md`、awesome-design-md `README.md`）；
2. **三方向硬门**（huashu-design 强制）：任何新视觉设计必须先产出 **3 个差异化方向**（含真实视觉初稿）给用户选择，用户选定后才进入执行；用户指定风格也不豁免；
3. **设计真源**（open-design 强制）：设计决策写入仓库根 `DESIGN.md`（唯一设计真源），所有 UI 改动必须遵循；
4. **动效纪律**（huashu-design + open-design）：动效=物理学；进入 200ms / 退出 140ms / ease-out `cubic-bezier(0.23,1,0.32,1)`；禁 `scale(0)` 起步；对照 `animation-pitfalls.md`；
5. **交付门控**（open-design 五维 critique + ui-ux-pro-max pro-rules）：交付前执行五维 critique 与 pro-rules 清单逐项对照。

**豁免**：非设计的机械操作（修 bug、纯文字改动、数据迁移、构建配置）不触发；任何视觉产出（哪怕一行 CSS 颜色改动）都触发。

**与规定 4（字体锁定）的关系**：字体锁定是上游合规约束，设计 SKILL 条款是设计质量约束；冲突时以更严格者为准。

- 守护落点：`docs/skills/` 四目录 + `AGENTS.md` §2.1 + 仓库根 `DESIGN.md`。

---

## 合规红线（许可证义务，与规定并行）

- 上游 LICENSE 文件**原样保留**在仓库内，禁止删除或改写；
- README 顶部二创署名声明（基于 STA1N156/RP-Hub，上游基线版本）不得移除；
- 项目保持 CC BY-NC 4.0，禁止任何商业化使用；
- **仅侧载分发，禁止上架应用商店**（nsfw_rules 含年龄条款，合规风险）。
