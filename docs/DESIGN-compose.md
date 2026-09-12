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

- [ ] HCT 色板生成代码落地，输出与本表逐值核对（±2 内回填修订本表）；
- [ ] ExtendColors 五色×十阶亮暗两套落 `ui/theme/`；
- [ ] 字体四族本地打包（Lora / PuHuiTi 3 / AlibabaSans / 系统等宽），零 CDN；
- [ ] 聊天页假数据版：顶栏 / 无气泡 AI 消息 / 用户气泡 / 思考卡折叠 / 分支 chip / 输入岛 / 抽屉双形态；
- [ ] MeshGradient 背景亮暗两套；
- [ ] 动效令牌常量类（200/140/贝塞尔）+ reduced-motion 适配；
- [ ] 五维 critique（方向/品牌/层级/动效/工程）+ ui-ux-pro-max pro-rules 对照通过。