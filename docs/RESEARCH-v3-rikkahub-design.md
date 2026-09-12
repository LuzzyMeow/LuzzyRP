# RESEARCH-v3 · rikkahub 前端设计侦察（P0 第 1 项）

> **立档**：2026-09-12（会话 63）。对应 `docs/PLAN-v3.0-compose.md` §7 第 2 项。
> **材料**：rikkahub master 分支源码快照（commit `288a034`，45MB tarball，经 GitHub API 获取——
> git clone 在本机网络下不稳）落库于 `docs/rikkahub-master/`（已 gitignore，**只读参考**）。
> **用途**：为 P0 三方向初稿与 `docs/DESIGN-compose.md` 提供设计参照事实。**本文件不构成复用许可
> 豁免**：凡源码级复用均受 AGPL-3.0 约束（见 `docs/LICENSING.md`）。

---

## 1. 项目事实（已核实）

| 项 | 值 |
|---|---|
| 定位 | 原生 Android LLM 聊天客户端（多供应商 OpenAI/Anthropic/Gemini 兼容） |
| 许可 | **AGPL-3.0**（其仓库 LICENSE 原文已核对；与 `docs/LICENSING.md` §1 记录一致） |
| 规模 | 677 个 .kt 文件；单 Activity + Compose；**7.5k stars**（活跃度高，架构成熟可参照） |
| 技术栈 | Compose + **Material 3 Expressive**（`MaterialExpressiveTheme` + `MotionScheme.expressive()`）+ Koin DI + kotlinx.serialization；界面特效用 `dev.chrisbanes.haze`（玻璃模糊）、`com.dokar.sonner`（toast）、HugeIcons |
| 模块划分 | `:app`（UI 全部）+ `:ai`（协议层，38 kt）+ `:common`（15 kt）+ `:highlight`（代码高亮，45 kt）+ `:material3`（1 kt，material-color-utilities 桥）+ `:speech` / `:document` / `:web` / `:workspace` / `:videogen` / `:oauth` / `:search` |

**与 LuzzyRP v2.0 的对应关系**：rikkahub 的 `:ai` 模块（协议/流式/消息模型）≈ 我们已有的
`chat/` 包（v2.0 Kotlin 传输，207 单测）；它的 `ui/` 包是我们 P1 起要新建的部分。

## 2. 代码组织（ui 包结构，P1 直接参照）

```
me.rerere.rikkahub.ui/
├── activity/          # 宿主 Activity
├── theme/             # 主题（下述 §3）
├── pages/             # 每页一目录：chat/ assistant/ backup/ setting/ history/ stats/ …
│   └── chat/          # ChatPage/ChatVM/ChatList/ChatDrawer/MeshGradientBackground…
├── components/        # 跨页组件：ai/(ChatInput,ModelList…), message/(ChatMessage 系列),
│                      # richtext/(Markdown 系), nav/, table/, webview/
├── context/           # CompositionLocal（LocalNavController/LocalToaster…）
├── hooks/             # 组合函数级「hooks」（rememberXxxState 等）
└── modifier/          # 自定义 Modifier
```

- 页面壳：`Scaffold(topBar=TopAppBar, bottomBar=ChatInput)` + `ModalNavigationDrawer`
  （手机）/ `PermanentNavigationDrawer`（宽屏 ≥1100dp 横屏）双形态切换。
- DI：Koin（`di/` 四模块：App/DataSource/Repository/ViewModel）；VM 用 `koinViewModel`。

## 3. 主题体系（设计令牌的载体）

### 3.1 结构（`ui/theme/` 15 个文件）

| 文件 | 内容 |
|---|---|
| `Theme.kt` | `RikkahubTheme()`：ColorMode(SYSTEM/LIGHT/DARK) → **dynamicColor（Material You 壁纸取色）** 或 **预设/自定义主题** → `ColorScheme`；AMOLED 纯黑开关（copy background/surface = #000000）；`LocalExtendColors` / `LocalDarkMode` 两个 CompositionLocal；状态栏图标色随动 |
| `CustomTheme.kt` | 用户自定义主题：给一个 primary ARGB → **material-color-utilities（HCT + TONAL_SPOT）动态生成整套色板**（light/dark 各一套）——这是「只给一个种子色就有完整主题」的关键机制 |
| `presets/` ×7 | Autumn / Black / **Claude** / Minimal / Ocean / Sakura / Spring——每个是手写 M3 全量 ColorScheme（light+dark 双套各 ~33 色） |
| `Color.kt` | `ExtendColors`：red/orange/green/blue/gray **5 色系 × 10 阶**扩展色（M3 色板装不下的语义色）；`CustomColors`：卡片/顶栏的语义封装（surfaceContainer 系） |
| `Type.kt` | `Typography` 用 **M3 默认**（强调体注释未启用）；`JetbrainsMono` 变量字体（代码块用）；另有 `GoogleSans.kt` / `ChatFont.kt`（聊天字体可配） |
| `CodeColor.kt` | 代码高亮配色 |

### 3.2 令牌事实提取（三方向初稿的参照系）

- **Claude 预设**（观感最接近 LuzzyRP 现行「暖幕手记」）：亮色背景 `#FAF9F5`（象牙白）、
  primary 赤陶橙 `#C96442`（暗色 `#E4906E`）、surfaceContainerLow `#F7F5EF`、outlineVariant
  `#E5E1D6`；暗色背景 `#1F1E1D`。→ 证明「暖纸底 + 珊瑚/赤陶 accent」在 M3 色板里完全可表达，
  且与 LuzzyRP 现行 `--tw-*` 暖色系同族（我们的 `#EDD7BD`/珊瑚方向）。
