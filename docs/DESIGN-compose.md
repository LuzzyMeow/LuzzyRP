# LuzzyRP DESIGN-compose.md · v3.0 Compose 设计真源

> **本文件是 v3.0（Jetpack Compose 版）的唯一设计真源**（open-design 品牌契约模式；现行 WebView 版契约仍见仓库根 `DESIGN.md`，两者并存至 P6 切换完成）。
> 方向选定：2026-09-12 用户选定「**A · 织机 Loom**」（三方向板与选择原话见 `docs/design/boards-v4/direction-approved-v4.md`）。
> 依据：硬性规定 9（4 项 SKILL 已完整阅读，会话 63）+ `docs/RESEARCH-v3-rikkahub-design.md` 侦察。
> **AGPL 义务**：凡按本文件复用 rikkahub 源码，文件头保留其原始版权声明与来源标注（`docs/LICENSING.md` §2）。

## 1 · Visual Theme & Atmosphere

**织机 Loom**——rikkahub 的骨架，Luzzy 的线。Material 3 Expressive 为座（`MaterialExpressiveTheme`
+ `MotionScheme.expressive()`），**HCT 动态色板为芯**（seed = 珊瑚陶土 `#CC785C`，
TONAL_SPOT variant，contrast level 0——机制照搬 rikkahub `CustomTheme.kt`，数值全新推导，
零照抄 rikkahub Claude 预设）。

气质：**现代 M3 工具感 × 暖色织机**。在满屏冷调 AI 工具里保持纸与墨的温度；
克制、清晰、可久读——「每次对话，都像一本有你的小说」的原生延续。

关键体验词：暖纸（HCT 暖中性面）、织染（一个种子色织出全套色板）、
沉浸（长文阅读优先）、工程（性能逃生门做成产品开关）。

## 2 · Color Palette & Roles（M3 ColorScheme，亮/暗双套）

> 推导方式：HCT（material-color-utilities 2021 spec）TONAL_SPOT，seed `#CC785C`，contrast 0。
> **本表为代码生成值权威落档**（`ui/theme/LuzzyTheme.kt` 生成，快照测试
> `LuzzyPaletteSnapshotTest` 钉死；2026-09-12 P1 以生成结果回填替换早期手算近似值）。

| M3 Role | 亮色 | 暗色 | 用途 |
|---------|------|------|------|
| primary | `#8F4C35`（T40） | `#FFB59D`（T80） | 主按钮文字/选中图标/强调 |
| onPrimary | `#FFFFFF` | `#55200C` | |
| primaryContainer | `#FFDBD0`（T90） | `#723520`（T30） | 用户气泡/选中底 |
| onPrimaryContainer | `#390C00` | `#FFDBD0` | |
| secondary | `#77574D` | `#E7BDB1` | 次级强调 |
| secondaryContainer | `#FFDBD0` | `#5D4036` | chip/次级面 |
| tertiary | `#6A5E2F` | `#D7C68D` | 荧光笔记号族（amber 系） |
| tertiaryContainer | `#F4E2A7` | `#51461A` | 高亮底 |
| error | `#BA1A1A` | `#FFB4AB` | 语义错误 |
| surface | `#FFF4F1`（T97 暖纸） | `#231917`（T10 暖黑） | 画布 |
| surfaceContainerLowest | `#FFFFFF` | `#140C0A` | |
| surfaceContainerLow | `#FFF1ED` | `#231917` | 列表面 |
| surfaceContainer | `#FCEAE5` | `#271D1B` | 卡片 |
| surfaceContainerHigh | `#F7E4DF` | `#322825` | AI 气泡（气泡模式） |
| surfaceContainerHighest | `#F1DFDA` | `#3D322F` | |
| surfaceDim / Bright | `#E8D6D1` / `#FFF8F6` | `#231917` / `#423733` | |
| surfaceVariant | `#F5DED7` | `#53433F` | |
| outline | `#85736E`（T50） | `#A08D87` | 强描边 |
| outlineVariant | `#D8C2BB`（T80） | `#53433F`（T30） | 发丝线/分隔 |
| inverseSurface / inversePrimary | `#392E2B` / `#FFB59D` | `#F1DFDA` / `#8F4C35` | |
| scrim | `#000000` | `#000000` | 遮罩 |

**ExtendColors（五色 × 十阶扩展色，结构照搬 rikkahub `Color.kt`）**：red / orange / green /
blue / gray 各 10 阶，用于用量图表分类色、状态徽标等 M3 装不下的语义色；**基準值以 HCT
从 seed 推导后回填**，亮暗两套（暗色反转，同 rikkahub 语义）。

