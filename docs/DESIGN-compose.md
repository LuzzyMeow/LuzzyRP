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

- 界面入口：输入岛右侧模型 chip → 「供应商配置」对话框。字段（自上而下）：
  **Base URL / API Key（密文）/ 模型 / 上下文窗口（tokens）**，末尾一行「当前密钥：…」打码脚注。
- **上下文窗口**（B5，2026-09-13 加）：压缩（§25）唯一的门槛输入，默认 **32768**（保守floor，
  「填 0」= 关闭自动压缩）。它是**请求参数簇**的一员，故紧跟「模型」；「当前密钥」是脚注，留在末尾。
  非法输入（空 / 负号 / 超 Int）**不静默回退**：字段级 `isError` + 错误文案贴在字段上，并**拦住保存**
  （解析是纯函数 `TransportConfig.parseContextWindow`，单测钉住空串 / 千分位 / 负数 / 超 Int 四类）。
- 说明文字 12sp / 行高 17sp（caption 级，遵 §3 字号下限）；控件沿用本对话框既有的
  `OutlinedTextField` 与既有 token，**零新增颜色 / 组件 / 图标 / 动效**。
- 保存必须走 `initial.copy(...)`：新建 `TransportConfig` 会把不在这张表单上的字段
  （温度 / 最大输出 / 工具开关）**重置为默认值**——那是一次静默的配置丢失（2026-09-13 修）。
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

---

## 16 · Markdown 渲染（P2，2026-09-12）

### 16.1 语义基线 = 上游，不再自造

上游 WebView 版用 **marked v15（GFM + `breaks:true`）+ DOMPurify**：`*动作*` 是标准
**emphasis 斜体**、`「对白」`只是**普通文本**。P2 之前的「对白 / 动作 / 叙述」三分类
是自造的语义偏差（会把星号当字面量画出来），本轮已删除并换成真 Markdown 渲染。

### 16.2 实现与依赖

| 项 | 取值 | 说明 |
|---|---|---|
| 解析器 | `org.jetbrains:markdown:0.7.3` | **Apache-2.0**（与 AGPL-3.0 自有代码兼容），GFM flavour；rikkahub 同款 |
| 渲染 | **自写** AST → Compose | 不引第三方 Compose Markdown 渲染库（避免再叠一层版本耦合） |
| 解析时机 | **静态消息：同步 + 记忆化**（`MarkdownMemo` LRU 24）；**流式生成中：后台解析** | 见下方 16.2.1 —— 这是「滚动回看时气泡突然弹出」的修复点 |
| 解析成本 | **4090 字 / 6.29ms**（实测，`MarkdownParserTest` 的成本门 <60ms） | 同步路径只在首次遇到该内容时付费，之后滚动来回都是缓存命中 |
| 文本颜色 | 读 `LocalContentColor` | 引用块/嵌套结构只需改一次局部颜色即可整体变淡 |

#### 16.2.1 为什么静态消息必须同步解析（2026-09-12 用户实测反馈）

**症状**：从最底部往上滑回看较早的气泡时，该气泡「突然弹出」——先以矮壳出现、再撑开，
视觉割裂。

**根因**：LazyColumn 会把划出视口的气泡**销毁、滚回来重建**。原实现用
`produceState(initialValue = emptyList())` **异步**解析，于是重建的那一帧 `blocks` 为空
→ 气泡只画出名牌（矮壳）→ 解析完成后内容出现，叠上外层 `animateContentSize(200ms)`
就是肉眼可见的「弹出」。

**修复**：静态消息改为 `remember(content) { MarkdownMemo.blocksOf(content) }` ——
**首次组合那一帧内容就已就绪**，不存在空壳中间帧；记忆化保证同一内容只在首次付费
（滚动来回都是命中，LRU 24 足够覆盖一屏 + 预取）。流式生成中仍走后台解析
（内容每个增量都在变、必然 miss，不必占主线程；且流式期间文本本来就在长，无「弹出」观感）。

**守卫**：`MarkdownParserTest` 增一条不变式——**非空内容必产出块**（否则空壳仍可能出现）；
`MarkdownMemoTest` 钉住「同内容返回同一实例」与「容量有界」。列表条目同时补上稳定 key
（`分支id#下标`），避免切换分支时条目状态（如代码块展开态）泄漏。

### 16.3 排版 token（`ui/markdown/MarkdownStyle.kt`，唯一取值处）

| 元素 | 取值 | 依据 |
|---|---|---|
| 正文 | 13.5sp / 行高 23sp / PuHuiTi | 沿用气泡正文（§12） |
| h1 / h2 | 18sp / 16sp，**Lora** SemiBold | 品牌 display 族做标题，层级靠**字号 + 字族**双信号 |
| h3 / h4+ | 15sp / 13.5sp，PuHuiTi Medium | |
| 行内代码 | mono，`primary` 色 + `surfaceContainerHighest@0.7` 底 | |
| 围栏代码块 | mono 11.5sp/17sp，`surfaceContainerHighest@0.6` 底 + 10dp 圆角；**语言标签**在左上；**>10 行折叠**（对齐 rikkahub） | |
| 引用块 | 左侧 3dp `primary@0.45` 竖条 + 内容整体转 `onSurfaceVariant` | |
| 列表 | 每级缩进 14dp；无序符号按层级 • / ◦ / ▪ | 与 rikkahub 同做法 |
| 块间距 | 8dp（4/8dp 节奏） | pro-rules |
| 链接 | `primary` + 下划线，走 `AnnotatedString` 的 **LinkAnnotation**（可点、可无障碍播报）；**只放行 http/https** | |

> **与 rikkahub 的差异（如实记录）**：rikkahub 标题 24/22/20/18sp 配 16sp 正文；我们要放进
> **336dp 宽的气泡**且正文 13.5sp，直接套用会溢出换行 → 按 1.33/1.19/1.11 比例收窄。
> 本版不引 LaTeX / Mermaid。

### 16.4 流式容错（逐字上屏的必要条件）