- **形状**：输入容器 = `MaterialTheme.shapes.largeIncreased`（M3 Expressive 大圆角）、
  消息气泡 = `RoundedCornerShape(16.dp)`、小徽章 = `RoundedCornerShape(50)`（胶囊）、
  附件卡 = 8dp。
- **气泡语义**：user = `primaryContainer`；assistant **默认无气泡**（裸 Markdown 直接落在
  surface 上），可选开「气泡模式」（`surfaceContainerHigh`）；两者都叠 `bubbleOpacity`。
- **背景**：`MeshGradientBackground`——Gemini 风格动态渐变（底层线性渐变 + 4 个正弦轨迹漂移
  的 radialGradient 光斑，5.5s/7s/8.5s/6.2s 各自周期，亮暗两套配色），**不用 `Modifier.blur`**
  （全 API 级可用、性能更好）——与 LuzzyRP「雾纸」配方的性能取舍一致，值得直接借鉴其手法。
- **玻璃效果**：输入岛用 haze 实时模糊（`hazeSource`/`hazeBlur`），但**做成设置开关**
  （`enableBlurEffect`，关掉回退纯色 tint）——性能逃生门是产品级开关。

### 3.3 消息渲染（P2 直接参照）

- `ChatMessage.kt`：`parts.groupMessageParts()` 把消息分片归组为 `ThinkingBlock`（思考+工具
  链）与 `ContentBlock`，`ChainOfThought` 组件统一渲染思考/工具步骤——**与我们会话 59/60 的
  patch 044/045「活通道 + 分块组合」同构**，思路互相印证。
- Markdown：`richtext/Markdown.kt`（自研解析）+ `MarkdownWeb`（WebView 兜底）+
  `HighlightCodeBlock`（`:highlight` 模块）+ Latex/Mermaid/Table 组件；**流式期间禁用
  SelectionContainer**（避免 Compose 选择工具栏并发修改崩溃）——这是踩过坑后写进注释的
  事实，P2 必须遵守。
- `animateContentSize()` 做气泡/内容尺寸过渡。

## 4. 可复用度评估（AGPL 义务视角）

### 4.1 可源码级复用（复用须保留版权头 + 本身已 AGPL 化，无额外障碍）

| 资产 | 复用价值 | 备注 |
|---|---|---|
| `ui/theme/` 整套结构（Theme/CustomTheme/ExtendColors/presets 骨架） | ★★★ | 结构可照搬，**数值必须重新设计**（见 4.3） |
| `CustomTheme.kt` 的 HCT 动态色板生成 | ★★★ | 「一个种子色 → 全套主题」，LuzzyRP 主题系统可直接用 |
| `MeshGradientBackground` 光斑手法 | ★★ | 性能友好的背景动效，参数（周期/色）可调 |
| `richtext/` Markdown 渲染链 + `:highlight` | ★★★ | 自研链路完整，省 P2 最大一块硬骨头 |
| `ChatMessage` 分块渲染 / `ChatInput` / `ChatList` | ★★ | 结构参照 > 逐行复制（业务模型不同） |
| `ui/context`/`hooks`/`modifier` 模式 | ★★ | CompositionLocal 用法范式 |

### 4.2 必须独立实现（RP 特有，rikkahub 无对应物）

角色卡（工坊导入导出/图标）、世界书（`at_depth` 绝对锚/概率触发）、预设体系（破限/COT/
写作风格/剧情面板）、正则（显示/提示词/深度过滤）、记忆（向量+经典+提取管线）、**消息分支树**
（rikkahub 的 branching 是线性 fork，我们是树状编辑器）、剧情面板、沉浸模式、CharacterDeck、
生图分流、用量趋势图、开屏「开卷」、页面交接转场。

### 4.3 ⚠️ token 数值规避（PLAN §1 已定的红线）

- **照抄** rikkahub 的具体色值/尺寸/组件结构 = 接近复制「表达」，虽已整体 AGPL 化而**许可上合法**，
  但设计上仍属「换皮 rikkahub」而非「LuzzyRP 自己的设计语言」；
- **正确姿势**：结构/机制照搬（HCT 生成、ExtendColors 分阶、气泡语义、双 drawer 形态），
  **数值层重新设计**——以 LuzzyRP 现行 DESIGN.md（暖幕手记 / 雾纸 / 珊瑚 accent / Lora+阿里
  字体系）为种子，在 M3 色板里重新推导。这正是三方向初稿要给的「复用程度」光谱。

## 5. 结论（三方向的坐标轴）

侦察后，「复用 rikkahub 到什么程度」收敛为一条清晰光谱，即三方向：

- **方向一「rikkahub 系」**：最大复用——M3 Expressive 座 + 动态色/预设主题机制照搬，
  以 LuzzyRP 暖色系重新生成数值；观感接近 rikkahub 官方默认（偏中性、克制、工具感）。
- **方向二「暖幕手记延续」**：把 v1.x~v2.x WebView 版的设计语言（雾纸 0.74+blur、暖纸底、
  珊瑚 accent、Lora 衬线标题）**翻译成 M3 token**，rikkahub 只借结构不借观感；品牌连续性最强。
- **方向三「混合/新定」**：以 rikkahub 的结构骨架 + 全新 RP 向观感（例：夜读纸墨/双栏书卷
  等新方向），品牌与复用各取一半。

（三方向视觉初稿见 `docs/design/boards-v4/`，由 T4 产出。）