**数据可视化分类色序**（承袭现行 DESIGN.md 语义）：primary-500 → tertiary(amber) →
primary-600 → success(green) → primary-700 → error → primary-400 → gray-500；第 9 起「其他」。

**语义色**：success `#5DB872` / warning `#D4A017` / error `#C64545`（承袭现行 DESIGN.md，不随主题推导）。

## 3 · Typography Rules（硬性规定 4 落地）

| 层 | 字体 | 说明 |
|----|------|------|
| display（角色名/名牌/区块标题） | **Lora**（本地 TTF） | 衬线文学声音——品牌不变量，三方向共同保留 |
| 正文/UI 拉丁+数字 | **AlibabaSans**（本地 TTF） | |
| 正文/UI 中文 | **Alibaba PuHuiTi 3.0**（本地 TTF） | Compose `FontFamily` 无逐字形回退，中文主族直接取 PuHuiTi（历史决策沿袭，见 DESIGN.md 助手章字体节） |
| 等宽（模型 ID/终端/代码块） | 系统等宽（`FontFamily.Monospace`） | 不单独打包（承袭既有决策） |
| 代码块 | JetBrains Mono（可选，参照 rikkahub） | P5 时再议体积代价 |

- 正文 ≥14sp，行高 1.68；caption ≥12sp；`Typography` 以 M3 默认骨架 + 字体族覆盖（同 rikkahub `Type.kt` 思路）；
- **禁运行时 CDN**（规定 4）：全部字体资源打包 `res/font/`，`Font(R.font.xxx)` 加载。

## 4 · Component Stylings

| 组件 | 规格 | 来源 |
|------|------|------|
| 顶栏 | M3 `TopAppBar`，`surfaceContainer` 面（亮） / 默认（暗）——rikkahub `CustomColors.topBarColors` 同语义 | rikkahub 结构 |
| 用户气泡 | `primaryContainer` 底，16dp 圆角，最大宽 320dp，右对齐，padding 14×10dp | rikkahub + 现行像素规格 |
| AI 消息 | **默认无气泡**（Markdown 裸排在 surface 上）；设置可开「气泡模式」（`surfaceContainerHigh` + `bubbleOpacity`） | rikkahub 语义 |
| 思考卡（ChainOfThought） | 折叠行 = dot + 摘要 + chevron；展开 = 步骤分组（思考/工具）；live 态 coral 描边 | rikkahub 结构 + 现行 live 语义 |
| 输入岛 | `MaterialTheme.shapes.largeIncreased`（28dp）圆角条，`outlineVariant.copy(alpha=.5)` 边，haze 玻璃**做成设置开关**（关 = 纯色 tint 回退）；左附件钮 + 圆形发送键 44dp（primary T40 底） | rikkahub ChatInput |
| 抽屉 | 手机 `ModalNavigationDrawer` / 宽屏(≥1100dp 横屏) `PermanentNavigationDrawer` 双形态 | rikkahub |
| 列表/卡片 | `ListItem`/`Card` 容器色 = `surfaceContainer`（同 rikkahub `cardColors` 语义） | rikkahub |
| 分支指示 | 胶囊 chip `‹ 2/3 ›`（outlineVariant 边），气泡名行右侧 | RP 特有 |
| 模型商徽标 | `[商名]` 胶囊 chip（`secondaryContainer` 底） | 承袭现行语义 |
| **图标体系** | **LuzzyIcons**（`ui/icons/`）：主体 = 之前 LuzzyRP（WebView 版）的 `ic_lz_*` VectorDrawable 集合（形状 = 上游 RP-Hub 内嵌 SVG d 路径原样搬运，Heroicons v1 outline 形状池，MIT）——新旧版本图标**同形**；补缺 6 枚取 Heroicons v1.0.6 官方（MIT）。**禁止**引入 material-icons / hugeicons（后者为 rikkahub 本地 jar，源码不可得且素材许可链不透明） | v1.5.0 LedgerIcons 先例延续 |
| **聊天页 · 沉浸形态**（2026-09-12，用户拍板「复刻原项目聊天页」，三方向豁免已落档） | 见 §12 | 上游聊天页 + 现行 DESIGN.md 条款翻译 |

## 12 · 聊天页 · 沉浸形态（角色背景版，2026-09-12）

> 用户拍板：以示例角色卡复刻原项目聊天页（全屏角色图背景 / 玻璃半透明气泡 / 流式输出 /
> 思考卡节点 / 输入框功能 icon）。三方向豁免记录：`docs/design/boards-v4/direction-approved-v4.md`。

