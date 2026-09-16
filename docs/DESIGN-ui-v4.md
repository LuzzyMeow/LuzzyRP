# DESIGN-ui-v4 · Loom v4 设计契约（现行设计真源）

> **地位**：本文件是 LuzzyRP Compose UI 的唯一设计真源（v3.1 起）。
> 根目录 `DESIGN.md` 与 `docs/DESIGN-compose.md` 为 v1.x WebView / v3.0 时代契约，已降级为历史，不再约束新工作。
> 所有 UI 改动必须遵循本文件；改令牌先改这里再改代码。

## 0. 一句话

**织机 Loom v4**：把界面收成一套「织层」——色彩全部来自珊瑚陶土种子（`#CC785C`，TONAL_SPOT）推导的 M3 role，珊瑚色每页只在一处发挥（accent），层级靠色调阶梯与物理动效表达，不靠阴影与边框堆叠。

## 1. 色彩

| 层 | 令牌 | 取值 | 用途 |
|---|---|---|---|
| 画布 | `Loom.current.canvas` | `surface` | 页面底（+ 页面 accent 的顶部 wash 6-8%） |
| 卡 | `Loom.current.card` | `surfaceContainerLow` | 常规卡 |
| 浮起 | `Loom.current.raised` | `surfaceContainerHigh` | 底部表 / 弹层 / 当前态行 |
| 覆盖 | `Loom.current.overlay` | `surfaceContainerHighest` | 开关轨道 / 未选中 chip |
| 发丝线 | `Loom.current.hairline` | `outlineVariant@40%` | 分隔 / 卡边 |
| 织纹 | `Loom.current.weave` | `onSurface@3.5%/2.5%` | 画布点阵（12dp 间距） |
| 顶缘高光 | `Loom.current.topHighlight` | 白 5%（暗 3%） | 卡片顶部微光，替代阴影 |

- **零硬编码色相**：唯一例外是卡面 monogram 的固定深暖底 `#2E2724`（白字压浅粉/深棕两头都不对的历史结论）。
- 每页一个 accent：对话/记忆/用量=primary，角色/会话=secondary，世界书/预设/关于=tertiary。
- 语义色沿用 `LuzzySemantic`（success/warning/error），徽标一律带文字（颜色不是唯一指示）。

## 2. 形状与字号

- 圆角：卡 18（`LoomShape.Card`）/ hero 24 / 控件 12 / 药丸 999。
- 字体：display = Lora（页标题、名牌、大数字）；正文 = `LuffyBody`（即 LuzzyFonts.Body）。
- 字号全部经 `fontScale` 双 token 体系（`luzzyTypography` + MarkdownTokens）。

## 3. 动效（`ui/theme/LoomMotion.kt`）

| 令牌 | 时长 | 曲线 | 用途 |
|---|---|---|---|
| press | 90ms | linear | 按下 0.985 缩放 + tonal 变换 |
| quick | 140ms | Exit | 退出/收起（快于进入） |
| standard | 200ms | Enter | 进入/淡入/scrim |
| expressive | 320ms | Page | hero、底部表、图表绘制 |
| page | **420ms** | Page | 跨页交叉淡化（2026-09-12 帧采样实测值，与抽屉等长，**勿改**） |
| push | 240ms | Enter | 详情推进（二级页） |
| stagger | 40ms ×6 | — | 列表入场（fade + 上移 10dp） |
| interactive | spring(0.75, MediumLow) | — | 开关/chip/选中态 |

- 页面/抽屉级必须用对称曲线 `FastOutSlowIn`（强 ease-out 会让 400ms 感知只有 ~100ms）；
  元素级用 ease-out `cubic-bezier(0.23,1,0.32,1)`。
- **禁 scale(0) 起步**；一次性时长全部过 `scaledDuration`（系统「移除动画」→ 归零跳终态）；
  循环动效（呼吸点/shimmer）用 `reduce` 分支停帧。

## 4. 组件（`ui/pages/common/LoomKit.kt` 为唯一组件源）

`LoomScaffold`（织纹画布+大标题头+可折叠）/ `LoomHero` / `BandCard`（=hero 皮的 API 兼容壳）/ `LoomCard(tier, rail)` / `LoomRow` / `LoomBadge` / `LoomChip` / `LoomSectionLabel` / `LoomSwitch` / `LoomSliderRow` / `LoomField` / `LoomEmpty` / `LoomSkeletonRow` / `LoomConfirmDialog` / `LoomOverflowMenu` / `LoomIconButton`。
旧名（`PageKit` 的 `SettingCard`/`EntryCard`/`EmptyState` 等）保留为**兼容壳**，内部已全部指向 Loom 实现——新代码直接用 Loom 名。

## 5. 交付判据（验收门）

1. 仪器化留证：`LoomGalleryVisualCaptureTest`（组件画廊，亮暗）+ 各页 VisualCapture（亮暗+状态）→ `/sdcard/Download/luzzy-captures` → 逐张识图。
2. 对比度门禁：`ThemeContrastTest`（正文 ≥4.5:1、图形 ≥3:1，色值取自生产推导）。
3. 行为门禁：`checkChat`（152 条仪器化用例）全绿；testTag 与用户可见文案在任何重皮中不变。
4. 触控目标 ≥48dp；文本层级随 fontScale；动效尊重「移除动画」。
5. **insets 必须用真窗口截图验收（screencap，不是 `captureToImage`）**：
   - `captureToImage` 只截 Compose root，**不含系统状态栏/导航栏**，因此 insets 类缺陷在留证图里完全看不见——本项目实测踩过：9 个页面顶栏被状态栏压住、空态长文案两侧溢出，而仪器化留证全绿。
   - 规矩：`adb shell screencap -p` + `adb pull`，**逐页**看图；重点看「顶栏是否在状态栏下方」「底部主按钮是否在导航栏上方」「长文案是否出屏」。
   - 全屏 `Dialog` 同样要查：默认 `decorFitsSystemWindows = true` 时窗口不覆盖状态栏，那里会透出被 dim 的下层（一条深灰带）；全屏编辑器须显式关掉 fit 并自行消费 insets。
   - 仪器化测试环境（`createComposeRule` 的空 Activity）**没有系统栏**，任何 insets 断言在那里都是空转——不要用「写个测试断言 insets」替代真窗口走查。