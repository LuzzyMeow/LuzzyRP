# AGENTS.md · LuzzyRP 开发指南（v3.1 轻量版）

> 接手本仓库的 Agent：先读完本文件再动手。本文件取代旧版（2026-09 删除的 49KB 长文），
> 旧版纪律中有生命力的条目已收敛到下面四节；冲突时以用户当前指示为最高优先级。

## 1. 项目一句话

LuzzyRP = 原生 Kotlin 壳（Jetpack Compose UI，v3.0 起 launcher 为 `ui.ComposeActivity`）。
上游 RP-Hub（Vue 3）同步已退役，基线定格 1.9.3；遵循上游协议（`LICENSE` / `LICENSE-AGPL-3.0` / `docs/LICENSING.md`）。
仅侧载分发，不上架。

## 2. 强制 SKILL 阅读（先读后做，落读取回执）

任何涉及 **UI / 设计 / 动效 / 前端页面 / 交互** 的任务，或**写后端 / 通用代码**的任务，
动手前必须完整读到以下 SKILL 的**正文**（首页摘要不算；先本机后云端，失效按 ①上报用户 ②再找替代）：

| 组 | 条目（读什么） |
|---|---|
| 设计基线 4（全读） | `huashu-design/SKILL.md` · `awesome-design-md/README.md` · `open-design/AGENTS.md` · `ui-ux-pro-max-skill/CLAUDE.md + SKILL.md` |
| 设计子项 · Compose 3（全读） | `android/skills` 的 `jetpack-compose/theming|adaptive|migration` 正文 · `aldefy/compose-skill` 正文（`skills/compose-expert/SKILL.md`）· `hamen/material-3-skill` 正文（`skills/material-3/SKILL.md`） |
| 后端 / 通用编码 3（全读） | `DietrichGebert/ponytail`（正文 + 阶梯纪律）· `github/spec-kit`（正文）· `mattpocock/skills`（正文） |

- 本机来源优先：`docs/skills/**`（huashu / awesome-design-md / open-design / ui-ux-pro-max 已存档）；
  其余从 GitHub raw 抓正文（AnySearch `extract` / `web_fetch`，GitHub 页面抓不到就抓 `api.github.com` 目录再取 `raw` 链接）。
- 回执格式：三类分段列表，每条写「读到正文 ✅ + 关键提取一句话」。读满才许写代码。

## 3. 设计与构建纪律

- **设计真源 = `docs/DESIGN-ui-v4.md`**（Loom v4：织层 token / 动效令牌 / 组件库 / 验收判据）。
  根 `DESIGN.md` 与 `docs/DESIGN-compose.md` 为历史，不再约束。
- 组件唯一源：`ui/pages/common/LoomKit.kt`；色彩零硬编码（M3 role 派生）；跨页转场 420ms 与抽屉等长（实测值，勿改）。
- **testTag 与用户可见文案在任何 UI 重皮中不变**（152 条仪器化用例依赖它们）。
- 构建：`./gradlew :app:assembleRelease` → 单 APK（`splits.abi` 已注释，禁止恢复）；签名永远同一密钥库（`keystore.properties` 不入库；换签 = 老用户无法覆盖升级），发布前 `apksigner verify --print-certs` 核对。
- 版本：versionCode/versionName 递增；`CHANGELOG.md` 新增一段（构建期 `genChangelog` 自动同步 README 与关于页数据）。

## 4. 测试纪律（只跑模拟器）

- 仪器化一律 `ANDROID_SERIAL=emulator-5554 ./gradlew checkChat`（门禁 `verifyEmulatorDevice` 会拦截非 emulator 序列号）。**真机只装 release 包做人工目视**，严禁安装测试件。
- 模拟器卡顿先查 AVD 资源（本机 `LuzzyRP_Test` 必须 `hw.ramSize=4096M`、`hw.gpu.enabled=no`——这是唯一能启动的组合）；长跑劣化（随机生命周期断言失败/漏跑用例）先冷启动模拟器再判定，不改代码迎合。
- 截图留证：`testing/Capture.kt` 写 `/sdcard/Download/luzzy-captures/`（唯一能被 `adb pull` 且活过卸载的落点）；截完逐张人工识图，不达标签回改重截。
- **insets 只信真窗口截图**：`captureToImage` 不含系统栏，测不出「顶栏被状态栏压住 / 文案出屏 / 全屏 Dialog 顶部灰带」这类缺陷（本项目踩过，9 页全中而留证全绿）。验收一律 `adb shell screencap` + `adb pull` 逐页看图；仪器化环境没有系统栏，insets 断言在那里是空转。
- 仪器化用例验收 = `checkChat` 真跑出 `OK (N tests)`；`compile*Kotlin` 通过不构成验收。

## 5. 文档落点

- 设计决策 → `docs/DESIGN-ui-v4.md`；工作日志 → `docs/WORKLOG.md`（每次会话追加「日期/完成/决策/遗留」）；测试纪律 → `docs/CHAT-REGRESSION.md`。
- 过期文档直接删（git 历史可回溯），不搬进 docs/archive；`docs/skills/**`（必读技能存档）与 `docs/design/brand-logo-v2-source.png`（图标源图）**永不删除**。