### 12.1 层级结构（自底向上）

| 层 | 内容 |
|----|------|
| 背景层 | 角色卡图 `Image(ContentScale.Crop, fillMaxSize)`（P1 演示资产 `vanio_card.png`；正式链路 P4 由角色卡导入提供）+ **hazeSource 锚点** |
| 可读性 scrim | 顶栏黑渐隐 `#141413 .55 → transparent @40%`（上游同款，现行 DESIGN.md「顶栏黑色渐隐」条款）+ 底部 `black .30 → transparent`（护输入岛）+ 暗模式整面 `black .22` |
| 消息流 | LazyColumn，玻璃气泡族（12.2） |
| 顶栏 | 沉浸态：透明 + 白字（角色名 Lora 17sp + 状态行 11sp）+ 头像圈（角色图裁圆 34dp）+ 圆形动作钮改**黑玻璃 chip**（`black 28%` 圆底 + 白 icon——上游移动端顶栏按钮语言） |
| 输入岛 | 玻璃近实底卡（12.4） |

### 12.2 雾纸玻璃气泡（统一雾纸配方的 Compose 翻译）

- 实现：**haze**（`dev.chrisbanes.haze`，rikkahub 同库）：背景层 `hazeSource(state)`，
  玻璃面 `hazeEffect(HazeStyle(tint, alpha, blurRadius))`；
- **配方（单点调参常量 `LuzzyGlass`，承袭现行 DESIGN.md「单点变量」纪律）**：
  - blur = **18dp**；基础 tint alpha = **0.78**；
  - AI/思考卡 tint：亮 `#F5F0E8` / 暗 `#2B2824`（现行 DESIGN.md 表面色）；
  - 用户气泡 tint：亮 `#F1E3D9` / 暗 `#3A2E26`（用户气泡底语义）+ 边框 coral-300@.55；
  - 上游实证：0.74+blur18 在深色立绘上正文 ≥7:1——对比达标；
- 圆角：气泡 16dp / 思考卡 14dp；文字 onSurface 系（雾纸后 = 纸面底，正常对比）。

### 12.3 思考卡节点 + 假流式（P1 demo；P2 接 v2.0 Kotlin 传输换真流式）

- **状态机** `Idle → Thinking → Streaming → Done`（发送键 ↔ 停止钮切换）；
- Thinking：思考卡 **live 态**（coral `primary@.45` 描边 + 「思考中…」+ 进度点），推理文本
  逐字填充（20ms/字）；完成后折叠为「思考 · N.Ns」；
- Streaming：AI 回复逐字上屏（22ms/字）+ `animateContentSize()`；**气泡容器在流式开始前
  即存在**（pitfalls 迁移：不留空白首帧）；
- 思考卡结构（上游 native-thinking-card 同构）：折叠行（dot + 摘要 + chevron，可点展开）+
  展开面板 = 步骤时间线（每步 = 圆点 + 短文本 + 缩进）——整卡玻璃；
- 动效一律 200/140ms + `Motion.Easing`；`animateContentSize` 走 spring 默认可接受（尺寸过渡非进出转场）。

### 12.4 输入岛（功能 icon 行复刻）

- 卡：28dp 圆角 + 玻璃近实底（tint `surfaceContainerHigh@.95` + 无 blur——键盘邻接面，
  承袭上游「输入岛不入玻璃族」语义）+ outlineVariant@.5 边；
- **功能行**（上游输入岛功能按钮复刻）：`Plus`(附件) / `Sliders`(预设) / `BookOpen`(世界书) /
  `Mcp`(工具) / `Workspace`(工作区) 五个 34dp 圆钮（onSurfaceVariant icon，按下反馈）+
  右侧模型 chip（`DeepSeek-V4`，primaryContainer 胶囊 12sp）；
- 输入行：`BasicTextField` 无边框（占位「写点什么……」outline 色）+ 38dp 圆形发送键
  （primary 底 / 流式中变停止 = error 底方块 icon）。
| 发送/主按钮 | primary 底 + onPrimary 字；active 加深 | M3 语义 |
| 荧光笔记号 | tertiary(amber) 低透明度压底，全屏 ≤3 处（手作记号纪律沿袭） | 承袭现行 |

## 5 · Layout Principles