| 输入状态 | 行为 | 依据 |
|---|---|---|
| 未闭合围栏 ` ```json\n{"a":1 ` | 仍产出代码块，`closed=false` → UI 左上显示「生成中…」 | 上游「未闭合按代码渲染到当前为止」同语义；这里把「没写完」显式化 |
| 未闭合强调符 `**他还没写完` | 不产出 Strong，**标记符原样保留**（不吞字符） | 单测钉死 |
| 半截链接 `[文字](htt` | 退化为普通文本 | 不猜 URL |
| HTML 块 | **按纯文本原样展示** | 上游把 HTML 塞 sandbox iframe 渲染；本版不做直通（安全 + 复杂度），P5 单独立项。原样展示让「未渲染」可见，不静默吞内容 |

**明确不做**：LaTeX / Mermaid（GFM 的 math 节点降级为纯文本）、图片（我们走 `image###`
私有格式 + 消息附件，P5）、表格的列宽自适应与复制工具栏（现为等宽列 + 表头加粗 + 行间 hairline）。

---

## 17 · 剧情分支列表（P2，2026-09-12）

### 17.1 这是我们的「会话列表」

**上游没有平铺会话列表**：一次「会话」= **角色 × 剧情分支**。上游的列表在
`ui-components.js` 的 `StoryBranchModal`（聊天页右上按钮打开）里，且画成 **SVG 路线图**
（节点卡片 + 连线），每项信息 = 分支名 / `N 楼 · x.x 字` / 「起点」/「当前」。
**本版按同一语义实现**（用户 2026-09-12 拍板；「平铺会话总览」归 P4 数据层一并设计）。

| 维度 | 上游 | 本版 |
|---|---|---|
| 入口 | 聊天页右上按钮 → 弹窗 | **聊天页顶栏按钮 → `ModalBottomSheet`**（同位置） |
| 树形表达 | SVG 路线图（节点 + 连线） | **层级缩进 + 树形连接线**（卡片内左侧留白处画竖脊 + 肘部；画在卡片外会被圆角裁掉——实测踩过） |
| 每项信息 | 分支名 / 楼数 · 字数 / 起点 / 当前 | 同（**全部真实算出**：楼数 = 消息数、字数 = 字符数） |
| 分叉来源 | 未在列表显示 | **显式一行**「自主线第 N 楼分出」（与统计同行会被动作按钮挤到换行——实测踩过） |
| 动作 | 进入 / 编辑（≤30 字）/ 删除（主线禁用） | 同；进入后**自动收起**（选完就看内容） |
| 主线约束 | 不可改名、不可删除 | 同（模型层 `BranchTree.rename/delete` 直接拒绝） |

### 17.2 数据与约束

- 模型：`chat/BranchModel.kt`（纯 Kotlin）——`ChatBranch(id, name, parentId, createdAt, isMain,
  forkFloor)` + `BranchStat(floorCount, wordCount)` + `BranchTree`（不可变，所有操作返回新实例）。
- 排序：**主线优先，其余按创建时间升序**（上游 `data-services.js` 同口径）。
- 删除：连同后代一并删除；当前分支被删则回退主线；**主线永不进删除路径**。
- 统计口径：字数 = 字符数（上游同样按字符计），≥10000 显示「x.x 万字」。
- **P2 阶段为内存态**（每条分支持有自己的消息列表；在分支 A 发送只进 A）。
  生成期间切到别的分支时，结果仍落在**发起的那条分支**上（`turnBranchId` 快照）。
- P4 换真实存储：按上游粒度整体存取（`rp_hub_chat_{角色}__branch__{分支}`）。

### 17.3 设计纪律落点

- **信息密度（huashu 高密度型）**：分支名 / 楼数 / 字数 / 分叉来源 / 起点·当前 —— 全是真实
  差异化信息，**零装饰图标**；唯一图标是顶部标题的「分支」（Heroicons v1 `share` = 三节点连线）。
- **触控目标（pro-rules）**：动作按钮一律 **48dp** 热区（图标 18dp）。同批修正了消息操作行
  原先 32dp 的不合规热区。
- **无障碍**：删除/重命名按钮的 `contentDescription` 带上分支名（「删除『教堂后墙』」），
  避免一排同名按钮；装饰性连线不进语义树（`drawBehind` 绘制，天然不参与无障碍）。
- **三方向门豁免**：本轮为**已选定方向（A · 织机 Loom）内的延续**（新页面沿用既有方向词汇：
  玻璃族/表面色阶/token 排版），符合 huashu「已选定方向后的迭代」豁免——记录见
  `docs/design/boards-v4/direction-approved-v4.md` 末节。

---

## 18 · 聊天页操作行与输入岛的真功能（P3 部分，2026-09-12）

> 用户指示：「给聊天页的所有气泡下的 icon 和输入框的 icon 做实质功能和组件 UI 可以吗，
> 还是要留到后面做」。答案是**分层**——只依赖已有消息/引擎的立刻做，依赖数据层或图片管线的
> 明确留到后续期，**并且不留「看着能点却没反应」的死图标**。

### 18.1 依赖判定（做与不做的分界）

| 图标 | 判定 | 依据 |
|---|---|---|
| 气泡：复制 / 编辑 / 重新生成 / 更多 | ✅ 本轮做 | 只依赖消息列表与既有聊天引擎 |
| 输入岛：模型 | ✅ 本轮做 | `GET {base}/models` 真实拉取 + 写回配置（`chat/ModelCatalog.kt`） |
| 输入岛：工具 | ✅ 本轮做 | 开关真实影响请求体（`TransportConfig.toolsEnabled`） |
| 输入岛：世界书 | ✅ 本轮做（**只读**） | 现有真实条目可展示；编辑属用户数据 |
| 输入岛：附件 | 🅿️ 入口保留，功能留 P5 | 需要图片管线（SAF + base64 + `image_url` part + 存储） |
| 输入岛：预设 | 🅿️ 入口保留，功能留 P4 | 预设是**用户数据**，无存储层做不出来 |
| 输入岛：工作区 | 🅿️ 入口保留，功能留 P5 | 上游是一整个特性（工件/文件工作区） |

**入口保留、点击给如实说明**（2026-09-12 用户指正后的定稿）：我曾把未实现的三项**从功能行撤掉**，
理由是「点不动的图标是欺骗」。用户当场纠正：「我说的臃肿不是叫你删掉组件啊，我让你重新设计一下
输入框的各部分内容」——**删组件是改需求，不是解决排版问题**。定稿：入口一律保留，
未实现的点击弹如实说明（「附件：需要 P5 的图片管线，届时开放」）——既不装死，也不改需求。

### 18.2 气泡操作行（`MessageActionRow`）

| 动作 | 真实现 | 验证 |
|---|---|---|
| 复制 | 系统剪贴板 `LocalClipboardManager` + Snackbar 反馈 | 代码路径 + Snackbar（Scaffold `snackbarHost`） |
| 重新生成 | **真实再跑一次请求**：以该消息之前的历史 + 上一条用户消息重发，结果作为**新候选**追加 | 真机：触发 recall/reasoning 全链路，切换器出现 `‹2/2›` |
| 编辑 | 就地编辑弹窗（Markdown 源码即所见；未修改时「保存」禁用） | 真机：标题「编辑这条回复」+ 预填源码 |
| 更多 | 下拉菜单：复制 Markdown 源码 / 删除此消息 / 删除此消息及之后 | 真机截图 |
| 多结果切换 `‹ n/m ›` | 数据模型为**候选列表**（`ChatMessage.Ai(results, index)`）；每次重新生成追加，可来回切换 | 真机：`‹2/2›` 且内容确为另一条新回复 |

- **重新生成期间就地渲染**：live 面板渲染在**该条消息的位置**（`regeneratingIndexState`），
  看起来是「原处重写」而不是凭空冒出新气泡。
- 用户消息**没有**「重新生成」（重生成是模型输出的动作），该按钮不出现。

### 18.3 输入岛：照 rikkahub 的架构重做（用户报「太臃肿」后的第三版定稿）

**症状**：第一版是「5 个 48dp 图标铺满一行 + 行内实心模型胶囊」→ 超出岛宽，胶囊被挤成两行并
**压到图标行上**（截图 `verify-p3-island-before.png`）。

**❌ 第一次修偏（第一版）**：把「太挤」误判成「组件太多」，**删掉了附件/预设/工作区三个入口**。
用户指正：「**我说的臃肿不是叫你删掉组件啊 我让你重新设计一下输入框的各部分内容**」。
——把「太挤」误判成「东西太多」、用删功能解决排版问题，是改需求。

**✅ 定稿：照 rikkahub 的架构**（用户指示「你可以看一下 rikkahub 的实现方式」）。
考察 `docs/rikkahub-master/.../ui/components/ai/ChatInput.kt`（AGPL-3.0；本项目已整体转
AGPL-3.0，形态复用许可兼容。**本轮采用其架构与节拍，未逐行复制代码**），它的三条结构性答案：

| rikkahub 的做法 | 解决什么 | 我们的落地 |
|---|---|---|
| **输入框满宽独占一行，放在最上**；动作行在其**下方** | 输入框是第一眼落点；长文本有完整宽度；动作贴键盘侧（拇指更近） | 同（此前是动作行在上、输入与发送同行，输入被发送键挤） |
| 动作行左簇 **`weight(1f) + horizontalScroll`** | **溢出问题从根上消失**：按钮再多也不换行、不挤压固定件——这才是「拥挤/溢出」的解法 | 同（此前靠「算宽度刚好放下」，脆） |
| 模型/推理强度 **只显示图标**；次要动作收进「＋ 更多」 | 名字不占宽度 | 半采用：模型 = **图标(`ic_lz_chip`) + 短名（限宽 78dp 省略）**——名字对用户有用，可滚动簇保证它不挤压别人 |
| 容器 padding 8/4、行距 2dp；按钮 30dp 圆形 | 紧凑节拍 | padding 8/4 + 行距 2dp 照用；**按钮取 44dp**（比 rikkahub 的 30dp 大：Android 触控下限是 48dp，44dp 是 iOS 下限、也是同类常见值；因左簇可滑，宽度放宽不再有代价） |

定稿结构：
```
┌──────────────────────────────────────────────────────────┐
│ 写点什么……（满宽，最多 5 行）                            │  ← 输入行（上）
│ [◈deepseek-fl…][＋][预设][世界书][工具][工作区] →→→   ( ➤ ) │  ← 动作行（下）：左簇可横滑 + 发送固定右端
└──────────────────────────────────────────────────────────┘
```

**验证**：模拟器 150% 系统字号下**不破版**（模型名继续省略、动作行与发送键完好、放不下则横滑）
——截图 `verify-p3-fontscale-150.png`；这正是「可横滑」结构买来的性质（pro-rules：大字号不破版）。

### 18.4 输入岛真面板

- **模型面板**（`ModelPickerSheet`）：真实 `GET {base}/models`，展示端点、当前模型、加载中/失败/空列表
  三种如实状态；选中即写回并持久化。真机实测返回 `deepseek-flash` / `deepseek-v4-pro`。
- **工具面板**（`ToolsSheet`）：`world_info_lookup` 开关 + **状态文案随开关变化**（说明请求体是否带 tools）；
  真机 A/B：**关→0 工具事件；开→tool_start + 21 个参数分片 + 结果**。
- **世界书面板**（`WorldBookSheet`）：只读列出当前生效的真实条目（标题 + 关键词 chips + 正文），
  并明说「编辑与多角色归 P4 数据层」。

### 18.5 协议噪声过滤（真机实测发现，`chat/ToolMarkupFilter.kt`）

开启工具后，DeepSeek 除正常 `tool_calls` 之外还会把工具调用用自家 **DSML 文本标记**复述一遍，
这段标记会作为 `content` **流进回复正文**（真机截图存档），用户看到 `<| | DSML | | invoke …>`。

- **位置**：放在**引擎层**——wire fidelity 纪律要求 OpenAI 路径逐字节转发，不能改帧内容；
  「哪些内容是给用户看的」是引擎的职责。
- **删的是整块**：只删标签会把工具参数（`钟楼 红苹果树 来历 种下`）留在正文里（单测抓到）。
- **绝不牺牲逐字流式**：普通文本一个字都不扣、逐片即刻放行；只有遇到 `<` 且尚未见到 `>` 的
  半截标签才短暂扣住（单测钉死「1 字 = 1 次更新」）。
- **未知情况的选择**：块一直等不到闭标签时，宁可**漏出噪声也不吞正文**（超限或收尾时去标签放回）。


---

## 19 · 聊天链路验收标准（P2/P3 收口，2026-09-12）

> 本节是**可执行**的验收表：每项给出「怎么操作 → 期望什么 → 拿什么当证据」。
> 依据是 rikkahub 与上游两套实现的对照（`docs/rikkahub-master` / `rp-hub-reference`）。
> 纪律：**验收标准写死在此**，不再靠对话里的口头结论。

### 19.1 已收口项（逐项可复现）

| # | 项目 | 操作步骤 | 期望 | 证据 |
|---|---|---|---|---|
| 1 | **键盘避让** | 点输入框唤出键盘 | 输入岛完整可见、输入框不被遮 | 截图 `verify-p4-ime-{before,after}.png`（修复前是**完全被盖住**） |
| 2 | **滚动跟随** | 底部 → 上滑 | 自动跟随停止，不再抢滚动 | 落盘截图 + 门禁日志（`atBottom=false bot>vpEnd`） |
| 3 | **回到底部** | 上滑离开底部 | 右下出现 40dp 圆钮（进入 200/退出 140ms、scale 起点 0.9） | 截图 `verify-p4-scrollbottom.png` |
| 4 | **新内容提示** | 离开底部后触发一轮生成 | 圆钮右上角出现主色点；点击后清除 | 截图 `verify-p4-newcontent-dot.png` |
| 5 | **错误卡栈** | 断网发一条 | 输入岛上方出现错误卡（可复制/关闭/多条全部清除）；**消息列表与分支楼数均无污染** | 单测（消息类型只有 Ai/User）+ 模拟器截图 |
| 6 | **用量脚注** | 正常发一条 | 操作行下方出现 `输入 N（缓存 M） · 输出 K · x.xs · n tok/s`；无数据显示时不占行 | 截图 `verify-p4-nerdline.png`（实测 输入742（缓存512）· 输出134 · 2.3s · 59 tok/s） |
| 7 | **截断可见化** | 把最大输出调到很小发长请求 | 脚注追加告警色「已截断（达到输出上限）」+ 一次 Snackbar 提示 | 单测（`length`/`max_tokens`/大小写）+ 模拟器 |
| 8 | **删除确认** | ⋯ → 删除此消息 / 删除此消息及之后 | 先弹确认框，写明条数与不可恢复 | 截图 `verify-p4-delete-confirm.png` |
| 9 | **编辑后重跑** | 编辑用户消息 → 保存 | 弹确认「按新内容重新生成？（其后楼层会被删除）」；选「只改内容」则只改文本 | 模拟器走通（两条路径） |
| 10 | **正文选中复制** | 长按静止消息 | 可选中/复制；**流式期间长按不崩**（期间不挂 SelectionContainer） | 代码纪律 + 模拟器（流式中长按） |

### 19.2 回归清单（每轮聊天相关改动后手动走一遍）

1. 冷启动 → 落在最新一轮（开页贴底）；
2. 唤出键盘 → 输入岛可见；收起 → 恢复；
3. 发送 → 逐字流式（引擎 emit 粒度 = SSE 粒度）→ 思考节点自动展开/流式/收起 → 完成常驻；
4. 工具开启时出现「调用工具」节点，关闭时**不出现**；
5. 停止 → 保留已到达正文；重新生成 → 生成第 2 个候选，`‹2/2›` 可来回切；
6. 上滑回看 → 跟随停止、回底按钮出现、点击回底；
7. 切分支 → 对话内容整体更换、统计随之变化；
8. 编辑（用户消息 / 助手消息）、删除（单条 / 及其后）→ 确认框语义正确；
9. 断网发送 → 错误卡（不污染消息与统计）；
10. 亮/暗 + 150% 字号走查上述 1-9（不破版）。

### 19.3 门禁（自动化）

**一条命令**：`ANDROID_SERIAL=emulator-5554 ./gradlew checkChat`
（= 设备门禁 `verifyEmulatorDevice` → 单测 → 仪器化 UI 测试；详细手册见 `docs/CHAT-REGRESSION.md`）

- **纯逻辑单测 304 例全绿**：`chat/`（引擎/检索/工具/标记过滤/用量/分支模型）、
  `ui/pages/chat/ChatMessageTest`（模型与候选）、**`ChatTurnStateTest`（状态机 10 例，原为零覆盖）**、
  `ui/markdown/*`。
- **仪器化 UI 测试 7 例全绿**（Stage 0 新增能力）：未配置→弹配置框 / 发送键状态 / 流式上屏+用量脚注 /
  失败→错误卡且不入列表 / 删除先确认 / 世界书真条目 / 工具开关联动。
  **基座上线即抓到 1 个真缺陷**：用户消息的删除绕过确认框（见 `docs/design/regression-p3.md` §2）。
- **真机门禁（硬性）**：`connectedDebugAndroidTest` 默认会在**所有已连接设备**上安装执行——
  曾因此把测试件指向真机（AGP 报告里的设备名 `A9210`）。现由 `verifyEmulatorDevice` 强制
  `ANDROID_SERIAL=emulator-*`，否则任务直接失败（负向测试已验）。
- 真实 SSE 节奏 / 断网 / 冷启动 / 手势等 6 项仍属人工，逐项归属见
  `docs/design/regression-p3.md` §1（十步全部有明确归属，无「大概没问题」）。

---

## 20 · 数据层与迁移验收标准（P4 收口，2026-09-13）

> 与 §19 同格式：**怎么操作 → 期望什么 → 拿什么当证据**。
> 设计真源在 `docs/DESIGN-migration.md`（键形态 / 12 条坑 / 存储选型 / 通道协议）；
> 本节是**验收面**，写死之后不再靠口头结论。

### 20.1 已收口项

| # | 项目 | 操作步骤 | 期望 | 证据（命令 / 文件） |
|---|---|---|---|---|
| 1 | **迁移器语义**（12 条坑） | 用真夹具跑迁移器 | 角色 5 / 分支 6 / 会话 5 / 消息 16 / 向量记忆 4 / 经典 4 / 世界书 4 / 正则 2 / 预设 19 / 跳过的坏记录 0 | `LegacyMigratorTest`（JVM，22 例） |
| 2 | **幂等** | 同一夹具跑两次 | `MigratedData` **逐字段相等**（缺 uuid 的角色按内容哈希补，不是随机 UUID） | 同上一例 |
| 3 | **坏数据只跳过不中断** | 塞入畸形记录 | 好数据照常迁完；每条坏记录有 `key + reason` | 同上一例 |
| 4 | **分块协议** | 分块回传（含 `?chunk=8000` 强制的 11 块） | 序号必须连续；拼接结果与单块版**内容完全一致**；缺块/空导出判失败 | `MigrationInboxTest`（9 例）+ 设备实测 §20.2 |
| 5 | **导出器与夹具同解** | 设备上跑 `ext/luzzy-migrate.html` | 导出物与测试夹具 **31/32 键逐字节相同**（唯一差异＝夹具中被脱敏的两个密钥字段，长度保留） | `tools/mig-fixture/run-exporter.mjs` |
| 6 | **通道健康检查** | 导出页第一步 | 两库都读不到键时**明确失败**（不是静默导出空文件） | 页面 `#stage=失败` + `migrateError` 日志 |
| 7 | **数据层运行时** | 真机/模拟器开库、事务、关库重开 | 开库 / 事务 / 索引查询 / 关库重开全通过 | `LuzzyStoreTest`（仪器化 10 例） |
| 8 | **会话持久化** | 启动即读 → 发送/编辑/删除 → 关连接重开 | 展示存储内容而非演示数据；改动落盘；**编辑只改 content，payload 一字不动**；重开后原历史/分支/思考节点都在 | `ChatPersistenceTest`（仪器化 5 例） |
| 9 | **设置持久化** | 切亮/暗 → 重启 | 记住选择；「跟随系统」是默认；旧设置**只搬一次**且不覆盖用户在新版改过的值 | `SettingsStoreTest`（仪器化 5 例）+ `LegacySettingsReaderTest`（JVM 8 例） |
| 10 | **迁移入口** | 清空新库后启动 Compose 界面 | 日志：`导出页回调 ok=true` → `迁移完成：已从旧版迁移：角色 5 · 会话 16 条 · 记忆 8 · 世界书 4 · 预设 19`；界面顶栏显示迁移进来的角色 | 模拟器 logcat `LuzzyMigrate`（与第 1 项**互相印证**） |
| 11 | **迁移只做一次** | 再次启动 | 无迁移日志、数据仍在 | 同上 |
| 12 | **会话总览数据层** | 调 `overview()` | 每个角色 × 每个分支各一条摘要（主线优先、空分支也在），条数与末条预览均来自消息表 | `LuzzyStoreTest.sessionOverviewListsEveryCharacterAndBranch` |
| 13 | **覆盖安装数据保留**（模拟器） | 同签名 `install -r` 新包 → 启动 | 旧 WebView 数据完好、迁移照常完成 | 本轮实测（见 §20.2） |

### 20.2 已实测的设备证据

- **模拟器（emulator-5554）**：清空新库 → 启动 Compose 界面 → `角色 5 · 会话 16 条 · 记忆 8 · 世界书 4 · 预设 19`
  （与 JVM 夹具测试期望完全一致）→ 重启不再迁移 → 顶栏显示迁移进来的角色（`last_active_char=1` 的换算生效）。
- **覆盖安装**：同签名 `install -r` 后旧 WebView 库（`RPHubDB` 29 键 + `SillyTavernDB` 3 键）完好，迁移照常完成。

### 20.3 尚未验收 / 待办（**不得当作已完成**）

| # | 项目 | 为什么还没做 |
|---|---|---|
| 1 | **真机覆盖安装** | 需要把 v3.0 包装到用户日常机（小米 A9210）。虽同签名、数据保留、且迁移只读旧库，但那是**用户的日常应用**——留给用户自己执行或明确授权（命令见 §20.4）。 |
| 2 | ~~跨角色平铺总览页~~ | **已完成（2026-09-13）**：三方向设计门已过、用户选 B·分组行 → 见 §21。 |
| 3 | **预设 / 世界书编辑** | 现在是只读列表；编辑表单属新增交互 → 同样需要设计流程。 |
| 4 | **用户可调字号** | 旧数据里的 `fontSize` 已能读出（`LegacySettingsReader`），但**用户可调字号属字体排版** → 需要设计流程；现状只有「系统 150% 字号不破版」这条鲁棒性要求。 |
| 5 | **迁移报告页** | 现在只用既有 Snackbar 提示一行（明细在 kv `legacy.migrationReport` 与日志）；专门页面属视觉产出 → 需要设计流程。 |
| 6 | ~~正文 `{{char}}`/`{{user}}` 占位符替换~~ | **已完成（2026-09-13）**：渲染期替换（存储不动）→ 见 §22.2。 |
| 7 | **顶栏头像/背景用真实角色图** | 同上，需要位图解码接入；归 P5。 |

### 20.4 真机覆盖安装步骤（用户执行，或明确授权后由 Agent 执行）

```bash
./gradlew :app:assembleRelease
apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk   # 必须与上一版指纹一致
adb -s <真机序号> install -r app/build/outputs/apk/release/app-release.apk      # 同签名覆盖，数据保留
adb -s <真机序号> shell am start -n com.luzzymeow.luzzyrp/.ui.ComposeActivity  # 首次进 Compose 界面触发迁移
adb -s <真机序号> logcat -s LuzzyMigrate                                        # 期望看到「迁移完成：…」
```

回滚：装回原 APK（同签名）即可——**旧数据从未被修改**（迁移只读旧库，见 `DESIGN-migration` §1 G5）。

---

## 21 · 会话总览页（P4-C，2026-09-13）

> 设计门：**已过**。三方向真实渲染与用户选择原话见
> [`design/boards-v5/direction-approved-v5.md`](design/boards-v5/direction-approved-v5.md)
> （方向 **B · 分组行**，逻辑二「现实参照 = Things 3」）。spec 见 `boards-v5/SPEC.md`。

### 21.1 已实现形态

| 项 | 取值 |
|---|---|
| 骨架 | **角色 = 粘性组头**（monogram + 名 + 会话数）／**分支 = 等高行**（左分支名、右条数、下方单行预览） |
| 行高 | ≥68dp（触控基线 ≥44dp） |
| 预览 | 取**最后一条用户发言**（用户拍板）；无用户发言时回落末条正文；**已剥内联 CoT**（见 §22.2） |
| 空会话 | 可见，斜体「还没开始」——用户会用它想起「我本来想开这条线」 |
| 当前会话 | 分支名转 primary + 行尾 6dp 圆点（本屏唯一的 accent 用法） |
| 头像 | 真实图（P5 接位图解码）／无图显示**首字 monogram**（真实降级路径，不是占位色块） |
| 入口 | **聊天页顶栏**「全部会话」（用户拍板）；`LuzzyRoute.Sessions` 带 `inDrawer = false`，**侧栏不加项** |
| 点一行 | 写 kv（当前角色 + 当前分支）→ 回聊天页；聊天页按 kv 装载 ⇒「点一行 = 回到那一段」，跨页不传状态 |
| 数据源 | `ChatSessionRepository.overview()`：**恒定 3 次查询**（角色 + 全部分支 + 全会话聚合），与数据集规模无关（§22.1） |

### 21.2 未做的部分（如实登记）

- **顶栏以外的入口**（侧栏项）**不做**——用户明确选了「聊天页顶栏入口」。
- 排序/筛选/搜索：**没有**。这一页是「目录」，不是管理器；要管理去角色页。
- 真实角色头像的解码仍是 P5（现在显示 monogram，属设计内降级）。

---

## 22 · 性能专项（2026-09-13，P4-C）

> 触发：用户问「是不是对性能取舍太过严重？」→ 先量再改。**没有数字就不优化**。

### 22.1 数据层：把「每次读」上的代价压掉

专用剖面 `PerfProfileTest`（仪器化，30 张卡 / 90 条会话 / **1.24 万条消息**；
预热 1 次 + 测 3 次取中位数）：

| 操作 | 优化前 | 优化后 | 改了什么 |
|---|---|---|---|
| `overview(90 条会话)` | **332 ms** | **9 ms** | 从「每角色查分支 + 每会话查 3 次」（3N 次挂起查询）改为**恒定 3 次查询**：`branches.all()` + 一条 `GROUP BY` 聚合（内层取条数与末条下标，两个 `LEFT JOIN` 靠 `scopeId` 索引取回末条正文/末条用户发言；**未用窗口函数**，API 26 自带 SQLite 3.18 就支持） |
| `load(重卡 6000 条 / 3 分支)` | 51 ms | **37 ms** | `load()` 原本把该角色**所有分支的全部消息**读进内存；改为**只装当前分支**，其余由 `loadBranch()` 在切换时按需装入（切换成本 4~5ms） |
| `load(轻卡 200 条)` | 17 ms | 11 ms | 同上（不再顺带读两条空分支） |
| `append(1 条)` / `characters(30 张全量 payload)` | 4 / 2 ms | 6 / 1 ms | 未动（噪声内） |

`characters()` 的 payload 列**不参与解析**（Room 只把字符串读出来，JSON 解在真正用到时才做），
所以「30 张全量 payload 2ms」——**原以为这是热点的猜测被数字否掉了**。

### 22.2 渲染侧：一个真实缺陷 + 一个真实浪费

1. **思维链被当正文渲染（真实缺陷，已修）**：上游把 CoT**存在正文里**
   （`<thinking>…</thinking>`，靠 `parseCot()` 渲染期剥离），我们当初只认独立的 `reasoning` 字段，
   于是迁移进来的消息**思维链跑进正文、思考节点是空的**。新增 `CotParser`（上游语义的 Kotlin 移植：
   成对栈+嵌套、围栏/行内码/HTML 块/注释里的标签不算、流式半截标签不显示）→
   载入时把内联 CoT 变成思考节点、正文用 `body`（剥 CoT）；编辑时用 `rewrap` 把思维链按原样装回，
   免得「改几个字」把那次生成的思考记录抹掉。
2. **每帧重解析（真实浪费，已修）**：`body` 一度做成 getter → **每次重组都把 1~2KB 正文重新正则扫一遍**
   （滚动掉帧有它一份）。改为在 `AiResult` **构造时算一次** + `CotParser` 加**有界缓存**（上限 64，对应上游 `parseCotCache`）。
   实测：**冷解析 193µs / 热命中 0.44µs**（1920 字文本）。
3. `{{char}}`/`{{user}}` 占位符在**渲染期**替换（上游只替换 `{{user}}`，`{{char}}` 是它也没做的一步）：
   存储不动、`raw` 保持逐字一致（迁移保真 + 发给模型的上下文不变）。

### 22.3 环境：那 250ms/帧其实是模拟器**内存饿死**（不是应用）

用户反馈「看你测的时候那么卡」。实测三步定性：

| 证据 | 数值 |
|---|---|
| 应用 chat 页滚动（原始 AVD） | 中位帧 **250ms**、掉帧 **88%** |
| **系统设置页同输入**（对照基线） | 中位帧 **200ms**、掉帧 **72%** ⇒ **不是应用的问题** |
| AVD `hw.ramSize` | **1536M**（1080×2400/420dpi 下长期换页） |

把 RAM 提到 **4096M** 后（GPU 配置保持原样，理由见下）：

| 对象 | 中位帧 | 掉帧 |
|---|---|---|
| 系统设置页 | **17 ms** | 2.2% |
| 本应用聊天页 | **17 ms** | 2.6% |
| 整套仪器化测试耗时 | 2m40s~3m28s | → **54s** |

⇒ 结论：**卡的主因是模拟器内存，其次是我当时并行跑的 Gradle/安装/截图**。
连带解释了之前几次「偶发红」（同一批用例单跑绿、整套跑红）。两条纪律已写入 `AGENTS.md` §7 坑表：
**① 卡顿先做系统应用基线对照再决定查谁；② 仪器化测试莫名超时先看模拟器资源**。

**GPU 加速在这台机器上不可用**：`-gpu host` 刷屏 `Failed to find ColorBuffer` 且永远起不来、
`-gpu angle_indirect` 报 `Failed to load opengl32sw` 后崩溃 → 只有原始组合能启动。
因此这里的帧数字**只作相对比较**（同一模拟器内的 A/B），**绝对值不代表真机**；
用户感知的那一档要用真机 CDP + `dumpsys gfxinfo` 测（`AGENTS.md` §6.1）。

---

## 23 · 预设 / 世界书编辑（P4-C 4.2，2026-09-13 · 已实现）

> 计划与数据裁决：`docs/PLAN-v3.0-presets-worldbook.md`；设计门（**用户豁免三方向**）：
> `docs/design/boards-v6/direction-approved-v6.md`。本节只写**设计落点**与**实现后的实测状态**。

### 23.1 方向：纸页清单 + 抽页编辑（单方向，用户豁免三方向门）

- **列表 = 目录**：一行一个条目（名称 + 徽标组 + 触发说明 + 启停 + 「⋯」菜单），密度服从扫读；
- **编辑 = 抽页**：这一条被抽出来铺满全屏（表单），**正文再抽一层**（`LongTextEditorDialog`）——
  真实正文可达 **3.7KB**（「自动生图」），塞进表单里改是折磨；且「可滚动文本域套在可滚动列表里」
  是嵌套滚动反模式（Compose 栈规约 #29）。
- 视觉母题落地一句话：**「条目即纸页」**——目录项 → 单页 → 正文页，三层都是同一张纸。

### 23.2 组件与纪律（不新造）

| 项 | 落点 |
|---|---|
| 共用件 | `PageKit`（`SettingCard`/`SectionTitle`/`BadgeChip`/`EmptyState`）+ 新增 `EntryCard`/`EntryBadge`/`EntryMenuAction` |
| 编辑器壳 | `ui/pages/common/EditorKit.kt`：`EditorHeader`/`FieldLabel`/`ToggleRow`/`SegmentChips`/`PrimaryButton`/`LongTextEditorDialog`（**两个编辑器共用一份正文编辑器**，防漂移） |
| 动效 | 沿用 `Motion`（进入 200 / 退出 140 / `cubic-bezier(0.23,1,0.32,1)`），未自造时长 |
| 触控 | 开关视觉 44×24 不变，热区用 `minimumInteractiveComponentSize()` 撑到 **48dp**（Android 下限） |
| 无障碍 | 开关带 `Role.Switch` + 「启用 〈名称〉」标签；每个「⋯」带「〈名称〉的更多操作」；**颜色不是唯一指示**（徽标一律带文字） |
| 排序 | **不做拖拽**：菜单「上移/下移」——pro-rules 要求「拖拽必须有非拖拽替代」，本批直接用可达的那一种 |
| 诚实边界 | 两个页面与两个聊天面板都写着「本版只管管理，检索注入 / 拼进请求在 P5 接入」 |

### 23.3 实现后实测（证据）

| 项 | 结果 |
|---|---|
| 门禁 | `ANDROID_SERIAL=emulator-5554 ./gradlew checkChat` → **单测全绿 + 仪器化 65/65 绿**（新页面/编辑器/仓库共 31 例新增） |
| 设备端真实数据 | 世界书页显示 **全局条目 · 2**（自动生图【全局·已停用·常驻】/ 全局：语言风格【全局·概率 80%】）+ **谢昭 绑定 · 0**；预设页显示 **19 条**真实预设（破限 / 破限预注入 ×4 / 防抢话 / 防重复 …），角色徽标与正文摘录均正确 |
| 聊天侧 | 世界书面板标题即为「**2 条**」并列出真库条目（不再是演示角色的内置书） |
| 截图（亮/暗） | `verify-p4-worldinfo-light.png` · `-dark.png`（世界书页）／`verify-p4-worldeditor-light.png`（条目编辑器）／`verify-p4-presets-light.png` · `-dark.png`（预设页）／`verify-p4-preseteditor-light.png` · `verify-p4-longtexteditor-light.png`（二级正文编辑器）／`verify-p4-worldbooksheet-dark.png`（聊天面板） |
| 目测走过的项 | 空态 / 分组（含 0 条的绑定组）/ 概率与常驻徽标 / 停用态 / 滑杆真值（扫描深度 2、最大扫描深度「不限制」）/ 亮暗对比 / 二级编辑器 |

### 23.4 实现中发现并修掉的两处缺陷（如实登记）

1. **UI 文案里的 Markdown 星号**：四处说明文字写了 `**检索注入**`，而 Compose `Text` 不解析 Markdown
   → 用户会看到光秃秃的 `**`。**这是截图目测抓到的**（仪器化测试与单测都看不见字面星号）——
   已全部改成「」。教训：**文案里的排版记号要按目标渲染器写，不能照抄文档习惯**。
2. **假绿判据（门禁稳定性）**：`SessionsPageTest.tappingARowRemembersThatSession` 曾在冷启动首跑偶发红，
   报 `attempt to re-open an already-closed object`。真因**不是超时**：样例里 `char-1` 的
   `activeBranchId` **本来就是 b1**，而判据正是「== b1」→ 写入还没发生判据就已经成立（**假绿**），
   挂起的写入于是跑到测试结束、库被关掉之后才执行。修法：**先把当前分支拨到 main**，
   再点 b1 那行，让判据能区分「点之前/点之后」。纪律与 `substring=true` 那次同源——
   **判据必须能区分前后状态**。

---

## 24 · 请求组装与缓存（P5-A，2026-09-13）

### 24.1 第一原则

> **"Model-visible ⟺ durably referenced."** 前缀缓存稳定是**推论**而不是目标：
> 请求若是会话日志的纯函数，且日志只追加，则「本轮请求 = 上轮请求的严格延伸」是**涌现**的。
> —— DSH `.agents/notes/implemented/architecture/2026-07-05-reconstructable-requests.md`

落成三条可执行约束（每条都有门禁）：**① 请求 = 状态的纯函数**（`PromptAssembler` +
`ui/pages/chat/RequestBuilder.kt`，两者都是纯 Kotlin）；**② 历史永不改写**（历史段不参与相邻同 role
合并：合并会让一条消息的内容取决于它的邻居，下一轮邻居一变就改写了已进历史的字节）；
**③ 凡会变的事实逐出 system**，改成「变了才追加」的尾部快照。

### 24.2 三方分工（谁负责什么，一处不许有两份）

| 层 | 文件 | 职责 | 为什么在这一层 |
|---|---|---|---|
| 组装 | `chat/PromptAssembler.kt`（纯函数） | system 命名 section（order + 名称字典序决定化）/ 预设 / 前置 / 开场白 / 历史 / 快照 / 本轮输入 | 组装一旦留在引擎里，「前缀是否纯追加」就只能靠发请求看结果 |
| 计划 | `ui/pages/chat/RequestBuilder.kt`（纯函数） | **请求长什么样** + **本轮按什么顺序落盘什么**，同一个返回值给出 | 调用方没有机会「只做对一半」（§24.4） |
| 传输 | `chat/AgentLoop.kt` | 发出去 → 收事件 → 工具续跑 → 收尾 | 引擎只面对**已装配好**的 `ChatRequest`，不持有第二份会话状态 |

### 24.3 稳定块 / 易变块（七位置：4 保真 + 3 改道）

| 上游 position | 是否漂移 | 本版处置 |
|---|---|---|
| `system_top` / `global_note` / `before_char` / `after_char` | 否 | **照上游语义进 system 稳定块** |
| `at_depth`（距尾深度插入） | 是 | **改尾部快照**（距尾第 N 条在对话变长后必然指向不同位置） |
| `user_top` / `assistant_top`（就地改写某条消息） | 是 | **改尾部快照**（那条消息每轮被重写 = 前缀每轮断在那里） |

改道的三条**如实登记为偏离**，并在世界书编辑器里标注「这两个位置在本版按尾部快照执行」。
去重口径同 DSH：**内容没变就一条都不发**（`RuntimeSnapshots.project` 返回 null）。

### 24.4 两张「隐藏行」（存储里有、界面上没有）

| 行 | 作用 | 位置语义 |
|---|---|---|
| `ChatMessage.Snapshot` | 运行时上下文尾部快照 | 在请求里位于「历史之后、本轮输入之前」→ **存储里必须也在那里**（会话 73 的第一次回退就是这条错了：快照挂在列表末尾，下一轮前缀照样断，且不报错） |
| `ChatMessage.Compacted` | 压缩水位线（§25） | 「**这条之前**的历史都被它取代」——行位置语义，对编辑 / 删除 / 分支天然免疫；删掉它 = 恢复整段历史（自愈） |

两张行都不渲染（`visibleMessages()` 滤掉）、都不进楼数统计（SQL 只数 `user`/`assistant`）、
都照常进请求。判据一律落在**独立类型**上，不靠字符串前缀去猜。

### 24.5 观测层（只统计，不干预）

`chat/CacheObserver.kt` 与 WebView 版 `ext/luzzy-prefix-guard.js` 同口径：
每轮算「与上一轮 messages 的**公共前缀字符数**」→ 单轮占比；累计取**加权**占比；
命中率 = `Σcached / Σprompt`（供应商没给 cached 就只加分母，不把未知当 0）；
另比一个**请求头指纹**（协议 + 端点 + 模型 + 采样值 + 工具集哈希，**不含 apiKey**）——
它变了就是缓存纪元断裂，是验收负控的判据。

### 24.6 验收与实测

| 判据 | 门槛 | 实测 |
|---|---|---|
| 连续五轮 `avgCommonRatio` | ≥ 0.95 | **1.0000**（JVM 全链路 `BatchACacheAcceptanceTest`） |
| 真机连续两轮 | ≥ 0.95 | **1.0000**、该轮命中 `输入 10,956（缓存 9,756）` = **89.0%**、纪元变化 0、`落盘 idx=5 顺序=Snapshot→User` |
| 负控（改预设 + 换模型） | 当轮 < 0.5 | **0.0000**、累计跌到 0.6152、纪元变化 1 |

**天花板（如实登记）**：`avgCommonRatio` 只看「相邻两轮」，多轮后的漂移要靠累计值读；
压缩（§25）会**真实切断一次**前缀，这不可避免，但它只切一次。

---

## 25 · Agent Loop（P5-B，2026-09-13）

### 25.1 两级循环与终止

`chat/AgentLoop.kt`：**turn** = 一次用户意图（一次点击到彻底停下）；**step** = 一次「请求 → 工具 → 回填」。
全程 `while` **非递归**（递归会把「第几步」藏进调用栈，中断与恢复都要靠猜）。

| 情形 | 结束原因 |
|---|---|
| 没有 tool_call（**主终止条件**） | `completed` |
| 撞输出上限（`length` / `max_tokens`） | `max_tokens`（**粘性**：即使同时请求了工具也不再续跑） |
| step 达 50 | `step_limit`（**我们的加法**：DSH 靠外部监督，我们一次点击没有外部刹车） |
| 点停止 / 协程被取消 | `aborted`（未启动的调用补合成结果）+ 已收到的正文以 `interrupted` 落库 |
| 传输/协议错误 | `error` |

### 25.2 工具配对（硬约束）

结果**按模型给出的顺序**回填；abort 时给未启动的调用补
`Error: tool call aborted before dispatch`；**崩溃修复**在「展开进请求的那一刻」做
（`ToolTrail.expand`：存储里 `result == null` 的轨迹补 `Error: previous run was interrupted…`）——
比「启动时扫日志」更早、也更难绕过。工具**执行抛异常绝不允许掀翻整个 turn**：异常本身作为结果回填。

### 25.3 压缩（B5）

| 项 | 取值 / 做法 |
|---|---|
| 触发 | `tokens ≥ contextWindow × 0.8`（`Compaction.THRESHOLD`），在每个 step **发请求之前**判 |
| 保留 | system 头 + 全部**非历史**消息（预设 / 本轮输入）永不裁；历史保留最近 `retainRatio = 0.16` 的尾部（整单位保留，最新单位无条件保留） |
| 配对 | 裁切单位 = `assistant(tool_calls)` + 紧随的 `tool` 结果（**整体保留或整体丢弃**） |
| 摘要请求 | **原前缀逐字节重放**（system + tools + 被裁消息）+ 末尾一条摘要指令 → 这次调用**吃一次前缀缓存** |
| 落地 | 简报作为 `ChatMessage.Compacted` 落库到「最后一条被裁消息之后」= 下一轮前缀继续纯追加 |
| 记账 | `Event.Compacted(reason, summary, dropped, kept, tokensBefore/After, summaryUsage)`；失败发 `Event.CompactionFailed`（不静默） |
| 上限 | **一轮最多压一次**（`dropped` 的口径是「相对本轮原始历史」，压第二次落库位置就错了） |

**失败一律降级**：摘要为空 / 摘要请求失败 → 不压缩、**原样继续**（压缩是优化，不能因为摘要失败把对话弄丢）。
`contextWindow` 填 0 = 关闭自动压缩（§15.3）。

### 25.4 重试（B6）

| 判据（`chat/RetryPolicy.kt`） | 处置 | 前提 |
|---|---|---|
| 上下文溢出（三家报错文案取样） | **强制压缩后重发该 step** | 本次尝试**没有任何内容上屏** |
| 网络类（`retryable` / 408 / 409 / 425 / 5xx / 429） | 原样重发该 step | 同上 |
| 其它（鉴权 / 参数 / 地址非法） | 如实报错 | — |

上限**各一次**（`MAX_RETRIES = 1`）：传输层已经在连接层退避重试过 2 次，引擎再退避就是在同一故障上叠时间。
**已经上屏过的失败绝不重发**——宁可留半截话，也不能重复输出半截话。

### 25.5 观测与已知缺口

- 探针：`adb logcat -s LuzzyCache` 一轮两行（`落盘 idx=… 顺序=…` / `本轮 …公共前缀…命中…纪元变化…`），
  压缩再补一行 `压缩落库 idx=… 裁=… 条`；debug 包另有 `LuzzyStream` 逐事件轨迹（release 不打印）。
- **未做（如实登记）**：压缩 / 压缩失败在**界面上不可见**——那是视觉产出，须先走设计流程（AGENTS §2.1）；
  B4 的 `interrupted` 标记同样只有数据没有界面。
- **未验收**：B5/B6 的**真机**验收（JVM 侧 12 例 + 6 例已绿）；真机装包前须用户授权。