- 单 Activity + Nav 骨架；页面壳 = `Scaffold(topBar, bottomBar=input)`；
- 内容边距 16dp；消息轮间距 32dp / 同轮 8dp（承袭现行像素规格）；
- 消息流 = `LazyColumn`（长文优先，禁止整屏重组——会话 59/60 性能结论在 Compose 侧同样生效）；
- 宽屏 ≥1100dp 横屏进永久抽屉形态；
- 居中阅读宽上限（气泡 320dp / 无气泡正文约 292-320dp 栏宽）。

## 6 · Depth & Elevation

- **以表面色阶分层为主，阴影为辅**（M3 语义）：canvas → surfaceContainerLow →
  surfaceContainer → surfaceContainerHigh 逐级提亮，暗色下同理；
- 输入岛：tonalElevation 0 + 玻璃开关（开 = haze blur 16dp + tint；关 = surfaceContainerHigh 实底）；
- 弹窗/BottomSheet：M3 默认 elevation + scrim；**禁自造多层阴影**。

## 7 · Motion（动效令牌，硬约束）

- 基线：进入 **200ms** / 退出 **140ms** / `CubicBezierEasing(0.23f, 1f, 0.32f, 1f)`（ease-out 系）；
  **禁 ease-in**；禁 `scale(0)` 起步（自 `scale(0.96)+alpha 0` 起步）；尊重系统「减少动态效果」；
- `MotionScheme.expressive()` 作 M3 组件默认（rikkahub 同款），**自定义交互动画仍守 200/140 令牌**；
- MeshGradient 背景：线性底 + 4 个正弦轨迹光斑（5.5s/7s/8.5s/6.2s 错落周期，LinearEasing），
  珊瑚/amber/暖青三斑 + 亮暗两套配色——**不用 `Modifier.blur`**（全 API 级可用，rikkahub 已验证手法）；
- 气泡/内容尺寸过渡 `animateContentSize()`；流式期间**禁用文本选择**（Compose SelectionContainer
  并发修改崩溃——rikkahub 源码注释实证，P2 必守）；
- 页面交接：侧栏滑移 + 内容交叉淡化同帧起跑同时结束（沿用现行 DESIGN.md 交接编排语义，
  Compose 侧以 `AnimatedContent`/`AnimatedVisibility` 实现，时长仍 200ms）。

## 8 · Do's and Don'ts

✅ coral 稀缺使用（primary 系只上按钮/选中/链接）；✅ Lora 只上名牌与区块标题；
✅ 亮暗分别过 4.5:1；✅ 色值只允许「HCT seed 推导 + 本表 + ExtendColors」；
✅ 玻璃/模糊做成设置开关并自带实底回退；
❌ 照抄 rikkahub Claude 预设色值（#C96442/#FAF9F5 等——仅对照不入 token）；
❌ 紫渐变 / emoji 图标 / 左彩边圆角卡 / 均匀深蓝底+霓虹 glow（反 slop 清单）；
❌ 高频滚动/流式表面常驻 blur 或 will-change（性能档位沿袭 v1.3.0 结论）；
❌ 字体 CDN（规定 4）；❌ 临场发明色相。

## 9 · Responsive Behavior

- 手机竖屏为设计基准；宽屏/横屏 ≥1100dp 切永久抽屉（双形态，不另设计一套）；
- 触控目标 ≥44dp（图标按钮 40dp 方钮 + 间距补足 ≥44dp 命中区）；
- 键盘弹出 `imePadding()`，输入岛随之贴附（rikkahub 同款）。

## 10 · Agent Prompt Guide（给后续 P1+ 实现 Agent 的一句话速查）

> 「M3 Expressive 座 + HCT seed #CC785C 动态色板；Lora 名牌 + PuHuiTi 正文（本地字体）；
> AI 默认无气泡、用户 primaryContainer 气泡；输入岛 largeIncreased 圆角 + 玻璃开关；
> 动效 200/140 ease-out；色值只取本文件，拿不准看 `docs/design/boards-v4/direction-a-loom.png`。」

## 11 · 实施对账（P1 验收用）

- [x] HCT 色板生成代码落地，输出与本表逐值核对（本表已为 2026-09-12 生成值权威落档）；
- [x] ExtendColors 五色×十阶亮暗两套落 `ui/theme/`（`extendFor`，HCT 派生 ramp）；
- [x] 字体四族本地打包（Lora ×2 / PuHuiTi ×3 / AlibabaSans ×3 / 系统等宽），零 CDN；
- [x] 聊天页假数据版：顶栏 / 无气泡 AI 消息 / 用户气泡 / 思考卡折叠 / 分支 chip / 输入岛 / Modal 抽屉
  （宽屏 Permanent 抽屉延后至 P5 自适应）；
- [x] MeshGradient 背景亮暗两套；
- [x] 动效令牌常量类（`Motion`：200/140ms + 贝塞尔）；〔reduced-motion 显式适配随 P2 动效落地〕
- [x] 五维 critique 通过（2026-09-12：方向=织机 Loom 落位 / 品牌=Lora 名牌+珊瑚 primary /
  层级=顶栏 surfaceContainer-消息流-输入岛 High 三层清晰 / 动效=光斑无 blur+令牌已立 /
  工程=快照钉值+edge-to-edge 避让+release R8 通过）。验证截图
  `docs/design/verify-p1-{chat-light,chat-dark,drawer,scrolled}.png`。
## 13 · 全页面骨架与导航转场（2026-09-12，考察落档）

> **考察来源**（用户要求「重点考察所有参考项目，包括我们自己的项目」）：
> ① **rikkahub**（`docs/rikkahub-master/`）：Navigation3 `NavDisplay` 转场——push = 新页右滑入
> 全幅 + 旧页左滑半幅 + scaleOut(0.7)+fadeOut；**栈底返回 = 纯 fadeIn/fadeOut**；Chat 页
> metadata 单独覆盖为 fade；抽屉点菜单**直接 navigate 不先关抽屉**；设置类页面 = 大标题折叠
> TopAppBar + **CardGroup**（圆角 20dp 卡组、组内条目圆角压 4dp、按下动画）。② **WebView 版
> LuzzyRP**（我们自己）：统一页头组件 settings-page-header（汉堡+图标+标题+右侧操作）；
> 各页 IA 见下表；关于页 = 品牌卡 + CHANGELOG 卡（版本下拉+搜索）+ 回顶 FAB。

### 13.1 页面清单（P1 静态稿 = 侧栏 7 项 + 关于页）

| route | 页 | 骨架要点（上游 IA 翻译） |
|-------|-----|------------------------|
| Chat | 对话 | §12 沉浸形态（已落地） |
| Characters | 角色卡 | 页头（搜索+网格/叠卡切换+添加）→ 角色卡 2 列网格（图 + 名 + 「使用中」徽标 + 世界书/正则计数）→ 空态 |
| WorldInfo | 世界书 | 页头（导出/导入/新建）→ 激活设置折叠卡（扫描深度 slider ×2）→ 条目行卡（名 + 范围徽标 + toggle + 编辑/删除） |
| Presets | 预设 | 页头（导出/导入/新建）→ 行卡（名 + 注入位置徽标 + toggle + 编辑/删除） |
| Memory | 记忆 | 页头（清空红钮）→ 引擎设置折叠卡（toggle + 总结/向量 segmented + slider）→ 检索卡（统计双卡 + 检索行 + 结果列表） |
| Usage | 用量 | 页头（清空红钮）→ 类型 segmented（4 段）→ 总用量卡 → 趋势图卡（粒度 pill + 系列 chip，P1 静态占位折线）→ 日志列表（模型/类型/耗时 + 输入/输出/消耗三行） |
| Settings | 设置 | 三大卡（用户设置 / API 连接 / 高级设置），每卡 = 渐变头带 + 内容区（表单/toggle 网格/存储进度）——上游三卡 IA 翻译 |
| About | 关于（新增） | 品牌区（logo + LuzzyRP + versionName）+ 署名行（基于 RP-Hub 1.9.3 · AGPL-3.0 自有 / CC BY-NC 4.0 上游 · 仅侧载）+ 应用内 CHANGELOG 卡（真实数据源：`assets/ext/luzzy-changelog.js` 的 `window.LuzzyChangelog.md`，P1 简版 markdown 渲染）+ GitHub 页脚 |

### 13.2 导航与转场规格

- **导航**：P1 不引 Navigation3——`LuzzyRoute` sealed 状态 + `AnimatedContent` 容器
  （`LuzzyNavShell`：ModalNavigationDrawer 包全部页面，抽屉提升到壳层，各页收
  `onOpenDrawer` 回调；抽屉菜单点击 = setRoute + 关抽屉，同拍执行——rikkahub
  「直接 navigate」实践 + 我们的「侧栏收起与切页同拍」语义合并）；
- **转场（页面交接·用户 2026-09-12 定稿语义）**：**「点击菜单项 → 抽屉向左收起的**同时**
  内容交叉淡化 → 抽屉完全收起的时刻转场恰好完成、新页完整呈现」**（用户原话：「菜单栏向左
  隐藏的同时 交叉淡化页面 做好不透明度曲线…侧边菜单栏完全收入抽屉页面转场完成」）：
  - **时长与分段（2026-09-12 二次修订：用户实测反馈「250ms 像硬切」——淡化主体发生在
    抽屉遮挡期，被抽屉移动完全掩盖）**：内容转场 **450ms** 两段式——**0-250ms**（抽屉收起期）
    新页 alpha 0.35→~0.8 + 上移大半，**250-450ms**（抽屉收完后）为**可感知的落定段**
    （渐显至 1 + 上移到位）；旧页 fadeOut **200ms**（随抽屉期内完成，避免残影）；
  - **位移分量（可感知的关键）**：新页 `slideInVertically` 自下 **~30dp** 上移——位移比
    alpha 更易被视觉捕捉；不透明度曲线 ease-out `Motion.Easing` 全程；
  - 禁 `scale(0)`；easing 统一 `Motion.Easing`。rikkahub 的滑动+缩放 push/pop 转场
    **不采用**（其语义面向层级栈导航；LuzzyRP P1 页面为平铺切换，P5 引入详情栈时再议）；
- **页面底色**：非沉浸页 = `colorScheme.surface` 实底（无背景图）；聊天页保持 §12 沉浸形态。

### 13.3 通用组件（ui/pages/common/PageKit）

`PageHeader`（汉堡 + LuzzyIcons 页图标 + 20sp Bold 标题 + 右侧动作槽，M3 TopAppBar 透明底）、
`SettingCard`（card 底 + hairline 边 + 16dp 圆角 + 分组标题）、`SettingRow`（leading icon +
标题 + 支撑文本 + trailing 控件/toggle/chevron）、`SectionTitle`（12sp uppercase hairlineStrong）、
`EmptyState`（居中图标 + 主副文）——命名对齐上游组件语义。

## 14 · 思考节点卡片 + 消息操作行（2026-09-12 修订：节点入气泡 + 逐节点时序）

> 修订依据 = 用户 2026-09-12 四条指示：① 节点必须位于**模型输出气泡内部**；② 子节点内容
> 过长时**卡内滚动**，不无限延伸；③ 子节点时序 = **自动展开 → 内容流式 → 完成后自动收起**；
> ④ 真流式（非模拟请求，逐字更新）。
>
> 形态考察来源 `docs/rikkahub-master/.../ui/components/message/`（ChainOfThought.kt /
> ChatMessageCot.kt / ChatMessageReasoning.kt / ChatMessageTools.kt / ChatMessageActions.kt /
> ChatMessageBranch.kt）。**形态对齐 rikkahub，语义按 LuzzyRP 自己的模型**——关键差异：
> rikkahub **无**「记忆注入」节点（其 memory 只是模型主动写记忆的工具调用），
> 而我们有 WebView 版 patch 031 的「记忆召回」节点，故第二类按我们自己的语义建模。

### 14.1 容器与位置（①）

- **位置**：思考卡是 AI 气泡（`AiMessagePanel`）内的**第一个子块**，与名牌、正文同处
  **同一块玻璃面板**——不再是气泡上方的独立卡。理由：思考属于这条回复本身。
- **容器**：内嵌子卡 —— 12dp 圆角 + `surfaceContainerHighest @ alpha 0.45` + hairline 边；
  **live 态**边宽 1.5dp、色 `primary @ 0.40`（生成中可见的「进行中」标识）。
  **不用玻璃**：气泡本体已是雾纸玻璃，内层再叠 haze 只会糊成一片。
- **时间线竖线** 1dp `#BEB6A8 @ 0.35` @ x=12dp（上下各留 18dp）；内容缩进 32dp 对齐 label。
- `animateContentSize` **只挂静态态**：live 态逐字增长期间若每增量都触发尺寸动画，
  就是明确的掉帧来源——live 时让内容自然撑开。
- 摘要行：live = `思考中…` + 点呼吸；完成 = `<首节点语义> · N 步`（≥2 节点时）
  + `再显示 N 步` / `收起` + chevron 旋转（0↔180°，200ms）。

### 14.2 三类节点

| # | 节点 | 图标（LuzzyIcons） | 标题 | 展开内容 | live 态 |
|---|------|------------------|------|---------|---------|
| ① | **工具调用** | `Sliders` | `调用工具 {名}` | 入参（等宽 10sp）+ 结果（等宽 11sp）；**参数逐 delta 写入** | 图标槽换点呼吸（`DotLoading` 10dp，alpha 0.3↔1，700ms Reverse） |
| ② | **记忆召回**（LuzzyRP 语义，patch 016/031 同源） | `Memory`（灯泡） | `记忆召回 · {N} 片`，extra = 相关度区间 `0.09~0.26` | 每片：`第 N 轮` chip + `相关度 x%` chip + 正文 | 检索是本地瞬时完成，故进入即完成态 |
| ③ | **头脑风暴**（模型 `reasoning` 字段） | `Info` | 生成中 `思考中…`；完成 `思考了 {x.x} 秒`（真实计时） | reasoning 正文，**逐 delta 写入** | 点呼吸 + 正文实时增长 |

### 14.3 逐节点时序（③ 的核心）

`activeIndex` = **当前正在产出内容的节点下标**；该节点自动展开、内容流入，下标移走即自动收起。

| 触发（真实事件） | activeIndex | 可见表现 |
|---|---|---|
| 会话检索完成（发请求之前） | 召回节点 | 该节点自动展开，展示真实分片与相关度区间 |
| 模型开始吐 `reasoning_content` | 头脑风暴节点 | 召回节点自动收起；该节点展开，正文**逐 delta 写入** |
| 模型发出 `tool_calls` | 工具调用节点 | 前者收起；参数区逐 delta 写入；本地执行完追加「结果」 |
| 正文开始（reasoning 结束） | `-1` | **全部自动收起** = 用户所说的「流式输出完成思考内容后自动折叠」；正文接管气泡 |
| finished / failed / 取消 | `-1` | 卡片整体收起为一行摘要，**节点随消息常驻**（不消失） |

- 实现：`ThinkingCard(nodes, isLive, activeIndex)`，`isOpen[i] = 用户手动覆盖 ?: (i == activeIndex)`。
- **用户手动点击优先于自动态**（`opens` map 记录覆盖），自动态不会覆盖用户意图。

### 14.4 溢出策略（② 的核心）

`NodeBodyMaxHeight = 200.dp` + `verticalScroll`：超出部分**卡内滚动**——不把消息流撑爆，
也不截断内容（对比 rikkahub 的 100dp 渐隐 Preview：我们选择「可滚动」而非「后文不可达」）。
实测：同一节点 body 内上滑 → 内层内容滚动、外层消息列表不动。

### 14.5 消息操作行（气泡正下方，左对齐 / 用户消息右对齐）

`FlowRow`（**不是 Row**：4 个图标 + 分支切换器在 336dp 气泡宽度下会溢出，FlowRow 自动换行）
间距 2dp，图标 16dp / `onSurfaceVariant` / 点击热区 32dp 圆。

| 序 | 图标 | 功能 |
|----|------|------|
| 1 | `Copy` | 复制全文 |
| 2 | `Refresh` | 重新生成 |
| 3 | `Edit` | 编辑 |
| 4 | `DotsHorizontal` | 更多 |
| 5 | `‹ n/m ›` | **多结果切换**：`branchCount > 1` 才出现；两端禁用态 alpha 0.5 |

**分支语义（重要）**：切换器**只在真有多个结果时出现**——P2 每次生成只有 1 条结果，
故当前不显示；P3 把「重新生成」实现为累积分支（`branchIndex/branchCount` 已进消息模型）后自然出现。
**气泡内不再渲染分支 chip**：同一信息显示两遍是缺陷（用户 2026-09-12 指认，已修）。

---

## 15 · 真流式链路（P2，2026-09-12）

**零模拟纪律**：界面上每一次变化都必须对应一次真实事件——没有 `delay()` 造的假打字，
没有预置的假节点，没有假工具结果。

### 15.1 链路结构

```
用户发送
  └─ RecallEngine.search(历史, 输入)            ← 本机真实检索（CJK 二元组 + 拉丁词重叠打分）
       └─ 命中 → 召回块注入 system + 时间线留「记忆召回」节点（真实分片/相关度）
  └─ ChatEngine.run(...) → OpenAiTransport → SseClient（OkHttp，真实 HTTP + SSE）
       ├─ delta.reasoning_content → Event.Reasoning → LiveTurn.reasoning += chunk（逐字）
       ├─ delta.tool_calls[...]   → Event.ToolCallArgs → 工具节点参数 += chunk（逐字）
       │    └─ finish_reason=tool_calls → WorldBookTool.execute(真实参数) → 结果回填
       │         └─ assistant(tool_calls) + tool(result) 入消息 → **再发一次真实请求**（最多 3 轮）
       ├─ delta.content           → Event.Content → LiveTurn.body += chunk（逐字）
       └─ finish_reason           → Event.Finished → 落盘为消息（节点随消息常驻）
```

**「1 字 = 1 次更新」的实现**：每个 `LlmDelta` 触发一次且仅一次状态追加（`LiveTurn.apply`），
不插值、不节流、不合并；界面看到的增长与 SSE 帧一一对应。引擎层保持纯 Kotlin（无 Android 依赖），
日志/追踪放在 UI 层（见 §15.4）。

### 15.2 三类节点的真实数据源

| 节点 | 数据源 | 真到什么程度 |
|------|--------|------------|
| 记忆召回 | `RecallEngine` 对**真实会话历史**做词面重叠检索 | 分片文本与相关度都是真算的；P4 换嵌入检索时替换实现即可 |
| 工具调用 | 模型**自主**发出 `tool_calls` → `WorldBookTool` 用**模型给的参数**检索角色世界书 | 参数由模型流式发出（实测 20~37 个增量分片）、结果由本机真实执行 |
| 头脑风暴 | SSE 帧的 `reasoning_content` 字段 | 真实逐字，非事后补写 |

> 语义如实记录：P2 的「记忆召回」是词面重叠检索（不需要嵌入模型、可离线、可单测），
> 与 WebView 版的向量召回在**用途**上同源、在**算法**上不同；P4 数据层建成后替换实现。

### 15.3 供应商配置

- 界面入口：输入岛右侧模型 chip → 「供应商配置」对话框（Base URL / API Key / 模型）。
- 存储：`SharedPreferences("luzzy_transport")`，**仅设备本地**；不入库、不进安装包、不写日志
  （展示一律 `maskKey()` 打码）。P4 迁 DataStore 并做多供应商管理。
- 端点拼接：`chatEndpoint()` 补 `/chat/completions`（已含路径或已含 `/v1` 两种写法都兼容）。
- 未配置时：发送被拦回配置对话框（不静默失败）；真实失败以 `ChatMessage.Error` 如实展示，
  不伪装成模型输出。

### 15.4 真机验证证据（模拟器 × 真实端点 × 4 次真请求）

| 轮次 | 记忆召回 | reasoning 增量 | 工具调用 | 参数增量 | 工具结果 | 正文增量 | 总事件 | 时长 |
|------|---------|---------------|---------|---------|---------|---------|--------|------|
| 1 | 1 | 19 | 1 | 20 | 1 | 108 | 151 | 4.0s |
| 2 | 1 | 323 | **2** | 37 | 2 | 106 | 472 | 5.5s |
| 3 | 1 | 139 | 1 | 21 | 1 | 111 | 275 | 5.7s |
| 4 | 1 | 23 | 1 | 20 | 1 | 140 | 187 | 2.8s |

- **正文 465 个增量中 267 个是单字符**（reasoning 504 个中 191 个单字符）→ 确实逐字到达、逐字追加。
- 工具参数分片实测（模型给的真实参数）：`{"keywords": ["钟楼", "苹果树", "来历", "种树"]}`；
  本地执行结果 `{"matched":["钟楼红苹果树"],"entries":1,...}` 被回填后，模型的回复确实引用了
  世界书内容（「三十年前一位路过的恶魔随手种下」「靠钟声与月光结果」）。
- 证据截图：`docs/design/verify-p2-final.png`（节点在气泡内且常驻）、
  `verify-p2-nodes-expanded.png`（三类节点）、`verify-p2-node-tool.png`（真实入参+结果）、
  `verify-p2-node-brainstorm.png` + `verify-p2-node-scroll.png`（200dp 卡内滚动前后）、
  `verify-p2-live-reasoning.png`（live 态节点内正文流式）。

### 15.5 开发注入钩子（仅 debug 源集）

模拟器只有英文 IME（`adb shell input text` 遇 CJK 直接抛 NPE），而本应用内容全是中文——
没有注入通道就无法对 UI 做端到端验证。故：

- `ui/DevHooks.kt`（main 源集）：两个恒为 null 的挂点（`inputInjector` / `sendTrigger`），
  **release 下无注册者、零行为**；
- `src/debug/`（不进 release 包）：`DevInputReceiver` 收 adb 广播后调用挂点。

```
adb shell am broadcast -a com.luzzymeow.luzzyrp.DEV_INPUT \
  -n com.luzzymeow.luzzyrp.debug/com.luzzymeow.luzzyrp.DevInputReceiver \
  --es text '那棵苹果树……' --ez send true
```

